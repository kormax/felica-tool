package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TagUnavailableException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon
import kotlinx.coroutines.CancellationException

internal object ReadWithoutEncryptionDetermineMaxBlocksStep :
    ReadWithoutEncryptionScanStep(
        id = "read_without_encryption_determine_max_blocks",
        title = "Read: Determine Max Blocks",
        description = "How many blocks can be read in a request",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findReadWithoutEncryptionTestTarget()

        // Start with theoretical maximum and work down
        var maxBlocks = ReadWithoutEncryptionCommand.MAX_BLOCKS
        var usedFallback = false
        var fallbackStatus1: Byte? = null
        var fallbackStatus2: Byte? = null

        while (maxBlocks > 0) {
            try {
                val response =
                    transceiveWithRetries(
                        target = target,
                        systemCode = testTarget.systemContext.systemCode,
                    ) { activeTarget, _ ->
                        ReadWithoutEncryptionCommand(
                            idm = activeTarget.idm,
                            serviceCodes = arrayOf(testTarget.service.code),
                            blockListElements =
                                Array(maxBlocks) {
                                    BlockListElement(
                                        serviceCodeListOrder = 0,
                                        blockNumber = testTarget.blockNumber,
                                    )
                                },
                        )
                    }
                if (response.isStatusSuccessful) {
                    // Command succeeded, we found the maximum
                    ScanLog.d(
                        "CardScanService",
                        "ReadWithoutEncryption succeeded with $maxBlocks blocks",
                    )
                    break
                }
                if (
                    response.statusFlag2.toByte() != 0xA2.toByte() &&
                        response.statusFlag2.toByte() != 0xA8.toByte()
                ) {
                    usedFallback = true
                    maxBlocks = 1
                    fallbackStatus1 = response.statusFlag1
                    fallbackStatus2 = response.statusFlag2
                    ScanLog.w(
                        "CardScanService",
                        "ReadWithoutEncryption returned unexpected status while determining max blocks, falling back to 1 block (${formatStatus(fallbackStatus1, fallbackStatus2)})",
                    )
                    break
                }
                ScanLog.d(
                    "CardScanService",
                    "ReadWithoutEncryption failed with $maxBlocks blocks, ${formatStatus(response)}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                // Card may not respond if command is too large (e.g., FeliCa Lite)
                // Retry helper checks card availability before continuing with a smaller size.
                ScanLog.d(
                    "CardScanService",
                    "ReadWithoutEncryption got no response with $maxBlocks blocks: ${e.message}",
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
        scanContext = scanContext.copy(readWithoutEncryptionMaxBlocksPerRequest = maxBlocks)

        if (usedFallback) {
            markStepSupported()
            throw StepBehaviorUnexpected(
                "Maximum blocks fallback to 1: unexpected status (${formatStatus(fallbackStatus1, fallbackStatus2)})"
            )
        }

        return StepOutput(
            buildString { appendLine("Maximum blocks per request: $maxBlocks") }.trim()
        )
    }
}
