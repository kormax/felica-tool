package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon
import kotlin.time.Duration.Companion.milliseconds

private const val POLLING_TRAILING_DATA_PROBE_ATTEMPTS = 3
private val POLLING_TRAILING_DATA_PROBE_BYTES = byteArrayOf(0x00)

internal object PollingSystemCodeStep :
    CommandSupportScanStep(
        id = "polling_system_code",
        title = "Polling - System Code",
        description = "Request primary system code of the card using polling command",
        icon = ScanStepIcon.SETTINGS,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.polling.systemCodeSupported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(polling = polling.copy(systemCodeSupported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val parsedSystemCodeResponse =
            executeCommand(withPresenceChecking = false) {
                PollingCommand(
                    systemCode = SYSTEM_CODE_WILDCARD,
                    requestCode = RequestCode.SYSTEM_CODE_REQUEST,
                )
            }

        // Store system code in context
        if (parsedSystemCodeResponse.hasRequestData) {
            scanContext = scanContext.copy(primarySystemCode = parsedSystemCodeResponse.systemCode)

            // Handle special system codes and ensure system contexts exist
            val updatedSystemContexts =
                handleDiscoveredSystemCodes(listOf(parsedSystemCodeResponse.systemCode))

            // Update scan context with the new system contexts
            scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)
        } else {
            // Update existing system context or create a placeholder one (fallback for legacy
            // code)
            if (scanContext.systemScanContexts.isNotEmpty()) {
                val updatedSystemContext =
                    scanContext.systemScanContexts
                        .first()
                        .copy(systemCode = parsedSystemCodeResponse.systemCode)
                scanContext = scanContext.copy(systemScanContexts = listOf(updatedSystemContext))
            } else {
                // Create a basic system context if none exists yet
                val systemContext =
                    SystemScanContext(
                        systemCode = parsedSystemCodeResponse.systemCode,
                        idm = parsedSystemCodeResponse.idm,
                    )
                scanContext = scanContext.copy(systemScanContexts = listOf(systemContext))
            }
        }

        return if (parsedSystemCodeResponse.hasRequestData) {
            val systemCodeHex = parsedSystemCodeResponse.systemCode.toHexString().uppercase()
            StepOutput("System Code: $systemCodeHex")
        } else {
            throw RuntimeException("Polling: System Code: Not available")
        }
    }
}

internal object PollingCommunicationPerformanceStep :
    CommandSupportScanStep(
        id = "polling_communication_performance",
        title = "Polling - Communication Performance",
        description =
            "Request information about supported communication speeds using polling command",
        icon = ScanStepIcon.PHONE,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.polling.communicationPerformanceSupported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(polling = polling.copy(communicationPerformanceSupported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val parsedCommPerfResponse =
            executeCommand(withPresenceChecking = false) {
                PollingCommand(
                    systemCode = SYSTEM_CODE_WILDCARD,
                    requestCode = RequestCode.COMMUNICATION_PERFORMANCE_REQUEST,
                )
            }

        if (parsedCommPerfResponse.hasRequestData) {
            scanContext =
                scanContext.copy(
                    communicationPerformance = parsedCommPerfResponse.communicationPerformance
                )
        }

        return if (parsedCommPerfResponse.hasRequestData) {
            val commPerf = parsedCommPerfResponse.communicationPerformance
            StepOutput(
                buildString {
                        appendLine("212 kbps: ${if (commPerf.supports212kbps) "✓" else "✗"}")
                        appendLine("424 kbps: ${if (commPerf.supports424kbps) "✓" else "✗"}")
                        appendLine(
                            "848 kbps: ${if (commPerf.supports848kbps) "✓" else "✗"} (reserved)"
                        )
                        appendLine(
                            "1696 kbps: ${if (commPerf.supports1696kbps) "✓" else "✗"} (reserved)"
                        )
                        appendLine(
                            "Auto Detection: ${if (commPerf.isAutomaticDetectionCompliant) "✓" else "✗"}"
                        )
                        appendLine("Highest Rate: ${commPerf.getHighestSupportedRate()}")
                    }
                    .trim()
            )
        } else {
            throw RuntimeException("Polling: Communication Performance: Not available")
        }
    }
}

internal object PollingDetermineTrailingDataSupportedStep :
    ScanStep(
        id = "polling_determine_trailing_data_supported",
        title = "Polling - Trailing Data Supported",
        description = "Check whether polling responds with trailing data bytes",
        icon = ScanStepIcon.SEARCH,
    ) {
    override fun isEnabled(settings: ScanSettings): Boolean = settings.testTrailingDataCommands

    override suspend fun ScanSession.perform(): StepOutput {
        val selectedSystemCode = systemCode ?: SYSTEM_CODE_WILDCARD
        val command =
            PollingCommand(
                systemCode = selectedSystemCode,
                requestCode = RequestCode.NO_REQUEST,
                timeSlot = TimeSlot.SLOT_1,
                trailingData = POLLING_TRAILING_DATA_PROBE_BYTES,
            )
        val commandLength = command.toByteArray().size

        val response =
            try {
                executeCommand(
                    attempts = POLLING_TRAILING_DATA_PROBE_ATTEMPTS,
                    retryDelay = 50.milliseconds,
                ) {
                    command
                }
            } catch (e: TransceiveTimeoutException) {
                null
            }

        if (response != null) {
            scanContext = scanContext.withCommands {
                copy(polling = polling.copy(trailingDataSupported = CommandSupport.SUPPORTED))
            }

            val responseIdmHex = response.idm.toHexString().uppercase()
            val responsePmmHex = response.pmm.toHexString().uppercase()

            return StepOutput(
                buildString {
                        appendLine("Polling with trailing data: supported")
                        appendLine("Command length: $commandLength bytes")
                        appendLine(
                            "Trailing data: ${POLLING_TRAILING_DATA_PROBE_BYTES.toHexString()}"
                        )
                        appendLine("Response IDM: $responseIdmHex")
                        appendLine("Response PMM: $responsePmmHex")
                    }
                    .trim()
            )
        }

        scanContext = scanContext.withCommands {
            copy(polling = polling.copy(trailingDataSupported = CommandSupport.UNSUPPORTED))
        }

        return StepOutput(
            buildString {
                    appendLine("Polling with trailing data: not supported")
                    appendLine("Command length: $commandLength bytes")
                    appendLine("Trailing data: ${POLLING_TRAILING_DATA_PROBE_BYTES.toHexString()}")
                    appendLine("No response after $POLLING_TRAILING_DATA_PROBE_ATTEMPTS attempts")
                }
                .trim()
        )
    }
}
