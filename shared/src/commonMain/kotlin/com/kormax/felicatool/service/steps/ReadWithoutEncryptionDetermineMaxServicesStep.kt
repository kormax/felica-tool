package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object ReadWithoutEncryptionDetermineMaxServicesStep :
    ReadWithoutEncryptionScanStep(
        id = "read_without_encryption_determine_max_services",
        title = "Read: Determine Max Services",
        description = "How many services can be read in a request",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findReadWithoutEncryptionTestTarget()

        // Start with theoretical maximum and work down
        var maxServices =
            ReadWithoutEncryptionCommand
                .MAX_SERVICE_CODES // FeliCa specification limit for service codes
        var usedFallback = false
        var fallbackStatus1: Byte? = null
        var fallbackStatus2: Byte? = null
        var observedIllegalNumberPreference: IllegalNumberErrorPreference? = null

        while (maxServices > 0) {
            val response =
                transceiveWithRetries(
                    target = target,
                    commandLabel = "ReadWithoutEncryptionCommand",
                    systemCode = testTarget.systemContext.systemCode,
                ) { activeTarget, _ ->
                    ReadWithoutEncryptionCommand(
                        idm = activeTarget.idm,
                        serviceCodes = Array(maxServices) { testTarget.service.code },
                        blockListElements =
                            Array(maxServices) { serviceIndex ->
                                BlockListElement(
                                    serviceCodeListOrder = serviceIndex,
                                    blockNumber = testTarget.blockNumber,
                                )
                            },
                    )
                }
            if (response.isStatusSuccessful) {
                // Command succeeded, we found the maximum
                ScanLog.d(
                    "CardScanService",
                    "ReadWithoutEncryption succeeded with $maxServices services",
                )
                break
            }
            val status2 = response.statusFlag2.toByte()
            observedIllegalNumberPreference =
                when (status2) {
                    0xA1.toByte() -> IllegalNumberErrorPreference.SERVICE_ERROR
                    0xA2.toByte() -> IllegalNumberErrorPreference.BLOCK_ERROR
                    else -> null
                }

            if (observedIllegalNumberPreference == null) {
                usedFallback = true
                maxServices = 1
                fallbackStatus1 = response.statusFlag1
                fallbackStatus2 = response.statusFlag2
                ScanLog.w(
                    "CardScanService",
                    "ReadWithoutEncryption returned unexpected status while determining max services, falling back to 1 service (${formatStatus(fallbackStatus1, fallbackStatus2)})",
                )
                break
            }

            ScanLog.d(
                "CardScanService",
                "ReadWithoutEncryption failed with $maxServices services, ${formatStatus(response)} (${observedIllegalNumberPreference.name})",
            )
            maxServices--
        }

        if (maxServices == 0) {
            throw RuntimeException(
                "Unable to determine maximum services per request - even 1 service failed"
            )
        }

        // Update scan context with the determined maximum
        scanContext = scanContext.copy(readWithoutEncryptionMaxServicesPerRequest = maxServices)

        if (usedFallback) {
            markStepSupported()
            throw StepBehaviorUnexpected(
                "Maximum services fallback to 1: unexpected status (${formatStatus(fallbackStatus1, fallbackStatus2)})"
            )
        }

        return StepOutput(
            buildString { appendLine("Maximum services per request: $maxServices") }.trim()
        )
    }
}
