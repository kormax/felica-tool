package com.kormax.felicatool.util

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.SystemScanContext

object ServicePresenceAnalyzer {
    data class ProviderPresence(
        val provider: String,
        val systems: Set<String>,
        val nodeCount: Int,
    )

    data class DetectionResult(val providers: List<ProviderPresence>, val unknownServiceCount: Int)

    fun detectProviders(scanContext: CardScanContext): DetectionResult {
        if (!NodeRegistry.isReady()) {
            return DetectionResult(emptyList(), 0)
        }

        val providers = mutableMapOf<String, MutableProviderPresence>()
        var unknownServiceCount = 0

        scanContext.systemScanContexts.forEach { systemContext ->
            val systemCode = systemContext.systemCode?.toHexString()?.uppercase() ?: return@forEach

            systemContext.nodes.filterIsInstance<Area>().forEach { area ->
                val parentCode = findParentArea(area, systemContext)?.fullCode?.toHexString()
                val names =
                    NodeRegistry.getProvidersForNode(
                        systemCode,
                        area.fullCode.toHexString(),
                        parentCode,
                        NodeDefinitionType.AREA,
                    )
                names.forEach { providerName ->
                    providers
                        .getOrPut(providerName) { MutableProviderPresence(providerName) }
                        .addMatch(systemCode, isRootArea = area.isRoot)
                }
            }

            systemContext.nodes.filterIsInstance<Service>().forEach { service ->
                val parentCode = findContainingArea(service, systemContext)?.fullCode?.toHexString()
                val blockData = systemContext.serviceBlockData[service]
                val names =
                    NodeRegistry.getProvidersForNode(
                        systemCode,
                        service.code.toHexString(),
                        parentCode,
                        NodeDefinitionType.SERVICE,
                        blockData = blockData,
                    )
                if (names.isEmpty()) {
                    unknownServiceCount++
                } else {
                    names.forEach { providerName ->
                        providers
                            .getOrPut(providerName) { MutableProviderPresence(providerName) }
                            .addMatch(systemCode, isRootArea = false)
                    }
                }
            }
        }

        val hasAnyNonRootProvider = providers.values.any { it.hasNonRootNode }
        val visibleProviders =
            providers.values.filter { it.hasNonRootNode || !hasAnyNonRootProvider }
        val systemPriorities = visibleProviders.systemPriorities()
        val providerList =
            visibleProviders.sortedWith(providerComparator(systemPriorities)).map {
                it.toImmutable(systemPriorities)
            }

        return DetectionResult(providerList, unknownServiceCount)
    }

    private fun List<MutableProviderPresence>.systemPriorities(): Map<String, Int> {
        val largestProviderGroupNodeCounts = mutableMapOf<String, Int>()
        val totalProviderNodeCounts = mutableMapOf<String, Int>()

        forEach { provider ->
            provider.systemNodeCounts.forEach { (systemCode, nodeCount) ->
                largestProviderGroupNodeCounts[systemCode] =
                    maxOf(largestProviderGroupNodeCounts[systemCode] ?: 0, nodeCount)
                totalProviderNodeCounts[systemCode] =
                    (totalProviderNodeCounts[systemCode] ?: 0) + nodeCount
            }
        }

        return largestProviderGroupNodeCounts.keys
            .sortedWith(
                compareByDescending<String> { systemCode ->
                        largestProviderGroupNodeCounts[systemCode] ?: 0
                    }
                    .thenByDescending { systemCode -> totalProviderNodeCounts[systemCode] ?: 0 }
                    .thenBy { it }
            )
            .mapIndexed { index, systemCode -> systemCode to index }
            .toMap()
    }

    private fun providerComparator(
        systemPriorities: Map<String, Int>
    ): Comparator<MutableProviderPresence> =
        compareBy<MutableProviderPresence> { provider ->
                provider.primarySystem(systemPriorities)?.let { systemPriorities[it] }
                    ?: Int.MAX_VALUE
            }
            .thenByDescending { provider ->
                provider.primarySystem(systemPriorities)?.let { provider.systemNodeCounts[it] } ?: 0
            }
            .thenByDescending { it.nodeCount }
            .thenBy { it.provider }

    private fun findContainingArea(node: Node, context: SystemScanContext): Area? {
        return context.nodes
            .filterIsInstance<Area>()
            .filter { candidate -> node.belongsTo(candidate) }
            .minWithOrNull(areaContainmentComparator)
    }

    private fun findParentArea(area: Area, context: SystemScanContext): Area? {
        val parentAreas =
            context.nodes.filterIsInstance<Area>().filter { other ->
                other != area && area.belongsTo(other)
            }
        return parentAreas.minWithOrNull(areaContainmentComparator)
    }

    private val areaContainmentComparator =
        compareBy<Area> { it.endNumber - it.number }
            .thenByDescending { it.number }
            .thenBy { it.attribute.canCreateSubArea }
            .thenBy { it.fullCode.toHexString() }

    private class MutableProviderPresence(val provider: String) {
        private val systemCodes = linkedSetOf<String>()
        val systemNodeCounts = linkedMapOf<String, Int>()
        var nodeCount: Int = 0
            private set

        var hasNonRootNode: Boolean = false
            private set

        fun addMatch(systemCode: String, isRootArea: Boolean) {
            systemCodes += systemCode
            systemNodeCounts[systemCode] = (systemNodeCounts[systemCode] ?: 0) + 1
            nodeCount++
            if (!isRootArea) {
                hasNonRootNode = true
            }
        }

        fun toImmutable(systemPriorities: Map<String, Int>): ProviderPresence {
            val orderedSystemCodes =
                systemCodes
                    .sortedWith(
                        compareBy<String> { systemPriorities[it] ?: Int.MAX_VALUE }.thenBy { it }
                    )
                    .toCollection(linkedSetOf())
            return ProviderPresence(
                provider = provider,
                systems = orderedSystemCodes,
                nodeCount = nodeCount,
            )
        }

        fun primarySystem(systemPriorities: Map<String, Int>): String? =
            systemNodeCounts.keys.minWithOrNull(
                compareBy<String> { systemPriorities[it] ?: Int.MAX_VALUE }
                    .thenByDescending { systemNodeCounts[it] ?: 0 }
                    .thenBy { it }
            )
    }
}
