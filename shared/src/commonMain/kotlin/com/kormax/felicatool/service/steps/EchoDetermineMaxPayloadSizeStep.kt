package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TagUnavailableException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

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
        if (scanContext.echoSupport != CommandSupport.SUPPORTED) {
            throw StepPreconditionNotMet(
                "Echo support must be confirmed before determining max payload size"
            )
        }

        ensureCardPresence(target)

        val minLength = 0
        val maxLength = 252
        val attempts = mutableListOf<EchoAttemptResult>()

        val baselineAttempt = attemptEcho(minLength)
        attempts += baselineAttempt
        if (!baselineAttempt.success) {
            val reason = baselineAttempt.error?.let { ": $it" } ?: ""
            throw RuntimeException("Echo command failed even at $minLength bytes$reason")
        }

        val maxAttempt = attemptEcho(maxLength)
        attempts += maxAttempt
        if (maxAttempt.success) {
            scanContext = scanContext.copy(echoMaxPayloadSize = maxLength)
            return formatResult(maxLength, attempts)
        }

        var lowerBound = minLength
        var upperBound = maxLength
        var bestLength = minLength

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

        scanContext = scanContext.copy(echoMaxPayloadSize = bestLength)
        return formatResult(bestLength, attempts)
    }

    private suspend fun ScanSession.attemptEcho(length: Int): EchoAttemptResult {
        val payload = ByteArray(length) { index -> (index and 0xFF).toByte() }
        val command = EchoCommand(payload)
        return try {
            val response = transceiveWithRetries(target, command)
            if (response.data.contentEquals(payload)) {
                EchoAttemptResult(length, success = true, error = null)
            } else {
                EchoAttemptResult(
                    length = length,
                    success = false,
                    error = "Echo mismatch (${response.data.size} bytes returned)",
                )
            }
        } catch (e: TagUnavailableException) {
            throw e
        } catch (e: Exception) {
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
