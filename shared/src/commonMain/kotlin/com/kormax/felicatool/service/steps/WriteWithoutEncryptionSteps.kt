package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object WriteWithoutEncryptionDetermineSupportedStep :
    WriteWithoutEncryptionScanStep(
        id = "write_without_encryption_determine_supported",
        title = "Write: Supported",
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

internal object WriteWithoutEncryptionDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<WriteWithoutEncryptionResponse>(
        id = "write_without_encryption_determine_trailing_data_supported",
        title = "Write - Trailing Data Supported",
        description = "Check whether Write Without Encryption accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Write Without Encryption",
    ) {
    override fun isEnabled(settings: ScanSettings): Boolean =
        settings.testWriteCommands && super.isEnabled(settings)

    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.writeWithoutEncryption.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(writeWithoutEncryption = writeWithoutEncryption.copy(trailingDataSupported = support))
    }

    override fun ScanSession.selectedSystemCode(): ByteArray? =
        scanContext.findWritableBlockProbeTarget().systemCode

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<WriteWithoutEncryptionResponse> {
        val probeTarget = scanContext.findWritableBlockProbeTarget()
        return WriteWithoutEncryptionCommand(
            idm = scope.idm,
            serviceCodes = arrayOf(probeTarget.service.code),
            blockListElements =
                arrayOf(
                    BlockListElement(
                        serviceCodeListOrder = 0,
                        blockNumber = probeTarget.safeBlockNumber,
                    )
                ),
            blockData = arrayOf(probeTarget.safeBlockData.copyOf()),
            trailingData = trailingData,
        )
    }

    override fun responseLines(response: WriteWithoutEncryptionResponse): List<String> =
        listOf("Status: ${formatStatus(response)}")
}

internal object WriteWithoutEncryptionDetermineErrorIndicationStep :
    WriteWithoutEncryptionScanStep(
        id = "write_without_encryption_determine_error_indication",
        title = "Write: Determine Error Indication",
        description = "How errors are indicated when writing blocks",
        icon = ScanStepIcon.EDIT,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.commands.writeWithoutEncryption.supported != CommandSupport.SUPPORTED) {
            throw StepSkipped(
                "Write Without Encryption support must be confirmed before determining error indication"
            )
        }

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

        scanContext = scanContext.withCommands {
            copy(
                writeWithoutEncryption =
                    writeWithoutEncryption.copy(errorLocationIndication = errorIndicationType)
            )
        }

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

internal object WriteWithoutEncryptionDetermineMaxBlocksStep :
    WriteWithoutEncryptionScanStep(
        id = "write_without_encryption_determine_max_blocks",
        title = "Write: Determine Max Blocks",
        description = "How many blocks can be written in a request",
        icon = ScanStepIcon.EDIT,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.commands.writeWithoutEncryption.supported != CommandSupport.SUPPORTED) {
            throw StepSkipped(
                "Write Without Encryption support must be confirmed before determining max blocks"
            )
        }

        val probeTarget = scanContext.findWritableBlockProbeTarget()

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

        scanContext = scanContext.withCommands {
            copy(
                writeWithoutEncryption =
                    writeWithoutEncryption.copy(maxBlocksPerRequest = maxBlocks)
            )
        }

        return StepOutput(
            buildString { appendLine("Maximum blocks per request: $maxBlocks") }.trim()
        )
    }
}
