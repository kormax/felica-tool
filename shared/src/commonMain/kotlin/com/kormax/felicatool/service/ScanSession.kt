package com.kormax.felicatool.service

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.NfcReaderException
import com.kormax.felicatool.nfc.NfcTargetUnavailableException
import com.kormax.felicatool.nfc.TransceiveErrorException
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.logging.CommunicationLogEntry
import com.kormax.felicatool.util.NodeMetadataProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay

private const val PRESENCE_CHECK_ATTEMPTS = 5
private const val PRESENCE_CHECK_RETRY_DELAY_STEP_MS = 50L
private const val PRESENCE_CHECK_REDISCOVERY_TIMEOUT_MILLIS = 600
private const val FIELD_RESET_REDISCOVERY_TIMEOUT_MILLIS = 600

internal const val COMMAND_EXECUTION_ATTEMPTS = 3
internal val COMMAND_EXECUTION_RETRY_DELAY: Duration = 5.milliseconds
internal val SYSTEM_CODE_WILDCARD: ByteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte())

internal class CommandExecutionScope(
    val idm: ByteArray,
    val systemCode: ByteArray?,
    /** 1-based command execution attempt index. */
    val attempt: Int,
)

internal class ScanSession
internal constructor(
    initialTarget: FeliCaTarget,
    val settings: ScanSettings,
    val nodeMetadataProvider: NodeMetadataProvider,
    initialContext: CardScanContext = CardScanContext(),
) {
    private var activeTarget: FeliCaTarget = initialTarget
    private val communicationLogMark = TimeSource.Monotonic.markNow()
    private val communicationLog = mutableListOf<CommunicationLogEntry>()

    val idm: ByteArray
        get() = activeTarget.idm.copyOf()

    val systemCode: ByteArray?
        get() = activeTarget.systemCode?.copyOf()

    val pmm: Pmm
        get() = Pmm(activeTarget.pmm.toByteArray())

    var context: CardScanContext = initialContext

    var scanContext: CardScanContext
        get() = context
        set(value) {
            context = value
        }

    private var activeSystemCode: ByteArray? = null

    private var currentModeValue: Mode = Mode.Mode0
    val currentMode: Mode
        get() = currentModeValue

    fun contextSnapshot(): CardScanContext =
        context.copy(communicationLog = communicationLog.toList())

    suspend fun <T : FelicaResponse> executeCommand(
        withSelectedSystemCode: ByteArray? = null,
        withResetToMode0: Boolean = false,
        withPresenceChecking: Boolean = true,
        attempts: Int = COMMAND_EXECUTION_ATTEMPTS,
        retryDelay: Duration = COMMAND_EXECUTION_RETRY_DELAY,
        createCommand: CommandExecutionScope.() -> FelicaCommand<T>,
    ): T =
        executeCommand(
            withSelectedSystemCode = withSelectedSystemCode,
            withResetToMode0 = withResetToMode0,
            withPresenceChecking = withPresenceChecking,
            attempts = attempts,
            retryDelay = { retryDelay },
            createCommand = createCommand,
        )

    suspend fun <T : FelicaResponse> executeCommand(
        withSelectedSystemCode: ByteArray? = null,
        withResetToMode0: Boolean = false,
        withPresenceChecking: Boolean = true,
        attempts: Int = COMMAND_EXECUTION_ATTEMPTS,
        retryDelay: CommandExecutionScope.() -> Duration,
        createCommand: CommandExecutionScope.() -> FelicaCommand<T>,
    ): T {
        require(attempts >= 1) { "Command execution attempts must be at least 1" }
        val selectedSystemCode = withSelectedSystemCode?.copyOf()
        selectedSystemCode?.let {
            require(it.size == 2) { "Selected system code must be exactly 2 bytes" }
        }

        var lastException: Exception? = null
        var lastCommandLabel = "FeliCa command"
        var lastCommandIdm: ByteArray? = null
        var commandWasSent = false

        for (attempt in 1..attempts) {
            var attemptCommandLabel = "system activation"
            val failure: Exception =
                try {
                    ensureActiveTargetAvailable()

                    if (selectedSystemCode != null && shouldSelectSystemCode(selectedSystemCode)) {
                        selectSystemCode(selectedSystemCode)
                    }

                    val scope =
                        CommandExecutionScope(
                            idm = resolveCurrentIdm(selectedSystemCode),
                            systemCode = selectedSystemCode?.copyOf(),
                            attempt = attempt,
                        )
                    val command = scope.createCommand()
                    lastCommandLabel = command::class.simpleName ?: "FeliCa command"
                    attemptCommandLabel = lastCommandLabel
                    lastCommandIdm = (command as? FelicaCommandWithIdm<*>)?.idm?.copyOf()

                    commandWasSent = true

                    val response = transceive(command, selectedSystemCode = selectedSystemCode)

                    if (withResetToMode0) {
                        resetToMode0OrThrow(
                            authenticatedSystemCode = selectedSystemCode,
                            authenticatedSystemIdm = lastCommandIdm,
                        )
                    }

                    return response
                } catch (e: CancellationException) {
                    throw e
                } catch (e: NfcTargetUnavailableException) {
                    throw e
                } catch (e: TransceiveTimeoutException) {
                    e
                } catch (e: TransceiveErrorException) {
                    e
                }

            lastException = failure
            if (attempt >= attempts) {
                break
            }

            val delayScope =
                CommandExecutionScope(
                    idm = resolveCurrentIdm(selectedSystemCode),
                    systemCode = selectedSystemCode?.copyOf(),
                    attempt = attempt,
                )
            val delayDuration = delayScope.retryDelay()
            require(!delayDuration.isNegative()) { "Retry delay must not be negative" }
            ScanLog.w(
                "CardScanService",
                "$attemptCommandLabel attempt $attempt failed; retrying",
                failure,
            )
            delay(delayDuration)
        }

        val cause =
            lastException ?: RuntimeException("$lastCommandLabel failed without an exception")
        if (withPresenceChecking) {
            if (withResetToMode0 && commandWasSent) {
                resetToMode0OrThrow(
                    authenticatedSystemCode = selectedSystemCode,
                    authenticatedSystemIdm = lastCommandIdm,
                )
            } else {
                confirmCardPresence(withSelectedSystemCode = selectedSystemCode)
            }
        }

        if (cause is TransceiveErrorException) {
            throw cause
        }

        throw TransceiveTimeoutException(
            message = "No response from target after $attempts attempt(s)",
            cause = cause,
        )
    }

    suspend fun handleDiscoveredSystemCodes(
        discoveredSystemCodes: List<ByteArray>
    ): List<SystemScanContext> {
        val allSystemCodes = discoveredSystemCodes.toUniqueByteArrays()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        allSystemCodes.forEach { systemCode ->
            val existingContext =
                scanContext.systemScanContexts.find {
                    it.systemCode?.contentEquals(systemCode) == true
                }

            if (existingContext != null) {
                updatedSystemContexts.add(existingContext)
            } else {
                val canPoll =
                    try {
                        selectSystemCode(systemCode)
                        true
                    } catch (e: Exception) {
                        ScanLog.d(
                            "CardScanService",
                            "Skipping system ${systemCode.toHexString().uppercase()} - polling failed: ${e.message}",
                        )
                        false
                    }

                if (canPoll) {
                    updatedSystemContexts.add(
                        SystemScanContext(
                            systemCode = systemCode.copyOf(),
                            nodes = emptyList(),
                            idm = activeTarget.idm.copyOf(),
                        )
                    )
                }
            }
        }

        return updatedSystemContexts
    }

    private suspend fun ensureActiveTargetAvailable() {
        if (activeTarget.isAvailable) {
            return
        }

        activeTarget =
            dropAndRediscover(
                target = activeTarget,
                timeout = PRESENCE_CHECK_REDISCOVERY_TIMEOUT_MILLIS.milliseconds,
            )
        ScanLog.w("CardScanService", "Card rediscovered")
    }

    private suspend fun <T : FelicaResponse> transceive(
        command: FelicaCommand<T>,
        selectedSystemCode: ByteArray? = null,
        timeout: Duration? = null,
    ): T {
        val commandBytes = command.toByteArray()
        log(command)
        updateStateAfterCommandSent(command, selectedSystemCode)

        val responseBytes =
            try {
                activeTarget.transceive(
                    commandBytes,
                    timeout ?: activeTarget.inferTimeout(command),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: NfcReaderException) {
                throw e
            } catch (e: Exception) {
                throw TransceiveErrorException(cause = e)
            }

        val response = command.responseFromByteArray(responseBytes)
        log(response)
        updateStateAfterResponse(command, response)
        return response
    }

    private suspend fun selectSystemCode(systemCode: ByteArray): PollingResponse {
        require(systemCode.size == 2) { "System code must be exactly 2 bytes" }
        val selectedSystemCode = systemCode.copyOf()
        val pollingCommand =
            PollingCommand(
                systemCode = selectedSystemCode,
                requestCode = RequestCode.NO_REQUEST,
                timeSlot = TimeSlot.SLOT_1,
            )

        return transceive(pollingCommand)
    }

    private fun shouldSelectSystemCode(systemCode: ByteArray): Boolean {
        val currentSystemCode = activeTarget.systemCode ?: return true
        return !systemCode.sameBytes(SYSTEM_CODE_WILDCARD) &&
            !currentSystemCode.contentEquals(systemCode)
    }

    private suspend fun resetToMode0OrThrow(
        authenticatedSystemCode: ByteArray?,
        authenticatedSystemIdm: ByteArray?,
    ) {
        if (
            authenticatedSystemIdm != null &&
                scanContext.resetModeSupport == CommandSupport.SUPPORTED
        ) {
            try {
                val resetModeCommand = ResetModeCommand(authenticatedSystemIdm)
                transceive(resetModeCommand)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: NfcTargetUnavailableException) {
                throw e
            } catch (e: Exception) {
                ScanLog.w("CardScanService", "Reset Mode command failed; falling back", e)
            }
        }

        val alternativeSystemCode =
            if (scanContext.systemScanContexts.size > 1) {
                scanContext.systemScanContexts
                    .firstOrNull { context ->
                        !context.systemCode.sameBytes(authenticatedSystemCode)
                    }
                    ?.systemCode
                    ?.copyOf()
            } else {
                null
            }

        if (alternativeSystemCode != null) {
            try {
                selectSystemCode(alternativeSystemCode)
                authenticatedSystemCode?.let { selectSystemCode(it) }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: NfcTargetUnavailableException) {
                throw e
            } catch (e: Exception) {
                ScanLog.w(
                    "CardScanService",
                    "Alternate-system polling reset failed; falling back to field reset",
                    e,
                )
            }
        }

        activeTarget =
            dropAndRediscover(
                target = activeTarget,
                timeout = FIELD_RESET_REDISCOVERY_TIMEOUT_MILLIS.milliseconds,
            )
        resetCurrentMode()
    }

    private suspend fun confirmCardPresence(
        withSelectedSystemCode: ByteArray? = null,
        maxAttempts: Int = PRESENCE_CHECK_ATTEMPTS,
    ) {
        var lastException: Exception? = null

        var attempt = 1
        while (attempt <= maxAttempts) {
            try {
                ensureActiveTargetAvailable()

                if (
                    scanContext.requestResponseSupport == CommandSupport.SUPPORTED &&
                        activeTarget.pmm.icType != 0x24.toByte()
                ) {
                    val command = RequestResponseCommand(activeTarget.idm)
                    transceive(command)
                    return
                }

                if (withSelectedSystemCode.sameBytes(SYSTEM_CODE_WILDCARD)) {
                    selectSystemCode(SYSTEM_CODE_WILDCARD)
                    return
                }

                if (scanContext.requestServiceSupport == CommandSupport.SUPPORTED) {
                    val probeService = Service(0, ServiceAttribute.RandomRoWithoutKey)
                    val command =
                        RequestServiceCommand(activeTarget.idm, arrayOf(probeService.code))
                    transceive(command)
                    return
                }

                selectSystemCode(
                    withSelectedSystemCode ?: activeTarget.systemCode ?: SYSTEM_CODE_WILDCARD
                )
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: NfcTargetUnavailableException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                ScanLog.w(
                    "CardScanService",
                    "Card presence check attempt $attempt failed",
                    e,
                )
                delay(PRESENCE_CHECK_RETRY_DELAY_STEP_MS * attempt)
            }
            attempt++
        }

        throw NfcTargetUnavailableException(CardScanService.CARD_LOST_MESSAGE, lastException)
    }

    private fun resolveCurrentIdm(systemCode: ByteArray?): ByteArray {
        val contextIdm =
            if (systemCode != null && !systemCode.sameBytes(SYSTEM_CODE_WILDCARD)) {
                scanContext.systemScanContexts
                    .firstOrNull { context -> context.systemCode.sameBytes(systemCode) }
                    ?.idm
            } else {
                null
            }

        return (contextIdm ?: activeTarget.idm).copyOf()
    }

    private fun updateStateAfterResponse(command: FelicaCommand<*>, response: FelicaResponse) {
        when {
            command is PollingCommand && response is PollingResponse -> {
                activeTarget.currentIdm = response.idm.copyOf()
                activeTarget.currentSystemCode =
                    when {
                        command.requestCode == RequestCode.SYSTEM_CODE_REQUEST &&
                            response.hasRequestData -> response.systemCode.copyOf()
                        !command.systemCode.sameBytes(SYSTEM_CODE_WILDCARD) ->
                            command.systemCode.copyOf()
                        else -> null
                    }
                updateSystemContextIdm(command, response)
                resetModeIfPollingSelectedDifferentSystem(command)
            }
            command is FelicaCommandWithIdm<*> -> {
                val commandIdm = command.idm.copyOf()
                if (!activeTarget.currentIdm.contentEquals(commandIdm)) {
                    activeTarget.currentSystemCode = null
                }
                activeTarget.currentIdm = commandIdm
            }
        }

        if (command is ResetModeCommand && response is ResetModeResponse) {
            resetCurrentMode()
        }
    }

    private fun resetModeIfPollingSelectedDifferentSystem(command: PollingCommand) {
        if (currentModeValue == Mode.Mode0) {
            return
        }

        val resolvedPolledSystemCode =
            command.systemCode.takeUnless { it.sameBytes(SYSTEM_CODE_WILDCARD) }
        if (!activeSystemCode.sameBytes(resolvedPolledSystemCode)) {
            resetCurrentMode()
        }
    }

    private fun updateSystemContextIdm(command: PollingCommand, response: PollingResponse) {
        if (scanContext.systemScanContexts.isEmpty()) {
            return
        }

        val requestedSystemCode = command.systemCode
        var updated = false
        val updatedContexts =
            scanContext.systemScanContexts.mapIndexed { index, context ->
                val shouldUpdate =
                    when {
                        !requestedSystemCode.sameBytes(SYSTEM_CODE_WILDCARD) ->
                            context.systemCode.sameBytes(requestedSystemCode)
                        scanContext.primarySystemCode != null ->
                            context.systemCode.sameBytes(scanContext.primarySystemCode)
                        scanContext.systemScanContexts.size == 1 -> index == 0
                        else -> false
                    }
                if (shouldUpdate && context.idm?.contentEquals(response.idm) != true) {
                    updated = true
                    context.copy(idm = response.idm.copyOf())
                } else {
                    context
                }
            }

        if (updated) {
            scanContext = scanContext.copy(systemScanContexts = updatedContexts)
        }
    }

    private fun updateStateAfterCommandSent(
        command: FelicaCommand<*>,
        selectedSystemCode: ByteArray?,
    ) {
        val mode =
            when (command) {
                is Authentication1DesCommand -> Mode.Mode1.Des
                is Authentication1AesCommand -> Mode.Mode1.Aes
                is InternalAuthenticateAndReadCommand -> Mode.Mode1.AesMac
                else -> null
            } ?: return

        activeSystemCode =
            selectedSystemCode?.takeUnless { it.sameBytes(SYSTEM_CODE_WILDCARD) }?.copyOf()
        currentModeValue = mode
    }

    private fun resetCurrentMode() {
        activeSystemCode = null
        currentModeValue = Mode.Mode0
    }

    private fun log(message: Any) {
        communicationLog +=
            CommunicationLogEntry(
                timestamp = communicationLogMark.elapsedNow().inWholeNanoseconds,
                message = message,
            )
    }
}

private suspend fun dropAndRediscover(
    target: FeliCaTarget,
    timeout: Duration,
): FeliCaTarget {
    val initialIdm = target.initialIdm.copyOf()
    val rediscoveredTarget =
        try {
            target.drop()
            target.readerSession.discoverFeliCaTarget(timeout = timeout)
        } catch (e: TimeoutCancellationException) {
            throw NfcTargetUnavailableException("card was not rediscovered", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val details = e.message ?: e::class.simpleName ?: "Unknown error"
            throw NfcTargetUnavailableException("rediscovery failed: $details", e)
        }

    if (!rediscoveredTarget.initialIdm.contentEquals(initialIdm)) {
        throw NfcTargetUnavailableException("rediscovered a different card")
    }

    return rediscoveredTarget
}
