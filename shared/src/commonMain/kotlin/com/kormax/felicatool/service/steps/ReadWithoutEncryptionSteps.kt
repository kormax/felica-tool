package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

private val FELICA_LITE_SYSTEM_CODE = byteArrayOf(0x88.toByte(), 0xB4.toByte())
private val NDEF_SYSTEM_CODE = byteArrayOf(0x12.toByte(), 0xFC.toByte())
private val PROTECTED_READ_TEST_SERVICE_CODES = setOf("0B00", "0900")
private const val PROTECTED_READ_TEST_BLOCK_NUMBER = 0x0092

private data class ReadWithoutEncryptionTestTarget(
    val systemContext: SystemScanContext,
    val service: Service,
    val blockNumber: Int,
)

private fun CardScanContext.findReadWithoutEncryptionTestTarget(
    allowAuthenticationRequiredFallback: Boolean = false
): ReadWithoutEncryptionTestTarget {
    val allServices = systemScanContexts.flatMap { context ->
        context.nodes.filterIsInstance<Service>()
    }
    if (allServices.isEmpty()) {
        throw StepSkipped("No services available")
    }

    val allServicesWithoutAuth = allServices.filter { service ->
        !service.attribute.authenticationRequired
    }
    val useAuthenticationRequiredFallback =
        allowAuthenticationRequiredFallback && allServicesWithoutAuth.isEmpty()
    if (allServicesWithoutAuth.isEmpty() && !useAuthenticationRequiredFallback) {
        throw StepSkipped("No services found that don't require authentication")
    }

    val bestSystemContext =
        if (useAuthenticationRequiredFallback) {
            systemScanContexts.maxByOrNull { systemContext ->
                systemContext.nodes.filterIsInstance<Service>().size
            }
        } else {
            systemScanContexts.maxByOrNull { systemContext ->
                systemContext.nodes.filterIsInstance<Service>().count { service ->
                    !service.attribute.authenticationRequired
                }
            }
        } ?: throw StepSkipped("No system context found with readable services")

    val servicesInBestSystem = bestSystemContext.nodes.filterIsInstance<Service>()
    val servicesWithoutAuth = servicesInBestSystem.filter { service ->
        !service.attribute.authenticationRequired
    }
    val candidateServices =
        if (servicesWithoutAuth.isNotEmpty()) {
            servicesWithoutAuth
        } else if (useAuthenticationRequiredFallback) {
            servicesInBestSystem
        } else {
            emptyList()
        }
    if (candidateServices.isEmpty()) {
        throw StepSkipped("No readable services found in the selected system")
    }

    val testService =
        candidateServices.maxByOrNull { service ->
            var score = 0
            if (service.number != 0) score += 4
            if (service.attribute.type == ServiceType.RANDOM) score += 2
            if (service.attribute.mode == ServiceMode.READ_ONLY) score += 1
            score
        }!!
    val isProtectedSystem =
        bestSystemContext.systemCode?.contentEquals(FELICA_LITE_SYSTEM_CODE) == true ||
            bestSystemContext.systemCode?.contentEquals(NDEF_SYSTEM_CODE) == true
    val serviceCodeHex = testService.code.toHexString().uppercase()
    val testBlockNumber =
        if (isProtectedSystem && serviceCodeHex in PROTECTED_READ_TEST_SERVICE_CODES) {
            PROTECTED_READ_TEST_BLOCK_NUMBER
        } else {
            0
        }

    return ReadWithoutEncryptionTestTarget(
        systemContext = bestSystemContext,
        service = testService,
        blockNumber = testBlockNumber,
    )
}

internal object ReadWithoutEncryptionDetermineSupportedStep :
    ReadWithoutEncryptionScanStep(
        id = "read_without_encryption_determine_supported",
        title = "Read: Supported",
        description =
            "Probe if Read Without Encryption is supported by sending a single-service, single-block read request",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget =
            scanContext.findReadWithoutEncryptionTestTarget(
                allowAuthenticationRequiredFallback = true
            )
        val systemCode = testTarget.systemContext.systemCode

        val response =
            executeCommand(
                withSelectedSystemCode = systemCode,
                attempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) {
                ReadWithoutEncryptionCommand(
                    idm = idm,
                    serviceCodes = arrayOf(testTarget.service.code),
                    blockListElements =
                        arrayOf(
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = testTarget.blockNumber,
                            )
                        ),
                )
            }

        val systemCodeHex = systemCode?.toHexString() ?: "unknown"
        val serviceCodeHex = testTarget.service.code.toHexString().uppercase()

        return StepOutput(
            buildString {
                    appendLine("Read Without Encryption command is supported (response received)")
                    appendLine(
                        "System: $systemCodeHex; Service: $serviceCodeHex; Block: ${formatBlockNumberHex(testTarget.blockNumber)}"
                    )
                    appendLine("(${formatStatus(response)})")
                    if (testTarget.service.attribute.authenticationRequired) {
                        appendLine(
                            "Note: Used auth-required service fallback because no no-auth service was available."
                        )
                    }
                }
                .trim()
        )
    }
}

internal object ReadWithoutEncryptionDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<ReadWithoutEncryptionResponse>(
        id = "read_without_encryption_determine_trailing_data_supported",
        title = "Read - Trailing Data Supported",
        description = "Check whether Read Without Encryption accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Read Without Encryption",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.readWithoutEncryption.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(readWithoutEncryption = readWithoutEncryption.copy(trailingDataSupported = support))
    }

    override fun ScanSession.selectedSystemCode(): ByteArray? =
        scanContext
            .findReadWithoutEncryptionTestTarget(allowAuthenticationRequiredFallback = true)
            .systemContext
            .systemCode

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<ReadWithoutEncryptionResponse> {
        val testTarget =
            scanContext.findReadWithoutEncryptionTestTarget(
                allowAuthenticationRequiredFallback = true
            )

        return ReadWithoutEncryptionCommand(
            idm = scope.idm,
            serviceCodes = arrayOf(testTarget.service.code),
            blockListElements =
                arrayOf(
                    BlockListElement(
                        serviceCodeListOrder = 0,
                        blockNumber = testTarget.blockNumber,
                    )
                ),
            trailingData = trailingData,
        )
    }

    override fun responseLines(response: ReadWithoutEncryptionResponse): List<String> =
        listOf("Status: ${formatStatus(response)}")
}

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

            scanContext = scanContext.withCommands {
                copy(
                    readWithoutEncryption =
                        readWithoutEncryption.copy(errorLocationIndication = fallbackType)
                )
            }

            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            throw StepBehaviorUnexpected(fallbackMessage)
        }

        if ((statusFlag2.toInt() and 0xFF) != 0xA8) {
            val fallbackMessage =
                "Error indication fallback to ${fallbackType.name}: unexpected status (${formatStatus(response)})"
            ScanLog.w("CardScanService", fallbackMessage)

            scanContext = scanContext.withCommands {
                copy(
                    readWithoutEncryption =
                        readWithoutEncryption.copy(errorLocationIndication = fallbackType)
                )
            }

            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            throw StepBehaviorUnexpected(fallbackMessage)
        }

        var usedFallback = false
        var fallbackMessage: String? = null
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

        scanContext = scanContext.withCommands {
            copy(
                readWithoutEncryption =
                    readWithoutEncryption.copy(errorLocationIndication = errorIndicationType)
            )
        }

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

internal object ReadWithoutEncryptionDetermineIllegalNumberErrorPreferenceStep :
    ReadWithoutEncryptionScanStep(
        id = "read_without_encryption_determine_illegal_number_error_preference",
        title = "Read: Determine Illegal Number Error Preference",
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
            executeCommand(withSelectedSystemCode = testTarget.systemContext.systemCode) {
                ReadWithoutEncryptionCommand(
                    idm = idm,
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
            val fallbackPreference =
                scanContext.commands.readWithoutEncryption.illegalNumberErrorPreference
            val fallbackLabel = fallbackPreference?.name ?: "UNCHANGED"
            val fallbackMessage =
                "Limit error preference fallback to $fallbackLabel: unexpected status (${formatStatus(response)})"
            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            throw StepBehaviorUnexpected(fallbackMessage)
        }

        scanContext = scanContext.withCommands {
            copy(
                readWithoutEncryption =
                    readWithoutEncryption.copy(illegalNumberErrorPreference = observedPreference)
            )
        }

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

internal object ReadWithoutEncryptionDetermineMaxServicesStep :
    ReadWithoutEncryptionScanStep(
        id = "read_without_encryption_determine_max_services",
        title = "Read: Determine Max Services",
        description = "How many services can be read in a request",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findReadWithoutEncryptionTestTarget()

        var maxServices = ReadWithoutEncryptionCommand.MAX_SERVICE_CODES
        var usedFallback = false
        var fallbackStatus1: Byte? = null
        var fallbackStatus2: Byte? = null
        var observedIllegalNumberPreference: IllegalNumberErrorPreference? = null

        while (maxServices > 0) {
            val response =
                executeCommand(withSelectedSystemCode = testTarget.systemContext.systemCode) {
                    ReadWithoutEncryptionCommand(
                        idm = idm,
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

        scanContext = scanContext.withCommands {
            copy(
                readWithoutEncryption =
                    readWithoutEncryption.copy(maxServicesPerRequest = maxServices)
            )
        }

        if (usedFallback) {
            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            throw StepBehaviorUnexpected(
                "Maximum services fallback to 1: unexpected status (${formatStatus(fallbackStatus1, fallbackStatus2)})"
            )
        }

        return StepOutput(
            buildString { appendLine("Maximum services per request: $maxServices") }.trim()
        )
    }
}

internal object ReadWithoutEncryptionDetermineMaxBlocksStep :
    ReadWithoutEncryptionScanStep(
        id = "read_without_encryption_determine_max_blocks",
        title = "Read: Determine Max Blocks",
        description = "How many blocks can be read in a request",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findReadWithoutEncryptionTestTarget()

        var maxBlocks = ReadWithoutEncryptionCommand.MAX_BLOCKS
        var usedFallback = false
        var fallbackStatus1: Byte? = null
        var fallbackStatus2: Byte? = null

        while (maxBlocks > 0) {
            try {
                val response =
                    executeCommand(withSelectedSystemCode = testTarget.systemContext.systemCode) {
                        ReadWithoutEncryptionCommand(
                            idm = idm,
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
            } catch (e: TransceiveTimeoutException) {
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

        scanContext = scanContext.withCommands {
            copy(
                readWithoutEncryption = readWithoutEncryption.copy(maxBlocksPerRequest = maxBlocks)
            )
        }

        if (usedFallback) {
            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            throw StepBehaviorUnexpected(
                "Maximum blocks fallback to 1: unexpected status (${formatStatus(fallbackStatus1, fallbackStatus2)})"
            )
        }

        return StepOutput(
            buildString { appendLine("Maximum blocks per request: $maxBlocks") }.trim()
        )
    }
}
