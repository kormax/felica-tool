package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TagUnavailableException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon
import com.kormax.felicatool.util.NodeDefinitionType
import kotlinx.coroutines.CancellationException

private data class NodeDiscoveryResult(
    val methodLabel: String,
    val systemContexts: List<SystemScanContext>,
    val details: List<String>,
)

internal object DiscoverNodesStep :
    ScanStep(
        id = "discover_nodes",
        title = "Discover Nodes",
        description =
            "Discover all available nodes using the best supported node discovery command",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val requestCodeListSupported =
            scanContext.requestCodeListSupport == CommandSupport.SUPPORTED
        val searchServiceCodeSupported =
            scanContext.searchServiceCodeSupport == CommandSupport.SUPPORTED
        if (requestCodeListSupported || searchServiceCodeSupported) {
            ensureCardPresence(target)
        }
        val details = mutableListOf<String>()

        val discoveryResult =
            when {
                requestCodeListSupported -> {
                    val requestCodeListResult = discoverNodesWithRequestCodeList()
                    details.addAll(requestCodeListResult.details)

                    if (
                        !requestCodeListResult.systemContexts.any { context ->
                            hasNonStructuralNodes(context.nodes)
                        } && searchServiceCodeSupported
                    ) {
                        details.add(
                            "Request Code List did not find non-structural nodes; retrying with Search Service Code"
                        )
                        val searchServiceCodeResult = discoverNodesWithSearchServiceCode()
                        details.addAll(searchServiceCodeResult.details)
                        searchServiceCodeResult
                    } else {
                        requestCodeListResult
                    }
                }
                searchServiceCodeSupported -> {
                    val searchServiceCodeResult = discoverNodesWithSearchServiceCode()
                    details.addAll(searchServiceCodeResult.details)
                    searchServiceCodeResult
                }
                else -> {
                    val systemContexts = ensureNodeDiscoverySystemContexts()
                    details.add(
                        "No supported node discovery command; using known-node fallback where available"
                    )
                    NodeDiscoveryResult(
                        methodLabel = "Known Node Fallback",
                        systemContexts = systemContexts,
                        details = emptyList(),
                    )
                }
            }

        val fallbackAllowed = !requestCodeListSupported && !searchServiceCodeSupported
        val (finalSystemContexts, fallbackSystems) =
            if (fallbackAllowed) {
                applyKnownNodeFallbacks(
                    systemContexts = discoveryResult.systemContexts,
                    force = true,
                    details = details,
                )
            } else {
                discoveryResult.systemContexts to 0
            }

        scanContext = scanContext.copy(systemScanContexts = finalSystemContexts)

        val allDiscoveredNodes = finalSystemContexts.flatMap { it.nodes }
        val areas = allDiscoveredNodes.filterIsInstance<Area>()
        val services = allDiscoveredNodes.filterIsInstance<Service>()
        val systems = allDiscoveredNodes.filterIsInstance<System>()
        val fallbackSummary =
            if (fallbackSystems > 0) {
                ", fallback populated: $fallbackSystems"
            } else {
                ""
            }
        val collapsedResult =
            "Found ${areas.size} areas, ${services.size} services across ${finalSystemContexts.size} system(s) using ${discoveryResult.methodLabel}$fallbackSummary"

        val expandedResult =
            buildString {
                    appendLine("Discover Nodes Results:")
                    appendLine("Method: ${discoveryResult.methodLabel}")
                    appendLine("Request Code List support: ${scanContext.requestCodeListSupport}")
                    appendLine(
                        "Search Service Code support: ${scanContext.searchServiceCodeSupport}"
                    )
                    appendLine()

                    if (details.isNotEmpty()) {
                        appendLine("Discovery Log:")
                        details.forEach { detail -> appendLine("  - $detail") }
                        appendLine()
                    }

                    finalSystemContexts.forEachIndexed { index, context ->
                        val contextAreas = context.nodes.filterIsInstance<Area>()
                        val contextServices = context.nodes.filterIsInstance<Service>()
                        val contextSystems = context.nodes.filterIsInstance<System>()

                        appendLine(
                            "System Context ${index + 1} (${formatSystemCodeLabel(context.systemCode)}):"
                        )
                        appendLine("  Areas (${contextAreas.size}):")
                        contextAreas.forEach { area ->
                            appendLine(
                                "    - ${describeNode(area)}: Range ${area.number}-${area.endNumber}"
                            )
                        }
                        if (contextAreas.isEmpty()) appendLine("    - None")

                        appendLine("  Services (${contextServices.size}):")
                        contextServices.forEach { service ->
                            appendLine(
                                "    - ${describeNode(service)}: ${service.attribute::class.simpleName}"
                            )
                        }
                        if (contextServices.isEmpty()) appendLine("    - None")

                        appendLine("  Systems (${contextSystems.size}):")
                        contextSystems.forEach { system ->
                            appendLine("    - ${describeNode(system)}")
                        }
                        if (contextSystems.isEmpty()) appendLine("    - None")
                        appendLine()
                    }

                    appendLine("Total Summary:")
                    appendLine(
                        "Areas: ${areas.size}, Services: ${services.size}, Systems: ${systems.size}"
                    )
                }
                .trim()

        return StepOutput(result = expandedResult, collapsedResult = collapsedResult)
    }

    private suspend fun ScanSession.discoverNodesWithRequestCodeList(): NodeDiscoveryResult {
        val details = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        val systemContexts = ensureNodeDiscoverySystemContexts()

        for ((contextIndex, systemContext) in systemContexts.withIndex()) {
            val systemCodeHex = formatSystemCodeLabel(systemContext.systemCode)

            try {
                pollSystemCode(target, systemContext.systemCode)

                val areas = mutableListOf<Area>()
                val services = mutableListOf<Service>()
                var requestCount = 0
                var stopReason = "completed"

                for (index in 1..RequestCodeListCommand.MAX_ITERATOR_INDEX) {
                    val requestCodeListCommand =
                        RequestCodeListCommand(target.idm, Area.ROOT, index)
                    val requestCodeListResponse = target.transceive(requestCodeListCommand)
                    requestCount++

                    if (!requestCodeListResponse.isStatusSuccessful) {
                        stopReason =
                            "status ${formatStatus(requestCodeListResponse)} at index $index"
                        ScanLog.d(
                            "CardScanService",
                            "RequestCodeList error at index $index for system $systemCodeHex: ${formatStatus(requestCodeListResponse)}",
                        )
                        break
                    }

                    areas.addAll(requestCodeListResponse.areas)
                    services.addAll(requestCodeListResponse.services)

                    if (!requestCodeListResponse.continueFlag) {
                        ScanLog.d(
                            "CardScanService",
                            "RequestCodeList completed at index $index for system $systemCodeHex, continueFlag=false",
                        )
                        break
                    }
                }

                val nodes = normalizeDiscoveredNodes(areas + services)
                updatedSystemContexts.add(
                    systemContext.copy(nodes = nodes, registryPopulatedNodes = emptySet())
                )
                details.add(
                    "System ${contextIndex + 1} ($systemCodeHex): Request Code List found ${areas.size} area(s), ${services.size} service(s) in $requestCount request(s); $stopReason"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                ScanLog.w(
                    "CardScanService",
                    "Request Code List discovery failed for system $systemCodeHex",
                    e,
                )
                updatedSystemContexts.add(systemContext)
                details.add(
                    "System ${contextIndex + 1} ($systemCodeHex): Request Code List discovery failed (${e.message ?: e::class.simpleName ?: "Unknown error"})"
                )
            }
        }

        return NodeDiscoveryResult(
            methodLabel = "Request Code List",
            systemContexts = updatedSystemContexts,
            details = details,
        )
    }

    private fun ScanSession.ensureNodeDiscoverySystemContexts(): List<SystemScanContext> {
        if (scanContext.systemScanContexts.isNotEmpty()) {
            return scanContext.systemScanContexts
        }

        val fallbackContext =
            SystemScanContext(systemCode = scanContext.primarySystemCode, idm = target.idm)
        scanContext = scanContext.copy(systemScanContexts = listOf(fallbackContext))
        return scanContext.systemScanContexts
    }

    private suspend fun ScanSession.discoverNodesWithSearchServiceCode(): NodeDiscoveryResult {
        val details = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        val systemContexts = ensureNodeDiscoverySystemContexts()

        for ((contextIndex, systemContext) in systemContexts.withIndex()) {
            val systemCodeHex = formatSystemCodeLabel(systemContext.systemCode)

            try {
                val nodes = mutableListOf<Node>()
                var requestCount = 0
                var stopReason = "completed"

                for (index in 0x0000..SearchServiceCodeCommand.MAX_ITERATOR_INDEX) {
                    val parsedSearchResponse =
                        transceiveWithRetries(
                            target = target,
                            commandLabel = "SearchServiceCodeCommand",
                            systemCode = systemContext.systemCode,
                        ) { activeTarget, _ ->
                            SearchServiceCodeCommand(activeTarget.idm, index)
                        }
                    requestCount++

                    val node = parsedSearchResponse.node
                    nodes.add(node)

                    if (node is System) {
                        ScanLog.d(
                            "CardScanService",
                            "Found system node at index $index for system $systemCodeHex, stopping iteration",
                        )
                        stopReason = "system node at index $index"
                        break
                    }
                }

                val normalizedNodes = normalizeDiscoveredNodes(nodes)
                val areaCount = normalizedNodes.filterIsInstance<Area>().size
                val serviceCount = normalizedNodes.filterIsInstance<Service>().size
                updatedSystemContexts.add(
                    systemContext.copy(
                        nodes = normalizedNodes,
                        registryPopulatedNodes = emptySet(),
                    )
                )
                details.add(
                    "System ${contextIndex + 1} ($systemCodeHex): Search Service Code found $areaCount area(s), $serviceCount service(s) in $requestCount request(s); $stopReason"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                ScanLog.w(
                    "CardScanService",
                    "Search Service Code discovery failed for system $systemCodeHex",
                    e,
                )
                updatedSystemContexts.add(systemContext)
                details.add(
                    "System ${contextIndex + 1} ($systemCodeHex): Search Service Code discovery failed (${e.message ?: e::class.simpleName ?: "Unknown error"})"
                )
            }
        }

        return NodeDiscoveryResult(
            methodLabel = "Search Service Code",
            systemContexts = updatedSystemContexts,
            details = details,
        )
    }

    private fun ScanSession.applyKnownNodeFallbacks(
        systemContexts: List<SystemScanContext>,
        force: Boolean,
        details: MutableList<String>,
    ): Pair<List<SystemScanContext>, Int> {
        var fallbackSystems = 0
        val updatedContexts = systemContexts.map { systemContext ->
            val fallbackNodes =
                knownNodesForSystemCode(systemContext.systemCode).ifEmpty {
                    normalizeDiscoveredNodes(emptyList())
                }
            val fallbackHasKnownNodes = hasNonStructuralNodes(fallbackNodes)
            val shouldUseFallback =
                force ||
                    systemContext.nodes.isEmpty() ||
                    (!hasNonStructuralNodes(systemContext.nodes) && fallbackHasKnownNodes)

            if (!shouldUseFallback) {
                systemContext
            } else {
                fallbackSystems++
                val source =
                    if (fallbackHasKnownNodes) {
                        "registry"
                    } else {
                        "default structural nodes"
                    }
                details.add(
                    "System ${formatSystemCodeLabel(systemContext.systemCode)}: populated ${fallbackNodes.size} fallback node(s) from $source"
                )
                systemContext.copy(
                    nodes = fallbackNodes,
                    registryPopulatedNodes = fallbackNodes.toSet(),
                )
            }
        }

        return updatedContexts to fallbackSystems
    }

    private fun ScanSession.knownNodesForSystemCode(systemCode: ByteArray?): List<Node> {
        val hex = systemCode?.toHexString()?.uppercase() ?: return emptyList()

        if (nodeMetadataProvider.isReady() && nodeMetadataProvider.isSystemCodeKnown(hex)) {
            val nodeDefinitions = nodeMetadataProvider.getNodesForSystemCode(hex)
            if (nodeDefinitions.isNotEmpty()) {
                val nodes = mutableListOf<Node>()
                for (definition in nodeDefinitions) {
                    val node =
                        when (definition.type) {
                            NodeDefinitionType.AREA -> Area.fromHexString(definition.code)
                            NodeDefinitionType.SERVICE -> Service.fromHexString(definition.code)
                            else -> null
                        }
                    if (node != null && !(node is Area && node.isRoot)) {
                        nodes.add(node)
                    }
                }

                return normalizeDiscoveredNodes(nodes.distinct().sortedBy { it.number })
            }
        }

        return listOf(System, Area.ROOT)
    }

    private fun hasNonStructuralNodes(nodes: List<Node>): Boolean = nodes.any { node ->
        node !is System && !(node is Area && node.isRoot)
    }

    private fun normalizeDiscoveredNodes(nodes: List<Node>): List<Node> {
        val normalized = mutableListOf<Node>()

        fun addNode(node: Node) {
            if (normalized.none { existing -> existing == node }) {
                normalized.add(node)
            }
        }

        addNode(System)
        addNode(Area.ROOT)
        nodes.forEach(::addNode)
        return normalized
    }
}
