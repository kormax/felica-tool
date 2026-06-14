package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object WriteWithoutEncryptionDetermineMaxBlocksStep :
    WriteWithoutEncryptionScanStep(
        id = "write_without_encryption_determine_max_blocks",
        title = "Write: Determine Max Blocks",
        description = "How many blocks can be written in a request",
        icon = ScanStepIcon.EDIT,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.writeBlocksWithoutEncryptionSupport != CommandSupport.SUPPORTED) {
            throw StepSkipped(
                "Write Without Encryption support must be confirmed before determining max blocks"
            )
        }

        val probeTarget = scanContext.findWritableBlockProbeTarget()

        // Start with theoretical maximum and work down
        var maxBlocks = WriteWithoutEncryptionCommand.MAX_BLOCKS

        while (maxBlocks > 0) {
            try {
                val response =
                    executeCommand(withSelectedSystemCode = probeTarget.systemCode) {
                        WriteWithoutEncryptionCommand(
                            idm = idm,
                            serviceCodes = arrayOf(probeTarget.service.code),
                            blockListElements =
                                Array(maxBlocks) {
                                    BlockListElement(
                                        serviceCodeListOrder = 0,
                                        blockNumber = probeTarget.safeBlockNumber,
                                    )
                                },
                            blockData = Array(maxBlocks) { probeTarget.safeBlockData.copyOf() },
                        )
                    }
                if (response.isStatusSuccessful) {
                    // Command succeeded, we found the maximum
                    ScanLog.d(
                        "CardScanService",
                        "WriteWithoutEncryption succeeded with $maxBlocks blocks",
                    )
                    break
                }
                if (
                    response.statusFlag2.toByte() != 0xA2.toByte() &&
                        response.statusFlag2.toByte() != 0xA8.toByte()
                ) {
                    throw RuntimeException(
                        "WriteWithoutEncryption failed with unexpected error (not 0xA2 or 0xA8) at $maxBlocks blocks, ${formatStatus(response)}"
                    )
                }
                ScanLog.d(
                    "CardScanService",
                    "WriteWithoutEncryption failed with $maxBlocks blocks, ${formatStatus(response)}",
                )
            } catch (e: TransceiveTimeoutException) {
                // Card may not respond if command is too large (e.g., FeliCa Lite)
                // Retry helper checks card availability before continuing with a smaller size.
                ScanLog.d(
                    "CardScanService",
                    "WriteWithoutEncryption got no response with $maxBlocks blocks: ${e.message}",
                )
            }
            maxBlocks--
        }

        if (maxBlocks == 0) {
            throw RuntimeException(
                "Unable to determine maximum blocks per request - even 1 block failed"
            )
        }

        // Update scan context with the determined maximum
        scanContext = scanContext.copy(writeWithoutEncryptionMaxBlocksPerRequest = maxBlocks)

        return StepOutput(
            buildString { appendLine("Maximum blocks per request: $maxBlocks") }.trim()
        )
    }
}
