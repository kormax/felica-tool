package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetNodePropertyValueLimitedServiceStep :
    CommandSupportScanStep(
        id = "get_node_property_value_limited_service",
        title = "Get Node Property - Value Limited Service",
        description = "Get value-limited purse service properties for discovered nodes",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getNodePropertyValueLimitedServiceSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getNodePropertyValueLimitedServiceSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allDiscoveredNodes.isEmpty()) {
            throw StepPreconditionNotMet(
                "No nodes discovered. Get Node Property (Value-Limited Service) requires discovered nodes from Discover Nodes step."
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
            val nodeValueLimitedPurseProperties =
                systemContext.nodeValueLimitedPurseProperties.toMutableMap()
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (discoveredNodes.isEmpty()) {
                results.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No nodes discovered"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            val contextResults = mutableListOf<String>()

            // Try to get Value-Limited Purse Service properties in batches
            val valueLimitedPurseResults = mutableListOf<String>()

            discoveredNodes.chunked(maxNodesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val valueLimitedPurseCommand =
                    GetNodePropertyCommand(
                        target.idm,
                        NodePropertyType.VALUE_LIMITED_PURSE_SERVICE,
                        nodeBatch,
                    )
                val valueLimitedPurseResponse = target.transceive(valueLimitedPurseCommand)

                if (valueLimitedPurseResponse.isStatusSuccessful) {
                    nodeBatch.zip(valueLimitedPurseResponse.nodeProperties).forEach {
                        (node, property) ->
                        if (property is ValueLimitedPurseServiceProperty) {
                            totalProperties++
                            if (property.enabled) {
                                enabledProperties++
                            }
                            val nodeCode = node.fullCode.toHexString()
                            val formatted =
                                if (property.enabled) {
                                    buildString {
                                            appendLine(" ${nodeCode.padStart(8, ' ')}:")
                                            appendLine("   Upper Limit: ${property.upperLimit}")
                                            appendLine("   Lower Limit: ${property.lowerLimit}")
                                            appendLine(
                                                "   Generation Number: ${property.generationNumber}"
                                            )
                                        }
                                        .trimEnd()
                                } else {
                                    " ${nodeCode.padStart(8, ' ')}: Disabled"
                                }

                            valueLimitedPurseResults.add(formatted)
                            nodeValueLimitedPurseProperties[node] = property
                        }
                    }
                } else {
                    valueLimitedPurseResults.add(
                        "Batch ${batchIndex + 1}: Failed to retrieve Value-Limited Purse Service properties (Status: 0x${byteToHex(valueLimitedPurseResponse.statusFlag1)})"
                    )
                }
            }

            contextResults.add(
                buildString {
                    if (valueLimitedPurseResults.isNotEmpty()) {
                        valueLimitedPurseResults.forEachIndexed { index, result ->
                            append(result)
                            if (index < valueLimitedPurseResults.lastIndex) {
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
                systemContext.copy(
                    nodeValueLimitedPurseProperties = nodeValueLimitedPurseProperties
                )
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
                "Get Node Property (Value-Limited Service) encountered $errors error(s)"
            )
        }

        val collapsedResult =
            "Value-limited purse properties: $enabledProperties enabled / $totalProperties returned for ${allDiscoveredNodes.size} node(s)"
        val expandedResult =
            buildString {
                    appendLine("Get Node Property (Value-Limited Service) Results:")
                    appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
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
}
