package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object Authentication1AesStep :
    CommandSupportScanStep(
        id = "authentication1_aes",
        title = "Authenticate1 AES",
        description = "Attempt AES authentication with discovered nodes",
        icon = ScanStepIcon.LOCK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.authentication1AesSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(authentication1AesSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        var bestSystemContext: SystemScanContext? = null
        var bestAesCompatibleAreas = emptyList<Area>()
        var bestAesCompatibleServices = emptyList<Service>()
        var bestScore = 0

        for (systemContext in scanContext.systemScanContexts) {
            val areas = systemContext.nodes.filterIsInstance<Area>()
            val authServices =
                systemContext.nodes.filterIsInstance<Service>().filter { service ->
                    service.attribute.authenticationRequired
                }
            val systemAesKeyVersions = systemContext.nodeAesKeyVersions

            val aesCompatibleAreas = areas.filter { area -> systemAesKeyVersions.containsKey(area) }
            val aesCompatibleServices = authServices.filter { service ->
                systemAesKeyVersions.containsKey(service)
            }

            val score =
                (if (aesCompatibleAreas.isNotEmpty()) 100 else 0) +
                    (if (aesCompatibleServices.isNotEmpty()) 100 else 0) +
                    aesCompatibleAreas.size +
                    aesCompatibleServices.size

            if (
                score > bestScore &&
                    (aesCompatibleAreas.isNotEmpty() || aesCompatibleServices.isNotEmpty())
            ) {
                bestScore = score
                bestSystemContext = systemContext
                bestAesCompatibleAreas = aesCompatibleAreas
                bestAesCompatibleServices = aesCompatibleServices
            }
        }

        val selectedSystemContext = bestSystemContext
        if (selectedSystemContext == null) {
            throw StepSkipped(
                "No system found with AES-compatible nodes. AES authentication requires nodes with AES key versions."
            )
        }

        val systemCodeHex = selectedSystemContext.systemCode?.toHexString() ?: "unknown"
        ScanLog.d(
            "CardScanService",
            "Selected system $systemCodeHex for AES authentication with ${bestAesCompatibleAreas.size} areas and ${bestAesCompatibleServices.size} services",
        )

        // Generate a random challenge1A (16 bytes for AES)
        val challenge1A = ByteArray(16) { 0x0.toByte() }

        // Take a subset of AES-compatible nodes from the selected system (areas and services
        // combined in single field)
        // According to user feedback: areas and services are sent in a single field,
        // with the first byte being a flag (default 0x00)
        // Up to 16 nodes in total
        val aesCompatibleNodes = bestAesCompatibleAreas.take(1) + bestAesCompatibleServices.take(1)

        val authenticateResponse =
            executeCommand(
                withSelectedSystemCode = selectedSystemContext.systemCode,
                withResetToMode0 = true,
            ) {
                Authentication1AesCommand(
                    idm = idm,
                    nodeCodes = aesCompatibleNodes.map { it.code }.toTypedArray(),
                    challenge1A = challenge1A,
                )
            }

        return StepOutput(
            buildString {
                    appendLine("AES Authentication Results:")
                    appendLine("Selected system: $systemCodeHex")
                    appendLine(
                        "AES-compatible nodes (${aesCompatibleNodes.size}) used in combined field"
                    )
                    appendLine("Challenge1A (sent): ${challenge1A.toHexString()}")
                    appendLine(
                        "Response data (received): ${authenticateResponse.data.toHexString()}"
                    )
                    appendLine()

                    if (aesCompatibleNodes.isNotEmpty()) {
                        appendLine("Nodes authenticated (areas and services combined):")
                        aesCompatibleNodes.forEachIndexed { index, node ->
                            appendLine("  ${index + 1}. ${describeNode(node)} - AES key")
                        }
                        appendLine()
                    }
                }
                .trim()
        )
    }
}
