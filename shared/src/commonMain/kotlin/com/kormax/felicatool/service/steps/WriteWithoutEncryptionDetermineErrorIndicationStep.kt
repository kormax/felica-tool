package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object WriteWithoutEncryptionDetermineErrorIndicationStep :
    WriteWithoutEncryptionScanStep(
        id = "write_without_encryption_determine_error_indication",
        title = "Write: Determine Error Indication",
        description = "How errors are indicated when writing blocks",
        icon = ScanStepIcon.EDIT,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.writeBlocksWithoutEncryptionSupport != CommandSupport.SUPPORTED) {
            throw StepPreconditionNotMet(
                "Write Without Encryption support must be confirmed before determining error indication"
            )
        }

        val probeTarget = scanContext.findWritableBlockProbeTarget()
        ensureCardPresence(target)

        val response =
            transceiveWithRetries(
                target = target,
                commandLabel = "WriteWithoutEncryptionCommand",
                systemCode = probeTarget.systemCode,
            ) { activeTarget, _ ->
                WriteWithoutEncryptionCommand(
                    idm = activeTarget.idm,
                    serviceCodes = arrayOf(probeTarget.service.code),
                    blockListElements =
                        arrayOf(
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = probeTarget.safeBlockNumber,
                            ),
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = probeTarget.safeBlockNumber,
                            ),
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = probeTarget.invalidBlockNumber,
                            ),
                        ),
                    blockData = Array(3) { probeTarget.safeBlockData.copyOf() },
                )
            }
        val statusFlag1 = response.statusFlag1
        val statusFlag2 = response.statusFlag2

        if (response.isStatusSuccessful) {
            throw RuntimeException(
                "WriteWithoutEncryption failed to determine error indication, ${formatStatus(statusFlag1, statusFlag2)}"
            )
        }

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
                        "Determined BITMASK error indication (status1=0x04)",
                    )
                    ErrorLocationIndication.BITMASK
                }
                statusFlag1.toInt() and 0xFF == 0x03 -> {
                    ScanLog.d(
                        "CardScanService",
                        "Determined INDEX error indication (status1=0x03)",
                    )
                    ErrorLocationIndication.INDEX
                }
                else -> {
                    throw RuntimeException(
                        "Unexpected response status for error indication determination: ${formatStatus(statusFlag1, statusFlag2)}"
                    )
                }
            }

        // Update scan context with determined error indication type
        scanContext =
            scanContext.copy(writeWithoutEncryptionErrorLocationIndication = errorIndicationType)

        ScanLog.d("CardScanService", "Determined error indication type: $errorIndicationType")

        return StepOutput(
            buildString {
                    appendLine(
                        "Error indication type: ${errorIndicationType.name} (${formatStatus(statusFlag1, statusFlag2)})"
                    )
                }
                .trim()
        )
    }
}
