package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

private val FELICA_LITE_SYSTEM_CODE = byteArrayOf(0x88.toByte(), 0xB4.toByte())
private val NDEF_SYSTEM_CODE = byteArrayOf(0x12.toByte(), 0xFC.toByte())

private data class WritableBlockProbeTarget(
    val service: Service,
    val systemCode: ByteArray?,
    val safeBlockNumber: Int,
    val safeBlockData: ByteArray,
    val invalidBlockNumber: Int,
)

private fun CardScanContext.findWritableBlockProbeTarget(): WritableBlockProbeTarget {
    val allDiscoveredNodes = systemScanContexts.flatMap { it.nodes }
    val allServices = allDiscoveredNodes.filterIsInstance<Service>()

    if (allServices.isEmpty()) {
        throw StepSkipped("No services available for write testing")
    }

    val writableServices = allServices.filter { service ->
        !service.attribute.authenticationRequired &&
            service.attribute.type == ServiceType.RANDOM &&
            service.attribute.mode == ServiceMode.READ_WRITE
    }

    if (writableServices.isEmpty()) {
        throw StepSkipped("No suitable writable services found (R/W RANDOM, no auth required)")
    }

    data class ServiceCandidate(
        val service: Service,
        val systemContext: SystemScanContext,
        val availableBlocks: MutableMap<Int, ByteArray>,
        val unavailableBlocks: MutableSet<Int>,
    )

    val serviceCandidates = mutableListOf<ServiceCandidate>()

    for (service in writableServices) {
        val systemContext =
            systemScanContexts.find { context -> context.nodes.contains(service) } ?: continue
        val isProtectedSystem =
            systemContext.systemCode?.contentEquals(FELICA_LITE_SYSTEM_CODE) == true ||
                systemContext.systemCode?.contentEquals(NDEF_SYSTEM_CODE) == true

        val isValidServiceNumber =
            if (isProtectedSystem) {
                service.number == 0 || (service.number != 0 && service.number < 1023)
            } else {
                service.number != 0 && service.number < 1023
            }
        if (!isValidServiceNumber) {
            continue
        }

        val macProperties = systemContext.nodeMacCommunicationProperties[service]
        if (macProperties != null && macProperties.enabled) {
            continue
        }

        val blockData = systemContext.serviceBlockData[service] ?: continue
        val maxSafeBlockNumber = if (isProtectedSystem) 0x0D else Int.MAX_VALUE
        val availableBlocks = mutableMapOf<Int, ByteArray>()
        val unavailableBlocks = mutableSetOf<Int>()

        for ((blockNumber, data) in blockData) {
            if (
                blockNumber > maxSafeBlockNumber ||
                    data.size != WriteWithoutEncryptionCommand.BLOCK_SIZE
            ) {
                unavailableBlocks.add(blockNumber)
                continue
            }

            val isAllZero = data.all { it == 0x00.toByte() }
            val isAllFf = data.all { it == 0xFF.toByte() }
            if (isProtectedSystem || isAllZero || isAllFf) {
                availableBlocks[blockNumber] = data
            } else {
                unavailableBlocks.add(blockNumber)
            }
        }

        if (availableBlocks.isNotEmpty()) {
            serviceCandidates.add(
                ServiceCandidate(service, systemContext, availableBlocks, unavailableBlocks)
            )
        }
    }

    if (serviceCandidates.isEmpty()) {
        throw StepSkipped(
            "No empty blocks (all 0x00 or 0xFF) found in any writable service. " +
                "Cannot safely test write commands without risking data modification."
        )
    }

    val bestCandidate = serviceCandidates.maxByOrNull { it.availableBlocks.size }!!
    val safeBlockNumber = bestCandidate.availableBlocks.keys.minOrNull()!!
    val safeBlockData = bestCandidate.availableBlocks[safeBlockNumber]!!
    val allKnownBlocks = bestCandidate.availableBlocks.keys + bestCandidate.unavailableBlocks
    var invalidBlockNumber = 0
    while (invalidBlockNumber in allKnownBlocks) {
        invalidBlockNumber++
    }

    return WritableBlockProbeTarget(
        service = bestCandidate.service,
        systemCode = bestCandidate.systemContext.systemCode,
        safeBlockNumber = safeBlockNumber,
        safeBlockData = safeBlockData,
        invalidBlockNumber = invalidBlockNumber,
    )
}

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
            executeCommand(
                attempts = supportCheckAttempts("write_without_encryption"),
                withSelectedSystemCode = probeTarget.systemCode,
            ) {
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

        return StepOutput(
            buildString {
                appendLine("Write Without Encryption command is supported")
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
        commandKey = "write_without_encryption",
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
        val singleBlockOnly = scanContext.commands.writeWithoutEncryption.maxBlocksPerRequest == 1
        val blockListElements =
            Array(if (singleBlockOnly) 1 else 3) { index ->
                BlockListElement(
                    serviceCodeListOrder = 0,
                    blockNumber =
                        if (singleBlockOnly || index == 2) probeTarget.invalidBlockNumber
                        else probeTarget.safeBlockNumber,
                )
            }

        val response =
            try {
                executeCommand(
                    withSelectedSystemCode = probeTarget.systemCode,
                    attempts = STANDARD_COMMAND_EXECUTION_ATTEMPTS,
                ) {
                    WriteWithoutEncryptionCommand(
                        idm = idm,
                        serviceCodes = arrayOf(probeTarget.service.code),
                        blockListElements = blockListElements,
                        blockData =
                            Array(blockListElements.size) { probeTarget.safeBlockData.copyOf() },
                    )
                }
            } catch (error: TransceiveTimeoutException) {
                scanContext = scanContext.withCommands {
                    copy(
                        writeWithoutEncryption =
                            writeWithoutEncryption.copy(
                                errorLocationIndication = ErrorLocationIndication.NO_RESPONSE
                            )
                    )
                }
                ScanLog.d("CardScanService", "Determined NO_RESPONSE write error indication")
                return StepOutput("Error indication type: NO_RESPONSE")
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

        val maxBlocks =
            determineCommandLimit(
                "write_without_encryption",
                "max_blocks_per_request",
                WriteWithoutEncryptionCommand.MAX_BLOCKS,
            ) { count ->
                try {
                    val response =
                        executeCommand(
                            withSelectedSystemCode = probeTarget.systemCode,
                            attempts = STANDARD_COMMAND_EXECUTION_ATTEMPTS,
                        ) {
                            WriteWithoutEncryptionCommand(
                                idm = idm,
                                serviceCodes = arrayOf(probeTarget.service.code),
                                blockListElements =
                                    Array(count) {
                                        BlockListElement(
                                            serviceCodeListOrder = 0,
                                            blockNumber = probeTarget.safeBlockNumber,
                                        )
                                    },
                                blockData = Array(count) { probeTarget.safeBlockData.copyOf() },
                            )
                        }
                    val status = response.status
                    if (
                        status is Status.Success
                        // FeliCa Lite tags may require authentication even for valid block counts.
                        || status is Status.AuthenticationRequired
                    ) {
                        ScanLog.d(
                            "CardScanService",
                            "WriteWithoutEncryption accepted $count blocks, ${formatStatus(response)}",
                        )
                        return@determineCommandLimit true
                    }
                    if (
                        status !is Status.IllegalNumberOfBlock &&
                            status !is Status.IllegalBlockNumber
                    ) {
                        throw RuntimeException(
                            "WriteWithoutEncryption failed with unexpected error (not 0xA2 or 0xA8) at $count blocks, ${formatStatus(response)}"
                        )
                    }
                    ScanLog.d(
                        "CardScanService",
                        "WriteWithoutEncryption failed with $count blocks, ${formatStatus(response)}",
                    )
                } catch (e: TransceiveTimeoutException) {
                    ScanLog.d(
                        "CardScanService",
                        "WriteWithoutEncryption got no response with $count blocks: ${e.message}",
                    )
                }
                false
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
