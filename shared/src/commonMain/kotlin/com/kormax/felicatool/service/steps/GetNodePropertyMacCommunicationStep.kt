package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetNodePropertyMacCommunicationStep :
    CommandSupportScanStep(
        id = "get_node_property_mac_communication",
        title = "Get Node Property - MAC Communication",
        description = "Get MAC communication properties for discovered nodes",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getNodePropertyMacCommunicationSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getNodePropertyMacCommunicationSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allDiscoveredNodes.isEmpty()) {
            throw StepPreconditionNotMet(
                "No nodes discovered. Get Node Property (MAC Communication) requires discovered nodes from Discover Nodes step."
            )
        }
        ensureCardPresence(target)

        var errors = 0
        val results = mutableListOf<String>()
        val maxNodesPerRequest = 16 // FeliCa specification limit
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalProperties = 0
        var enabledProperties = 0

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val discoveredNodes = systemContext.nodes
            val nodeMacCommunicationProperties =
                systemContext.nodeMacCommunicationProperties.toMutableMap()
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (discoveredNodes.isEmpty()) {
                results.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No nodes discovered"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            val contextResults = mutableListOf<String>()

            // Try to get MAC Communication properties in batches
            val macCommunicationResults = mutableListOf<String>()

            discoveredNodes.chunked(maxNodesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val macCommunicationCommand =
                    GetNodePropertyCommand(
                        target.idm,
                        NodePropertyType.MAC_COMMUNICATION,
                        nodeBatch,
                    )
                val macCommunicationResponse = target.transceive(macCommunicationCommand)

                if (macCommunicationResponse.isStatusSuccessful) {
                    nodeBatch.zip(macCommunicationResponse.nodeProperties).forEach {
                        (node, property) ->
                        if (property is MacCommunicationProperty) {
                            totalProperties++
                            if (property.enabled) {
                                enabledProperties++
                            }
                            val nodeCode = node.fullCode.toHexString().padStart(8, ' ')
                            macCommunicationResults.add(
                                " $nodeCode: ${if (property.enabled) "Enabled" else "Disabled"}"
                            )
                            nodeMacCommunicationProperties[node] = property
                        }
                    }
                } else {
                    macCommunicationResults.add(
                        "Batch ${batchIndex + 1}: Failed to retrieve MAC Communication properties (Status: 0x${byteToHex(macCommunicationResponse.statusFlag1)})"
                    )
                }
            }

            contextResults.add(
                buildString {
                    if (macCommunicationResults.isNotEmpty()) {
                        macCommunicationResults.forEachIndexed { index, result ->
                            append(result)
                            if (index < macCommunicationResults.lastIndex) {
                                appendLine()
                            }
                        }
                    } else {
                        appendLine("No properties retrieved")
                    }
                }
            )

            // Update context with properties
            val updatedSystemContext =
                systemContext.copy(nodeMacCommunicationProperties = nodeMacCommunicationProperties)
            updatedSystemContexts.add(updatedSystemContext)

            // Add context results to main results
            results.add(
                buildString {
                    appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                    contextResults.forEachIndexed { index, result ->
                        append(result)
                        if (index < contextResults.lastIndex) {
                            appendLine()
                        }
                    }
                }
            )
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        if (errors > 0) {
            throw RuntimeException(
                "Get Node Property (MAC Communication) encountered $errors error(s)"
            )
        }

        val collapsedResult =
            "MAC communication properties: $enabledProperties enabled / $totalProperties returned for ${allDiscoveredNodes.size} node(s)"
        val expandedResult =
            buildString {
                    appendLine("Get Node Property (MAC Communication) Results:")
                    appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                    results.forEachIndexed { index, result ->
                        appendLine(result.trimEnd())
                        if (index < results.lastIndex) {
                            appendLine()
                        }
                    }
                }
                .trim()

        return StepOutput(result = expandedResult, collapsedResult = collapsedResult)
    }
}
