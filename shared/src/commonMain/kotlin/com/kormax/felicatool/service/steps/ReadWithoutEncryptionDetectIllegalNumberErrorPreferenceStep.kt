package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object ReadWithoutEncryptionDetectIllegalNumberErrorPreferenceStep :
    ReadWithoutEncryptionScanStep(
        id = "read_without_encryption_detect_illegal_number_error_preference",
        title = "Read: Detect Illegal Number Error Preference",
        description =
            "Check which error type is preferred by the card when Read Without Encryption exceeds both block and service limits",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findReadWithoutEncryptionTestTarget()
        val requestedCount =
            minOf(
                ReadWithoutEncryptionCommand.MAX_SERVICE_CODES,
                ReadWithoutEncryptionCommand.MAX_BLOCKS,
            )

        val response =
            transceiveWithRetries(
                target = target,
                systemCode = testTarget.systemContext.systemCode,
            ) { activeTarget, _ ->
                ReadWithoutEncryptionCommand(
                    idm = activeTarget.idm,
                    serviceCodes = Array(requestedCount) { testTarget.service.code },
                    blockListElements =
                        Array(requestedCount) { serviceIndex ->
                            BlockListElement(
                                serviceCodeListOrder = serviceIndex,
                                blockNumber = testTarget.blockNumber,
                            )
                        },
                )
            }
        val statusFlag1 = response.statusFlag1
        val statusFlag2 = response.statusFlag2

        if (response.isStatusSuccessful) {
            ScanLog.w(
                "CardScanService",
                "Limit error detection request succeeded unexpectedly with $requestedCount services/blocks",
            )
            return StepOutput(
                buildString {
                        appendLine(
                            "Card accepted $requestedCount services and $requestedCount blocks (${formatStatus(response)})"
                        )
                        appendLine("Limit error preference unchanged")
                    }
                    .trim()
            )
        }

        val observedPreference =
            when (statusFlag2.toByte()) {
                0xA1.toByte() -> IllegalNumberErrorPreference.SERVICE_ERROR
                0xA2.toByte() -> IllegalNumberErrorPreference.BLOCK_ERROR
                else -> null
            }

        if (observedPreference == null) {
            val fallbackPreference = scanContext.readWithoutEncryptionIllegalNumberErrorPreference
            val fallbackLabel = fallbackPreference?.name ?: "UNCHANGED"
            val fallbackMessage =
                "Limit error preference fallback to $fallbackLabel: unexpected status (${formatStatus(response)})"
            markStepSupported()
            throw StepBehaviorUnexpected(fallbackMessage)
        }

        scanContext =
            scanContext.copy(readWithoutEncryptionIllegalNumberErrorPreference = observedPreference)

        ScanLog.d(
            "CardScanService",
            "Detected Read Without Encryption limit preference: ${observedPreference.name} (${formatStatus(response)})",
        )

        val preferenceLabel =
            when (observedPreference) {
                IllegalNumberErrorPreference.SERVICE_ERROR -> "SERVICE"
                IllegalNumberErrorPreference.BLOCK_ERROR -> "BLOCK"
            }

        return StepOutput(
            buildString {
                    appendLine(
                        "Limit error preference: $preferenceLabel (${formatStatus(response)})"
                    )
                }
                .trim()
        )
    }
}
