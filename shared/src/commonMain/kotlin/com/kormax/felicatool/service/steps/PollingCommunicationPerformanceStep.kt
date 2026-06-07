package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object PollingCommunicationPerformanceStep :
    CommandSupportScanStep(
        id = "polling_communication_performance",
        title = "Polling - Communication Performance",
        description =
            "Request information about supported communication speeds using polling command",
        icon = ScanStepIcon.PHONE,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.pollingCommunicationPerformanceSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(pollingCommunicationPerformanceSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        ensureCardPresence(target)

        val commPerfCommand =
            PollingCommand(
                systemCode = target.systemCode ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                requestCode = RequestCode.COMMUNICATION_PERFORMANCE_REQUEST,
            )
        val parsedCommPerfResponse = transceiveWithRetries(target, commPerfCommand)

        // Store communication performance in context
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
