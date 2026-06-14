package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

private data class Authentication1AesTestTarget(
    val systemContext: SystemScanContext,
    val aesCompatibleAreas: List<Area>,
    val aesCompatibleServices: List<Service>,
) {
    val nodes: List<Node>
        get() = aesCompatibleAreas.take(1) + aesCompatibleServices.take(1)
}

private fun CardScanContext.findBestAuthentication1AesTarget(): Authentication1AesTestTarget? {
    var bestTarget: Authentication1AesTestTarget? = null
    var bestScore = 0

    for (systemContext in systemScanContexts) {
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
            bestTarget =
                Authentication1AesTestTarget(
                    systemContext = systemContext,
                    aesCompatibleAreas = aesCompatibleAreas,
                    aesCompatibleServices = aesCompatibleServices,
                )
        }
    }

    return bestTarget
}

internal object Authentication1AesStep :
    CommandSupportScanStep(
        id = "authentication1_aes",
        title = "Authenticate1 AES",
        description = "Attempt AES authentication with discovered nodes",
        icon = ScanStepIcon.LOCK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.authentication1Aes.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(authentication1Aes = authentication1Aes.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findBestAuthentication1AesTarget()
        if (testTarget == null) {
            throw StepSkipped(
                "No system found with AES-compatible nodes. AES authentication requires nodes with AES key versions."
            )
        }
        val selectedSystemContext = testTarget.systemContext
        val aesCompatibleNodes = testTarget.nodes

        val systemCodeHex = selectedSystemContext.systemCode?.toHexString() ?: "unknown"
        ScanLog.d(
            "CardScanService",
            "Selected system $systemCodeHex for AES authentication with ${testTarget.aesCompatibleAreas.size} areas and ${testTarget.aesCompatibleServices.size} services",
        )

        // Generate a random challenge1A (16 bytes for AES)
        val challenge1A = ByteArray(16) { 0x0.toByte() }

        // Take a subset of AES-compatible nodes from the selected system (areas and services
        // combined in single field)
        // According to user feedback: areas and services are sent in a single field,
        // with the first byte being a flag (default 0x00)
        // Up to 16 nodes in total
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

internal object Authentication1AesDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<Authentication1AesResponse>(
        id = "authentication1_aes_determine_trailing_data_supported",
        title = "Authenticate1 AES - Trailing Data Supported",
        description = "Check whether Authenticate1 AES accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Authenticate1 AES",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.authentication1Aes.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(authentication1Aes = authentication1Aes.copy(trailingDataSupported = support))
    }

    override fun ScanSession.selectedSystemCode(): ByteArray? =
        scanContext.findBestAuthentication1AesTarget()?.systemContext?.systemCode
            ?: throw StepSkipped(
                "No system found with AES-compatible nodes. AES authentication requires nodes with AES key versions."
            )

    override fun ScanSession.resetToMode0AfterCommand(): Boolean = true

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<Authentication1AesResponse> {
        val testTarget =
            scanContext.findBestAuthentication1AesTarget()
                ?: throw StepSkipped(
                    "No system found with AES-compatible nodes. AES authentication requires nodes with AES key versions."
                )
        return Authentication1AesCommand(
            idm = scope.idm,
            nodeCodes = testTarget.nodes.map { it.code }.toTypedArray(),
            challenge1A = ByteArray(16) { 0x00.toByte() },
            trailingData = trailingData,
        )
    }

    override fun responseLines(response: Authentication1AesResponse): List<String> =
        listOf("Response data: ${response.data.toHexString()}")
}
