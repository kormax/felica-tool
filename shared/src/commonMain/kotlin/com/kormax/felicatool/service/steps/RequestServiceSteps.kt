package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon
import kotlin.time.Duration.Companion.milliseconds

private const val REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS = 3
private const val REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER = 0

internal object RequestServiceDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_service_determine_supported",
        title = "Request Service: Supported",
        description = "Check whether Request Service is available",
        icon = ScanStepIcon.CHECK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestService.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestService = requestService.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        var requestedNodes: List<Node> = emptyList()

        val requestServiceResponse =
            executeCommand(
                withSelectedSystemCode = SYSTEM_CODE_WILDCARD,
                attempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) {
                requestedNodes =
                    when (attempt) {
                        // Heuristics
                        // For some reason, the special Octopus variant (IC 24) succeeds more if
                        // we try different values when re-attempting the command
                        1 -> listOf(System)
                        2 -> listOf(Area.ROOT)
                        else -> listOf(System, Area.ROOT)
                    }
                RequestServiceCommand(
                    idm,
                    requestedNodes.map { node -> node.code }.toTypedArray(),
                )
            }

        return StepOutput(
            buildString {
                    appendLine("Request Service command is supported (response received)")
                    appendLine("Nodes:")
                    requestedNodes.zip(requestServiceResponse.keyVersions).forEach {
                        (node, keyVersion) ->
                        appendLine(
                            "  ${node.code.toHexString().uppercase()} (${describeNode(node, includeCode = false)}): ${
                                if (keyVersion.isMissing) {
                                    "Not found"
                                } else {
                                    keyVersion.toInt().toString()
                                }
                            }"
                        )
                    }
                }
                .trim()
        )
    }
}

internal object RequestServiceDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<RequestServiceResponse>(
        id = "request_service_determine_trailing_data_supported",
        title = "Request Service - Trailing Data Supported",
        description = "Check whether Request Service accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Request Service",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestService.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestService = requestService.copy(trailingDataSupported = support))
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<RequestServiceResponse> =
        RequestServiceCommand(scope.idm, arrayOf(System.code), trailingData = trailingData)

    override fun responseLines(response: RequestServiceResponse): List<String> {
        val keyVersion = response.keyVersions.firstOrNull()
        return listOfNotNull(
            "Node: ${System.code.toHexString().uppercase()} (System)",
            keyVersion?.let {
                "System Key Version: ${if (it.isMissing) "Not found" else it.toInt().toString()}"
            },
        )
    }
}

internal object RequestServiceUnknownNodeAttributesStep :
    ScanStep(
        id = "request_service_determine_unknown_node_attributes_supported",
        title = "Request Service - Unknown Attributes",
        description =
            "Check Request Service behavior when a node with an unknown attribute is requested",
        icon = ScanStepIcon.CHECK,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.commands.requestService.supported != CommandSupport.SUPPORTED) {
            throw StepSkipped(
                "Request Service command is unavailable; cannot probe unknown attribute behavior"
            )
        }
        val knownAttributes = buildSet {
            addAll(ServiceAttribute.entries.map { it.value })
            addAll(AreaAttribute.entries.map { it.value })
        }
        val unknownAttributeValue =
            (0..0x3F).firstOrNull { it !in knownAttributes }
                ?: throw StepSkipped("No unknown node attribute value available for probing")
        val unknownAttributeNodeCodeValue =
            (REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER shl 6) or unknownAttributeValue
        val unknownAttributeNodeCode =
            byteArrayOf(
                (unknownAttributeNodeCodeValue and 0xFF).toByte(),
                ((unknownAttributeNodeCodeValue shr 8) and 0xFF).toByte(),
            )

        val response =
            try {
                executeCommand(
                    withSelectedSystemCode = SYSTEM_CODE_WILDCARD,
                    attempts = REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS,
                    retryDelay = 50.milliseconds,
                ) {
                    RequestServiceCommand(idm, arrayOf(unknownAttributeNodeCode))
                }
            } catch (e: TransceiveTimeoutException) {
                null
            }
        val responseReceived = response != null

        scanContext = scanContext.withCommands {
            copy(
                requestService =
                    requestService.copy(
                        unknownNodeAttributesSupported =
                            if (responseReceived) {
                                CommandSupport.SUPPORTED
                            } else {
                                CommandSupport.UNSUPPORTED
                            }
                    )
            )
        }

        return StepOutput(
            buildString {
                    appendLine(
                        "Probe node: ${unknownAttributeNodeCode.toHexString().uppercase()} " +
                            "(service=${REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER}, " +
                            "attribute=0x${byteToHex(unknownAttributeValue)})"
                    )
                    appendLine("Supported = $responseReceived")
                    if (response != null) {
                        val keyVersionHex =
                            response.keyVersions.first().toByteArray().toHexString().uppercase()
                        appendLine("Key version: $keyVersionHex")
                    } else {
                        appendLine(
                            "No response after $REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS attempts"
                        )
                    }
                }
                .trim()
        )
    }
}

internal object RequestServiceV2DetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_service_v2_determine_supported",
        title = "Request Service V2: Supported",
        description = "Check whether Request Service V2 is available",
        icon = ScanStepIcon.CHECK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestServiceV2.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestServiceV2 = requestServiceV2.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val requestServiceV2Response =
            executeCommand(
                withSelectedSystemCode = SYSTEM_CODE_WILDCARD,
                attempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) {
                RequestServiceV2Command(idm, arrayOf(System.code))
            }
        val aesKeyVersion = requestServiceV2Response.aesKeyVersions.firstOrNull()
        val desKeyVersion = requestServiceV2Response.desKeyVersions.firstOrNull()

        return StepOutput(
            buildString {
                    appendLine("Request Service V2 command is supported (response received)")
                    appendLine("Node: ${System.code.toHexString().uppercase()} (System)")
                    appendLine("Status: ${formatStatus(requestServiceV2Response)}")
                    requestServiceV2Response.encryptionIdentifier?.let { encryptionIdentifier ->
                        appendLine("Encryption Identifier: ${encryptionIdentifier.name}")
                    }
                    aesKeyVersion?.let { keyVersion ->
                        appendLine(
                            "AES System Key Version: ${
                                    if (keyVersion.isMissing) {
                                        "Not found"
                                    } else {
                                        keyVersion.toInt().toString()
                                    }
                                }"
                        )
                    }
                    desKeyVersion?.let { keyVersion ->
                        appendLine(
                            "DES System Key Version: ${
                                    if (keyVersion.isMissing) {
                                        "Not found"
                                    } else {
                                        keyVersion.toInt().toString()
                                    }
                                }"
                        )
                    }
                }
                .trim()
        )
    }
}

internal object RequestServiceV2DetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<RequestServiceV2Response>(
        id = "request_service_v2_determine_trailing_data_supported",
        title = "Request Service V2 - Trailing Data Supported",
        description = "Check whether Request Service V2 accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Request Service V2",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestServiceV2.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestServiceV2 = requestServiceV2.copy(trailingDataSupported = support))
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<RequestServiceV2Response> =
        RequestServiceV2Command(scope.idm, arrayOf(System.code), trailingData = trailingData)

    override fun responseLines(response: RequestServiceV2Response): List<String> =
        listOf("Status: ${formatStatus(response)}")
}
