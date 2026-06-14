package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object WriteWithoutEncryptionDetermineSupportedStep :
    WriteWithoutEncryptionScanStep(
        id = "write_without_encryption_determine_supported",
        title = "Write: Determine Supported",
        description = "Safely check whether Write Without Encryption is available",
        icon = ScanStepIcon.EDIT,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val probeTarget = scanContext.findWritableBlockProbeTarget()

        val response =
            executeCommand(withSelectedSystemCode = probeTarget.systemCode) {
                WriteWithoutEncryptionCommand(
                    idm = idm,
                    serviceCodes = arrayOf(probeTarget.service.code),
                    blockListElements =
                        arrayOf(
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = probeTarget.safeBlockNumber,
                            )
                        ),
                    blockData = arrayOf(probeTarget.safeBlockData.copyOf()),
                )
            }

        if (!response.isStatusSuccessful) {
            throw RuntimeException(
                "WriteWithoutEncryption support probe failed with ${formatStatus(response)}"
            )
        }

        return StepOutput(
            buildString {
                    appendLine(
                        "Write Without Encryption command is supported (safe rewrite succeeded)"
                    )
                    appendLine("Service: ${probeTarget.service.code.toHexString().uppercase()}")
                    appendLine("Block: 0x${formatBlockNumberHex(probeTarget.safeBlockNumber)}")
                    appendLine("Status: ${formatStatus(response)}")
                }
                .trim()
        )
    }
}
