package com.kormax.felicatool.service

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TagUnavailableException
import com.kormax.felicatool.service.logging.CommunicationLoggedFeliCaTarget
import com.kormax.felicatool.util.NodeMetadataProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay

private const val PRESENCE_CHECK_ATTEMPTS = 5
private const val PRESENCE_CHECK_RETRY_DELAY_STEP_MS = 50L
private const val PRESENCE_CHECK_REDISCOVERY_TIMEOUT_MILLIS = 600
private const val RETRY_ATTEMPTS = 3
private const val RETRY_DELAY_STEP_MS = 10L
private const val FIELD_RESET_REDISCOVERY_TIMEOUT_MILLIS = 600

internal class ScanSession
internal constructor(
    val target: FeliCaTarget,
    val settings: ScanSettings,
    val nodeMetadataProvider: NodeMetadataProvider,
    private val readContext: () -> CardScanContext,
    private val writeContext: (CardScanContext) -> Unit,
) {
    var context: CardScanContext
        get() = readContext()
        set(value) {
            writeContext(value)
        }

    var scanContext: CardScanContext
        get() = context
        set(value) {
            context = value
        }

    private lateinit var currentStep: ScanStep
    private var activeSystemCode: ByteArray? = null

    private var currentModeValue: Mode = Mode.Mode0
    val currentMode: Mode
        get() = currentModeValue

    private val step: ScanStep
        get() = currentStep

    fun beginStep(step: ScanStep) {
        currentStep = step
    }

    fun markStepSupported() {
        context = step.withCommandSupport(context, CommandSupport.SUPPORTED)
    }

    fun setCurrentMode(
        mode: Mode,
        selectedSystemCode: ByteArray? = null,
    ) {
        if (mode == Mode.Mode0) {
            resetCurrentMode()
        } else {
            activeSystemCode = selectedSystemCode?.copyOf()
            currentModeValue = mode
        }
    }

    suspend fun pollSystemCode(
        target: FeliCaTarget,
        systemCode: ByteArray? = null,
    ) {
        val pollingSystemCode =
            systemCode ?: target.systemCode ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val pollingCommand =
            PollingCommand(
                systemCode = pollingSystemCode,
                requestCode = RequestCode.NO_REQUEST,
                timeSlot = TimeSlot.SLOT_1,
            )
        val pollingResponse = target.transceive(pollingCommand)

        if (scanContext.systemScanContexts.isNotEmpty()) {
            var updated = false
            val updatedContexts =
                scanContext.systemScanContexts.mapIndexed { index, context ->
                    val shouldUpdate =
                        when {
                            systemCode != null -> context.systemCode.sameBytes(systemCode)
                            scanContext.primarySystemCode != null ->
                                context.systemCode.sameBytes(scanContext.primarySystemCode)
                            scanContext.systemScanContexts.size == 1 -> index == 0
                            else -> false
                        }
                    if (shouldUpdate && context.idm?.contentEquals(pollingResponse.idm) != true) {
                        updated = true
                        context.copy(idm = pollingResponse.idm)
                    } else {
                        context
                    }
                }

            if (updated) {
                scanContext = scanContext.copy(systemScanContexts = updatedContexts)
            }
        }

        if (currentModeValue == Mode.Mode0) {
            return
        }

        val resolvedPolledSystemCode =
            systemCode
                ?: scanContext.primarySystemCode
                ?: scanContext.systemScanContexts.singleOrNull()?.systemCode
        if (!activeSystemCode.sameBytes(resolvedPolledSystemCode)) {
            resetCurrentMode()
        }
    }

    suspend fun resetAuthenticationState(
        target: FeliCaTarget,
        authenticatedSystemCode: ByteArray?,
        authenticatedSystemIdm: ByteArray?,
    ): String {
        if (authenticatedSystemIdm == null) {
            return "State reset skipped: selected-system IDM is unavailable"
        }

        var fieldResetFailurePrefix =
            when (scanContext.resetModeSupport) {
                CommandSupport.SUPPORTED ->
                    try {
                        val resetModeResponse =
                            target.transceive(ResetModeCommand(authenticatedSystemIdm))
                        if (resetModeResponse.isStatusSuccessful) {
                            setCurrentMode(Mode.Mode0)
                            return "State reset to Mode 0 by executing Reset Mode command"
                        }

                        "Reset Mode failed (${formatStatus(resetModeResponse)}); field-drop reset to Mode 0"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val details = e.message ?: e::class.simpleName ?: "Unknown error"
                        "Reset Mode error ($details); field-drop reset to Mode 0"
                    }
                CommandSupport.UNKNOWN ->
                    "Reset Mode support is not confirmed; field-drop reset to Mode 0"
                CommandSupport.UNSUPPORTED ->
                    "Reset Mode is unsupported; field-drop reset to Mode 0"
            }
        var fieldResetSuccessMessage =
            "State reset to Mode 0 by dropping reader field and rediscovering the card"

        val alternativeSystemCode =
            if (scanContext.systemScanContexts.size > 1) {
                scanContext.systemScanContexts
                    .firstOrNull { context ->
                        !context.systemCode.sameBytes(authenticatedSystemCode)
                    }
                    ?.systemCode
            } else {
                null
            }

        if (alternativeSystemCode != null) {
            try {
                pollSystemCode(target, alternativeSystemCode)
                val returnPollingResult =
                    try {
                        if (authenticatedSystemCode != null) {
                            pollSystemCode(target, authenticatedSystemCode)
                            "re-polled back to selected system (${authenticatedSystemCode.toHexString().uppercase()})"
                        } else {
                            "selected system code is unavailable for re-poll"
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val details = e.message ?: e::class.simpleName ?: "Unknown error"
                        "failed to re-poll selected system ($details)"
                    }

                return buildString {
                    append("State reset to Mode 0 by polling another system ")
                    append("(${alternativeSystemCode.toHexString().uppercase()}); ")
                    append(returnPollingResult)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val details = e.message ?: e::class.simpleName ?: "Unknown error"
                fieldResetFailurePrefix =
                    "State reset via alternate-system polling failed ($details); field-drop reset to Mode 0"
                fieldResetSuccessMessage =
                    "State reset to Mode 0 by dropping reader field after alternate-system polling failed ($details)"
            }
        } else {
            fieldResetFailurePrefix =
                when (scanContext.resetModeSupport) {
                    CommandSupport.UNSUPPORTED ->
                        "Reset Mode is unsupported and no alternate system is available for polling reset; field-drop reset to Mode 0"
                    CommandSupport.UNKNOWN ->
                        "Reset Mode support is not confirmed and no alternate system is available for polling reset; field-drop reset to Mode 0"
                    CommandSupport.SUPPORTED -> fieldResetFailurePrefix
                }
        }

        return try {
            val rediscoveredTarget =
                dropAndRediscover(
                    target = target,
                    timeout = FIELD_RESET_REDISCOVERY_TIMEOUT_MILLIS.milliseconds,
                )
            (target as? CommunicationLoggedFeliCaTarget)?.replaceTarget(rediscoveredTarget)
            resetCurrentMode()
            fieldResetSuccessMessage
        } catch (e: CancellationException) {
            throw e
        } catch (e: TagUnavailableException) {
            "$fieldResetFailurePrefix failed (${e.message})"
        }
    }

    private fun resetCurrentMode() {
        activeSystemCode = null
        currentModeValue = Mode.Mode0
    }

    suspend fun ensureCardPresence(
        target: FeliCaTarget,
        maxAttempts: Int = PRESENCE_CHECK_ATTEMPTS,
    ) {
        var lastException: Exception? = null
        val stepId = step.descriptor.id

        var attempt = 1
        while (attempt <= maxAttempts) {
            if (!target.isAvailable) {
                try {
                    val rediscoveredTarget =
                        dropAndRediscover(
                            target = target,
                            timeout = PRESENCE_CHECK_REDISCOVERY_TIMEOUT_MILLIS.milliseconds,
                        )
                    ScanLog.w("CardScanService", "Card rediscovered")
                    (target as? CommunicationLoggedFeliCaTarget)?.replaceTarget(rediscoveredTarget)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ScanLog.w(
                        "CardScanService",
                        "Card rediscovery failed during presence check for step $stepId",
                        e,
                    )
                    throw TagUnavailableException(CardScanService.CARD_LOST_MESSAGE, e)
                }
            }

            try {
                if (scanContext.requestResponseSupport == CommandSupport.SUPPORTED) {
                    try {
                        target.transceive(RequestResponseCommand(target.idm))
                        return
                    } catch (e: Exception) {
                        when (e) {
                            is CancellationException,
                            is TagUnavailableException -> throw e
                        }
                        ScanLog.w(
                            "CardScanService",
                            "Request Response presence check failed for step $stepId",
                            e,
                        )
                    }
                }

                if (scanContext.requestServiceSupport == CommandSupport.SUPPORTED) {
                    try {
                        val probeService = Service(0, ServiceAttribute.RandomRoWithoutKey)
                        target.transceive(
                            RequestServiceCommand(target.idm, arrayOf(probeService.code))
                        )
                        return
                    } catch (e: Exception) {
                        when (e) {
                            is CancellationException,
                            is TagUnavailableException -> throw e
                        }
                        ScanLog.w(
                            "CardScanService",
                            "Request Service presence check failed for step $stepId",
                            e,
                        )
                    }
                }

                pollSystemCode(target)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                ScanLog.w(
                    "CardScanService",
                    "Card presence check attempt $attempt failed for step $stepId",
                    e,
                )
                delay(PRESENCE_CHECK_RETRY_DELAY_STEP_MS * attempt)
            }
            attempt++
        }

        throw TagUnavailableException(CardScanService.CARD_LOST_MESSAGE, lastException)
    }

    suspend fun <T : FelicaResponse> transceiveWithRetries(
        target: FeliCaTarget,
        command: FelicaCommand<T>,
        systemCode: ByteArray? = null,
        maxAttempts: Int = RETRY_ATTEMPTS,
        retryDelayStepMs: Long = RETRY_DELAY_STEP_MS,
    ): T =
        transceiveWithRetries(
            target = target,
            systemCode = systemCode,
            maxAttempts = maxAttempts,
            retryDelayStepMs = retryDelayStepMs,
        ) { _, _ ->
            command
        }

    suspend fun <T : FelicaResponse> transceiveWithRetries(
        target: FeliCaTarget,
        systemCode: ByteArray? = null,
        maxAttempts: Int = RETRY_ATTEMPTS,
        retryDelayStepMs: Long = RETRY_DELAY_STEP_MS,
        createCommand: (FeliCaTarget, Int) -> FelicaCommand<T>,
    ): T {
        var lastException: Exception? = null
        var lastCommandLabel = "FeliCa command"
        var activeTarget = target

        for (attempt in 1..maxAttempts) {
            try {
                if (!activeTarget.isAvailable) {
                    val rediscoveredTarget =
                        try {
                            dropAndRediscover(
                                target = activeTarget,
                                timeout = PRESENCE_CHECK_REDISCOVERY_TIMEOUT_MILLIS.milliseconds,
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw TagUnavailableException(CardScanService.CARD_LOST_MESSAGE, e)
                        }

                    ScanLog.w("CardScanService", "Card rediscovered")
                    (target as? CommunicationLoggedFeliCaTarget)?.replaceTarget(rediscoveredTarget)
                    activeTarget =
                        if (target is CommunicationLoggedFeliCaTarget) {
                            target
                        } else {
                            rediscoveredTarget
                        }
                }

                if (systemCode != null) {
                    // First attempt can trust a matching cached system code; retries poll again to
                    // confirm the card is still present and selected after a failed exchange.
                    val isWildcardSystemCode =
                        systemCode.size == 2 && systemCode.all { it == 0xFF.toByte() }
                    val shouldPollSystemCode =
                        attempt > 1 ||
                            (!isWildcardSystemCode &&
                                !activeTarget.currentSystemCode.sameBytes(systemCode))
                    if (shouldPollSystemCode) {
                        pollSystemCode(activeTarget, systemCode)
                    }
                }
                val retryTimeoutExtension =
                    (retryDelayStepMs * (attempt - 1)).toDuration(DurationUnit.MILLISECONDS)
                val command = createCommand(activeTarget, attempt)
                lastCommandLabel = command::class.simpleName ?: "FeliCa command"
                return activeTarget.transceive(
                    command,
                    activeTarget.inferTimeout(command) + retryTimeoutExtension,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt >= maxAttempts) {
                    break
                }

                ScanLog.w(
                    "CardScanService",
                    "$lastCommandLabel attempt $attempt failed; retrying",
                    e,
                )
            }
        }

        if (!activeTarget.isAvailable) {
            throw TagUnavailableException(CardScanService.CARD_LOST_MESSAGE, lastException)
        }

        throw lastException ?: RuntimeException("$lastCommandLabel failed without an exception")
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
                        pollSystemCode(target, systemCode)
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
                            systemCode = systemCode,
                            nodes = emptyList(),
                            idm = target.idm,
                        )
                    )
                }
            }
        }

        return updatedSystemContexts
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
            throw TagUnavailableException("card was not rediscovered", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val details = e.message ?: e::class.simpleName ?: "Unknown error"
            throw TagUnavailableException("rediscovery failed: $details", e)
        }

    if (!rediscoveredTarget.initialIdm.contentEquals(initialIdm)) {
        throw TagUnavailableException("rediscovered a different card")
    }

    return rediscoveredTarget
}
