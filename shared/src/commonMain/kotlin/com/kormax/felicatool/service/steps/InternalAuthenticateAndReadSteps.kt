package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

private data class InternalAuthenticateAndReadTestTarget(
    val systemContext: SystemScanContext,
    val service: Service,
)

private fun CardScanContext.findInternalAuthenticateAndReadTestTarget():
    InternalAuthenticateAndReadTestTarget? {
    for (systemContext in systemScanContexts) {
        val macProperties = systemContext.nodeMacCommunicationProperties
        if (macProperties.isEmpty()) {
            continue
        }

        val services = systemContext.nodes.filterIsInstance<Service>()
        for (service in services) {
            val macProperty = macProperties[service]
            if (macProperty?.enabled == true) {
                return InternalAuthenticateAndReadTestTarget(
                    systemContext = systemContext,
                    service = service,
                )
            }
        }
    }

    return null
}

internal object InternalAuthenticateAndReadStep :
    CommandSupportScanStep(
        id = "internal_authenticate_and_read",
        title = "Internal Authenticate and Read",
        description = "Test MAC-authenticated read on services with MAC enabled",
        icon = ScanStepIcon.LOCK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.internalAuthenticateAndRead.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(internalAuthenticateAndRead = internalAuthenticateAndRead.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findInternalAuthenticateAndReadTestTarget()
        if (testTarget == null) {
            throw StepSkipped(
                "No services with MAC communication enabled found. " +
                    "Internal Authenticate and Read requires at least one service with MAC enabled."
            )
        }
        val bestSystemContext = testTarget.systemContext
        val bestMacService = testTarget.service

        val systemCodeHex = bestSystemContext.systemCode?.toHexString() ?: "unknown"
        val serviceCodeHex = bestMacService.code.toHexString()

        ScanLog.d(
            "CardScanService",
            "Selected system $systemCodeHex, service $serviceCodeHex for Internal Authenticate and Read",
        )

        // Generate a 16-byte challenge
        val challenge = ByteArray(16) { 0x00 }

        // Create block list element for block 0 of the service
        val blockListElement = BlockListElement(serviceCodeListOrder = 0, blockNumber = 0)

        val response =
            executeCommand(
                withSelectedSystemCode = bestSystemContext.systemCode,
                withResetToMode0 = true,
            ) {
                InternalAuthenticateAndReadCommand(
                    idm = idm,
                    serviceCodes = arrayOf(bestMacService.code),
                    blockListElements = arrayOf(blockListElement),
                    challenge = challenge,
                )
            }

        return if (response.isStatusSuccessful) {
            StepOutput(
                buildString {
                        appendLine("Internal Authenticate and Read Results:")
                        appendLine("System: $systemCodeHex")
                        appendLine("Service: $serviceCodeHex (${bestMacService.attribute})")
                        appendLine("Challenge sent: ${challenge.toHexString()}")
                        appendLine("Status: Success")
                        appendLine("Blocks returned: ${response.blockData.size}")
                        response.blockData.forEachIndexed { index, block ->
                            appendLine("  Block $index: ${block.toHexString()}")
                        }
                        appendLine("Challenge response: ${response.challenge.toHexString()}")
                        appendLine("MAC: ${response.mac.toHexString()}")
                    }
                    .trim()
            )
        } else {
            StepOutput(
                buildString {
                        appendLine("Internal Authenticate and Read Results:")
                        appendLine("System: $systemCodeHex")
                        appendLine("Service: $serviceCodeHex (${bestMacService.attribute})")
                        appendLine("Challenge sent: ${challenge.toHexString()}")
                        appendLine("Status: Failed (${formatStatus(response)})")
                    }
                    .trim()
            )
        }
    }
}

internal object InternalAuthenticateAndReadDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<InternalAuthenticateAndReadResponse>(
        id = "internal_authenticate_and_read_determine_trailing_data_supported",
        title = "Internal Authenticate and Read - Trailing Data Supported",
        description = "Check whether Internal Authenticate and Read accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Internal Authenticate and Read",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.internalAuthenticateAndRead.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(
            internalAuthenticateAndRead =
                internalAuthenticateAndRead.copy(trailingDataSupported = support)
        )
    }

    override fun ScanSession.selectedSystemCode(): ByteArray? =
        scanContext.findInternalAuthenticateAndReadTestTarget()?.systemContext?.systemCode
            ?: throw StepSkipped(
                "No services with MAC communication enabled found. " +
                    "Internal Authenticate and Read requires at least one service with MAC enabled."
            )

    override fun ScanSession.resetToMode0AfterCommand(): Boolean = true

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<InternalAuthenticateAndReadResponse> {
        val testTarget =
            scanContext.findInternalAuthenticateAndReadTestTarget()
                ?: throw StepSkipped(
                    "No services with MAC communication enabled found. " +
                        "Internal Authenticate and Read requires at least one service with MAC enabled."
                )
        return InternalAuthenticateAndReadCommand(
            idm = scope.idm,
            serviceCodes = arrayOf(testTarget.service.code),
            blockListElements =
                arrayOf(BlockListElement(serviceCodeListOrder = 0, blockNumber = 0)),
            challenge = ByteArray(16) { 0x00 },
            trailingData = trailingData,
        )
    }

    override fun responseLines(response: InternalAuthenticateAndReadResponse): List<String> =
        listOf("Status: ${formatStatus(response)}")
}
