package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object InternalAuthenticateAndReadStep :
    CommandSupportScanStep(
        id = "internal_authenticate_and_read",
        title = "Internal Authenticate and Read",
        description = "Test MAC-authenticated read on services with MAC enabled",
        icon = ScanStepIcon.LOCK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.internalAuthenticateAndReadSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(internalAuthenticateAndReadSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        // Find a system with at least one service that has MAC communication enabled
        var bestSystemContext: SystemScanContext? = null
        var bestMacService: Service? = null

        for (systemContext in scanContext.systemScanContexts) {
            val macProperties = systemContext.nodeMacCommunicationProperties
            if (macProperties.isEmpty()) {
                continue
            }

            // Find services with MAC communication enabled
            val services = systemContext.nodes.filterIsInstance<Service>()
            for (service in services) {
                val macProperty = macProperties[service]
                if (macProperty?.enabled == true) {
                    bestSystemContext = systemContext
                    bestMacService = service
                    break
                }
            }
            if (bestMacService != null) break
        }

        if (bestSystemContext == null || bestMacService == null) {
            throw StepPreconditionNotMet(
                "No services with MAC communication enabled found. " +
                    "Internal Authenticate and Read requires at least one service with MAC enabled."
            )
        }
        ensureCardPresence(target)

        val systemCodeHex = bestSystemContext.systemCode?.toHexString() ?: "unknown"
        val serviceCodeHex = bestMacService.code.toHexString()

        ScanLog.d(
            "CardScanService",
            "Selected system $systemCodeHex, service $serviceCodeHex for Internal Authenticate and Read",
        )

        // Poll the selected system before the command
        pollSystemCode(target, bestSystemContext.systemCode)
        val selectedSystemContext =
            scanContext.systemScanContexts.firstOrNull { context ->
                context.systemCode.sameBytes(bestSystemContext.systemCode)
            }
        val selectedSystemIdm = selectedSystemContext?.idm ?: target.idm

        // Generate a 16-byte challenge
        val challenge = ByteArray(16) { 0x00 }

        // Create block list element for block 0 of the service
        val blockListElement = BlockListElement(serviceCodeListOrder = 0, blockNumber = 0)

        val command =
            InternalAuthenticateAndReadCommand(
                idm = selectedSystemIdm,
                serviceCodes = arrayOf(bestMacService.code),
                blockListElements = arrayOf(blockListElement),
                challenge = challenge,
            )

        return try {
            val response = target.transceive(command)
            if (response.isStatusSuccessful) {
                setCurrentMode(Mode.Mode1.AesMac, selectedSystemCode = bestSystemContext.systemCode)
            }

            val resetModeResult =
                resetAuthenticationState(
                    target = target,
                    authenticatedSystemCode = bestSystemContext.systemCode,
                    authenticatedSystemIdm = selectedSystemIdm,
                )

            if (response.isStatusSuccessful) {
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
                            appendLine()
                            appendLine("Reset Mode:")
                            appendLine("  $resetModeResult")
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
                            appendLine()
                            appendLine("Reset Mode:")
                            appendLine("  $resetModeResult")
                        }
                        .trim()
                )
            }
        } catch (e: Exception) {
            throw RuntimeException(
                "Internal Authenticate and Read failed for service $serviceCodeHex: ${e.message}"
            )
        }
    }
}
