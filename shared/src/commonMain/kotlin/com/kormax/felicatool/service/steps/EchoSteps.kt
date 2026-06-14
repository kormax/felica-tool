package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal abstract class EchoScanStep(
    id: String,
    title: String,
    description: String,
    icon: ScanStepIcon,
) :
    CommandSupportScanStep(
        id = id,
        title = title,
        description = description,
        icon = icon,
    ) {
    final override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.echo.supported

    final override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands { copy(echo = echo.copy(supported = support)) }
}

internal object EchoDetermineSupportedStep :
    EchoScanStep(
        id = "echo_determine_supported",
        title = "Echo: Supported",
        description = "Check whether Echo is available",
        icon = ScanStepIcon.REFRESH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val payload = ByteArray(0)
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) { EchoCommand(payload) }
        if (!response.data.contentEquals(payload)) {
            throw RuntimeException(
                "Echo mismatch (${response.data.size} bytes returned): ${response.data.toHexString()}"
            )
        }

        return StepOutput("Echo command is supported (response matched ${payload.size} bytes)")
    }
}

internal object EchoDetermineMaxPayloadSizeStep :
    ScanStep(
        id = "echo_determine_max_payload_size",
        title = "Echo: Determine Max Payload Size",
        description = "Find the largest Echo payload accepted by the card",
        icon = ScanStepIcon.SEARCH,
    ) {
    private data class EchoAttemptResult(
        val length: Int,
        val success: Boolean,
        val error: String?,
    )

    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.commands.echo.supported != CommandSupport.SUPPORTED) {
            throw StepSkipped("Echo support must be confirmed before determining max payload size")
        }
        val knownSupportedLength = 0
        val maxLength = 252
        val attempts = mutableListOf<EchoAttemptResult>()

        val maxAttempt = attemptEcho(maxLength)
        attempts += maxAttempt
        if (maxAttempt.success) {
            scanContext = scanContext.withCommands {
                copy(echo = echo.copy(maxPayloadSize = maxLength))
            }
            return formatResult(maxLength, attempts)
        }

        var lowerBound = knownSupportedLength
        var upperBound = maxLength
        var bestLength = knownSupportedLength

        while ((upperBound - lowerBound) > 1) {
            val candidate = (lowerBound + upperBound) / 2
            val attempt = attemptEcho(candidate)
            attempts += attempt
            if (attempt.success) {
                lowerBound = candidate
                bestLength = maxOf(bestLength, candidate)
            } else {
                upperBound = candidate
            }
        }

        scanContext = scanContext.withCommands {
            copy(echo = echo.copy(maxPayloadSize = bestLength))
        }
        return formatResult(bestLength, attempts)
    }

    private suspend fun ScanSession.attemptEcho(length: Int): EchoAttemptResult {
        val payload = ByteArray(length) { index -> (index and 0xFF).toByte() }
        return try {
            val response = executeCommand { EchoCommand(payload) }
            if (response.data.contentEquals(payload)) {
                EchoAttemptResult(length, success = true, error = null)
            } else {
                EchoAttemptResult(
                    length = length,
                    success = false,
                    error = "Echo mismatch (${response.data.size} bytes returned)",
                )
            }
        } catch (e: TransceiveTimeoutException) {
            EchoAttemptResult(length, success = false, error = e.message ?: "Unknown error")
        }
    }

    private fun formatResult(
        maxSupported: Int,
        attempts: List<EchoAttemptResult>,
    ): StepOutput =
        StepOutput(
            buildString {
                    appendLine("Max echo payload: $maxSupported bytes")
                    appendLine("Attempts (${attempts.size}):")
                    attempts.forEachIndexed { index, attempt ->
                        val status =
                            if (attempt.success) {
                                "success"
                            } else {
                                "failure${attempt.error?.let { ": $it" } ?: ""}"
                            }
                        appendLine("  ${index + 1}. ${attempt.length} bytes -> $status")
                    }
                }
                .trim()
        )
}
