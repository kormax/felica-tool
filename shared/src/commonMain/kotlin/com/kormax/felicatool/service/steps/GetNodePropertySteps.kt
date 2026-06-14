package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetNodePropertyValueLimitedServiceDetermineSupportedStep :
    CommandSupportScanStep(
        id = "get_node_property_value_limited_service_determine_supported",
        title = "Get Node Property - Value Limited Service: Supported",
        description = "Check whether Value-Limited Purse Service properties are available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getNodeProperty.valueLimitedServiceSupported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getNodeProperty = getNodeProperty.copy(valueLimitedServiceSupported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                GetNodePropertyCommand(
                    idm = idm,
                    nodePropertyType = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE,
                    nodes = listOf(System),
                )
            }

        return StepOutput(
            buildString {
                    appendLine(
                        "Get Node Property Value-Limited Purse Service is supported (response received)"
                    )
                    appendLine("Node: ${System.code.toHexString().uppercase()} (System)")
                    appendLine("Status: ${formatStatus(response)}")
                    appendLine("Returned ${response.nodeProperties.size} properties")
                }
                .trim()
        )
    }
}

internal object GetNodePropertyMacCommunicationDetermineSupportedStep :
    CommandSupportScanStep(
        id = "get_node_property_mac_communication_determine_supported",
        title = "Get Node Property - MAC Communication: Supported",
        description = "Check whether MAC communication properties are available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getNodeProperty.macCommunicationSupported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getNodeProperty = getNodeProperty.copy(macCommunicationSupported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                GetNodePropertyCommand(
                    idm = idm,
                    nodePropertyType = NodePropertyType.MAC_COMMUNICATION,
                    nodes = listOf(System),
                )
            }

        return StepOutput(
            buildString {
                    appendLine(
                        "Get Node Property MAC Communication is supported (response received)"
                    )
                    appendLine("Node: ${System.code.toHexString().uppercase()} (System)")
                    appendLine("Status: ${formatStatus(response)}")
                    appendLine("Returned ${response.nodeProperties.size} properties")
                }
                .trim()
        )
    }
}

internal object GetNodePropertyDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<GetNodePropertyResponse>(
        id = "get_node_property_determine_trailing_data_supported",
        title = "Get Node Property - Trailing Data Supported",
        description = "Check whether Get Node Property accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Get Node Property",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getNodeProperty.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getNodeProperty = getNodeProperty.copy(trailingDataSupported = support))
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<GetNodePropertyResponse> {
        val request =
            scanContext.supportedNodePropertyRequests().firstOrNull()
                ?: throw StepSkipped(
                    "Get Node Property support must be confirmed before checking trailing data"
                )

        return GetNodePropertyCommand(
            idm = scope.idm,
            nodePropertyType = request.type,
            nodes = listOf(System),
            trailingData = trailingData,
        )
    }

    override fun responseLines(response: GetNodePropertyResponse): List<String> =
        listOf(
            "Status: ${formatStatus(response)}",
            "Returned ${response.nodeProperties.size} properties",
        )
}

