package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon
import kotlin.time.Duration.Companion.milliseconds

private const val POLLING_TRAILING_DATA_PROBE_ATTEMPTS = 3
private val POLLING_TRAILING_DATA_PROBE_BYTES = byteArrayOf(0x00)

internal object PollingDetermineTrailingDataSupportedStep :
    ScanStep(
        id = "polling_determine_trailing_data_supported",
        title = "Polling - Trailing Data Supported",
        description = "Check whether polling responds with trailing data bytes",
        icon = ScanStepIcon.SEARCH,
    ) {
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
            scanContext = scanContext.copy(pollingCommandTrailingDataSupported = true)

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

        scanContext = scanContext.copy(pollingCommandTrailingDataSupported = false)

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
