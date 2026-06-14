package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.GetNodePropertyCommand
import com.kormax.felicatool.felica.MacCommunicationProperty
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.NodePropertyType
import com.kormax.felicatool.felica.ValueLimitedPurseServiceProperty
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.ScanStep
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.service.StepPreconditionNotMet
import com.kormax.felicatool.service.StepSkipped
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.service.formatStatus
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetNodePropertyStep :
    ScanStep(
        id = "get_node_property",
        title = "Get Node Property",
        description = "Get supported node properties for discovered nodes",
        icon = ScanStepIcon.INFO,
    ) {
    override fun commandSupport(context: CardScanContext): CommandSupport =
        when {
            context.getNodePropertyValueLimitedServiceSupport == CommandSupport.SUPPORTED ||
                context.getNodePropertyMacCommunicationSupport == CommandSupport.SUPPORTED ->
                CommandSupport.SUPPORTED
            context.getNodePropertyValueLimitedServiceSupport == CommandSupport.UNSUPPORTED &&
                context.getNodePropertyMacCommunicationSupport == CommandSupport.UNSUPPORTED ->
                CommandSupport.UNSUPPORTED
            else -> CommandSupport.UNKNOWN
        }

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
    if (getNodePropertyValueLimitedServiceSupport == CommandSupport.SUPPORTED) {
        add(
            NodePropertyRequest(
                type = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE,
                label = "Value-Limited Purse Service",
            )
        )
    }
    if (getNodePropertyMacCommunicationSupport == CommandSupport.SUPPORTED) {
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