internal object GetNodePropertyStep :
    ScanStep(
        id = "get_node_property",
        title = "Get Node Property",
        description = "Get supported node properties for discovered nodes",
        icon = ScanStepIcon.INFO,
    ) {
    override fun commandSupport(context: CardScanContext): CommandSupport =
        context.commands.getNodeProperty.supported

    override suspend fun ScanSession.perform(): StepOutput {
        val propertyRequests = scanContext.supportedNodePropertyRequests()
        if (propertyRequests.isEmpty()) {
            throw StepSkipped("Get Node Property is unavailable; cannot read node properties")
        }

        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        if (allDiscoveredNodes.isEmpty()) {
            throw StepPreconditionNotMet(
                "No nodes discovered. Get Node Property requires discovered nodes from Discover Nodes step."
            )
        }

        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalProperties = 0
        var totalEnabledProperties = 0

        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            val discoveredNodes = systemContext.nodes
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"
            val valueLimitedPurseProperties =
                systemContext.nodeValueLimitedPurseProperties.toMutableMap()
            val macCommunicationProperties =
                systemContext.nodeMacCommunicationProperties.toMutableMap()

            if (discoveredNodes.isEmpty()) {
                results.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No nodes discovered"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            val contextLines = mutableListOf<String>()
            for (request in propertyRequests) {
                val propertyResult =
                    loadNodeProperty(
                        systemContext = systemContext,
                        nodes = discoveredNodes,
                        request = request,
                        valueLimitedPurseProperties = valueLimitedPurseProperties,
                        macCommunicationProperties = macCommunicationProperties,
                    )
                totalProperties += propertyResult.propertiesRetrieved
                totalEnabledProperties += propertyResult.enabledProperties
                contextLines += propertyResult.lines
            }

            updatedSystemContexts.add(
                systemContext.copy(
                    nodeValueLimitedPurseProperties = valueLimitedPurseProperties,
                    nodeMacCommunicationProperties = macCommunicationProperties,
                )
            )

            results.add(
                buildString {
                    appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                    if (contextLines.isNotEmpty()) {
                        contextLines.forEach { line -> appendLine(line) }
                    } else {
                        appendLine("No properties retrieved")
                    }
                }
            )
        }

        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        val collapsedResult =
            "Node properties: $totalEnabledProperties enabled / $totalProperties returned for ${allDiscoveredNodes.size} node(s)"
        val expandedResult =
            buildString {
                    appendLine("Get Node Property Results:")
                    appendLine("Properties: ${propertyRequests.joinToString { it.label }}")
                    appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                    appendLine()
                    results.forEachIndexed { index, result ->
                        appendLine(result.trimEnd())
                        if (index < results.lastIndex) {
                            appendLine()
                        }
                    }
                }
                .trimEnd()

        return StepOutput(result = expandedResult, collapsedResult = collapsedResult)
    }

    private suspend fun ScanSession.loadNodeProperty(
        systemContext: SystemScanContext,
        nodes: List<Node>,
        request: NodePropertyRequest,
        valueLimitedPurseProperties: MutableMap<Node, ValueLimitedPurseServiceProperty>,
        macCommunicationProperties: MutableMap<Node, MacCommunicationProperty>,
    ): NodePropertyLoadResult {
        val lines = mutableListOf<String>()
        var propertiesRetrieved = 0
        var enabledProperties = 0

        lines.add("${request.label}:")
        nodes.chunked(GetNodePropertyCommand.MAX_NODES).forEachIndexed { batchIndex, nodeBatch ->
            val response =
                executeCommand(withSelectedSystemCode = systemContext.systemCode) {
                    GetNodePropertyCommand(
                        idm = idm,
                        nodePropertyType = request.type,
                        nodes = nodeBatch,
                    )
                }

            if (!response.isStatusSuccessful) {
                lines.add("  Batch ${batchIndex + 1}: failed (${formatStatus(response)})")
                return@forEachIndexed
            }

            nodeBatch.zip(response.nodeProperties).forEach { (node, property) ->
                when {
                    request.type == NodePropertyType.VALUE_LIMITED_PURSE_SERVICE &&
                        property is ValueLimitedPurseServiceProperty -> {
                        propertiesRetrieved++
                        if (property.enabled) {
                            enabledProperties++
                            lines.add(formatValueLimitedPurseProperty(node, property))
                        }
                        valueLimitedPurseProperties[node] = property
                    }
                    request.type == NodePropertyType.MAC_COMMUNICATION &&
                        property is MacCommunicationProperty -> {
                        propertiesRetrieved++
                        if (property.enabled) {
                            enabledProperties++
                            lines.add(formatMacCommunicationProperty(node))
                        }
                        macCommunicationProperties[node] = property
                    }
                }
            }
        }

        if (lines.size == 1) {
            lines.add("  No enabled properties")
        }

        return NodePropertyLoadResult(
            lines = lines,
            propertiesRetrieved = propertiesRetrieved,
            enabledProperties = enabledProperties,
        )
    }

    private fun formatValueLimitedPurseProperty(
        node: Node,
        property: ValueLimitedPurseServiceProperty,
    ): String {
        val nodeCode = node.fullCode.toHexString().padStart(8, ' ')
        return buildString {
                appendLine(" $nodeCode:")
                appendLine("   Upper Limit: ${property.upperLimit}")
                appendLine("   Lower Limit: ${property.lowerLimit}")
                appendLine("   Generation Number: ${property.generationNumber}")
            }
            .trimEnd()
    }

    private fun formatMacCommunicationProperty(node: Node): String =
        " ${node.fullCode.toHexString().padStart(8, ' ')}: Enabled"
}

private fun CardScanContext.supportedNodePropertyRequests(): List<NodePropertyRequest> = buildList {
    if (commands.getNodeProperty.valueLimitedServiceSupported == CommandSupport.SUPPORTED) {
        add(
            NodePropertyRequest(
                type = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE,
                label = "Value-Limited Purse Service",
            )
        )
    }
    if (commands.getNodeProperty.macCommunicationSupported == CommandSupport.SUPPORTED) {
        add(
            NodePropertyRequest(
                type = NodePropertyType.MAC_COMMUNICATION,
                label = "MAC Communication",
            )
        )
    }
}

private data class NodePropertyRequest(
    val type: NodePropertyType,
    val label: String,
)

private data class NodePropertyLoadResult(
    val lines: List<String>,
    val propertiesRetrieved: Int,
    val enabledProperties: Int,
)
