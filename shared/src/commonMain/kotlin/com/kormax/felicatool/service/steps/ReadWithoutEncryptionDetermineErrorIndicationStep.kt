package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object ReadWithoutEncryptionDetermineErrorIndicationStep :
    ReadWithoutEncryptionScanStep(
        id = "read_without_encryption_determine_error_indication",
        title = "Read: Determine type of error indication",
        description = "How errors are indicated when reading blocks",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findReadWithoutEncryptionTestTarget()
        val invalidBlockNumber = 127

        val response =
            executeCommand(withSelectedSystemCode = testTarget.systemContext.systemCode) {
                ReadWithoutEncryptionCommand(
                    idm = idm,
                    serviceCodes = arrayOf(testTarget.service.code),
                    blockListElements =
                        arrayOf(
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = testTarget.blockNumber,
                            ),
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = testTarget.blockNumber,
                            ),
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = invalidBlockNumber,
                            ),
                        ),
                )
            }
        val statusFlag1 = response.statusFlag1
        val statusFlag2 = response.statusFlag2
        val fallbackType = ErrorLocationIndication.FLAG

        if (response.isStatusSuccessful) {
            val fallbackMessage =
                "Error indication fallback to ${fallbackType.name}: unexpected successful status (${formatStatus(response)})"

            scanContext =
                scanContext.copy(readWithoutEncryptionErrorLocationIndication = fallbackType)

            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            throw StepBehaviorUnexpected(fallbackMessage)
        }

        if ((statusFlag2.toInt() and 0xFF) != 0xA8) {
            val fallbackMessage =
                "Error indication fallback to ${fallbackType.name}: unexpected status (${formatStatus(response)})"
            ScanLog.w("CardScanService", fallbackMessage)

            scanContext =
                scanContext.copy(readWithoutEncryptionErrorLocationIndication = fallbackType)

            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            throw StepBehaviorUnexpected(fallbackMessage)
        }

        var usedFallback = false
        var fallbackMessage: String? = null
        // Analyze response status to determine error indication type
        val errorIndicationType =
            when {
                statusFlag1.toInt() and 0xFF == 0xFF -> {
                    ScanLog.d(
                        "CardScanService",
                        "Determined FLAG error indication (status1=0xFF)",
                    )
                    ErrorLocationIndication.FLAG
                }
                statusFlag1.toInt() and 0xFF == 0x04 -> {
                    ScanLog.d(
                        "CardScanService",
                        "Determined BITMASK error indication (status1=0x03)",
                    )
                    ErrorLocationIndication.BITMASK
                }
                statusFlag1.toInt() and 0xFF == 0x03 -> {
                    ScanLog.d(
                        "CardScanService",
                        "Determined NUMBER error indication (status1=0x01)",
                    )
                    ErrorLocationIndication.INDEX
                }
                else -> {
                    usedFallback = true
                    fallbackMessage =
                        "Error indication fallback to ${fallbackType.name}: unexpected status (${formatStatus(response)})"
                    fallbackType
                }
            }

        // Update scan context with determined error indication type
        scanContext =
            scanContext.copy(readWithoutEncryptionErrorLocationIndication = errorIndicationType)

        if (usedFallback) {
            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            throw StepBehaviorUnexpected(
                fallbackMessage ?: "Error indication fallback to ${fallbackType.name}"
            )
        }

        ScanLog.d("CardScanService", "Determined error indication type: $errorIndicationType")

        return StepOutput(
            buildString {
                    appendLine(
                        "Error indication type: ${errorIndicationType.name} (${formatStatus(response)})"
                    )
                }
                .trim()
        )
    }
}
