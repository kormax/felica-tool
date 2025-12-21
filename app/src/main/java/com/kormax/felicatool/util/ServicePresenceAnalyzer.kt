package com.kormax.felicatool.util

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.SystemScanContext

object ServicePresenceAnalyzer {
    data class ProviderPresence(val provider: String, val systems: Set<String>, val nodeCount: Int)

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
                        .addMatch(systemCode)
                }
            }

            systemContext.nodes.filterIsInstance<Service>().forEach { service ->
                val parentCode = findContainingArea(service, systemContext)?.fullCode?.toHexString()
                val names =
                    NodeRegistry.getProvidersForNode(
                        systemCode,
                        service.code.toHexString(),
                        parentCode,
                        NodeDefinitionType.SERVICE,
                    )
                if (names.isEmpty()) {
                    unknownServiceCount++
                } else {
                    names.forEach { providerName ->
                        providers
                            .getOrPut(providerName) { MutableProviderPresence(providerName) }
                            .addMatch(systemCode)
                    }
                }
            }
        }

        val providerList =
            providers.values
                .sortedWith(
                    compareByDescending<MutableProviderPresence> { it.nodeCount }
                        .thenBy { it.provider }
                )
                .map { it.toImmutable() }

        return DetectionResult(providerList, unknownServiceCount)
    }

    private fun findContainingArea(node: Node, context: SystemScanContext): Area? {
        return context.nodes
            .filterIsInstance<Area>()
            .filter { candidate -> node.belongsTo(candidate) }
            .minByOrNull { it.endNumber - it.number }
    }

    private fun findParentArea(area: Area, context: SystemScanContext): Area? {
        val parentAreas =
            context.nodes.filterIsInstance<Area>().filter { other ->
                other != area && area.belongsTo(other)
            }
        return parentAreas.minByOrNull { it.endNumber - it.number }
    }

    private class MutableProviderPresence(val provider: String) {
        private val systemCodes = linkedSetOf<String>()
        var nodeCount: Int = 0
            private set

        fun addMatch(systemCode: String) {
            systemCodes += systemCode
            nodeCount++
        }

        fun toImmutable(): ProviderPresence {
            return ProviderPresence(provider, systemCodes.toSet(), nodeCount)
        }
    }
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte -> "%02X".format(byte) }
}
