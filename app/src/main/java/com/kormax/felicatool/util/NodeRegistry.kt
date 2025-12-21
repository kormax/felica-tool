package com.kormax.felicatool.util

import android.content.Context
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

/** Loads the shared nodes.json dataset once and exposes helper lookups for service providers. */
object NodeRegistry {
    private val isInitialized = AtomicBoolean(false)
    private val lock = Any()
    private var definitions: Map<String, List<NodeDefinition>> = emptyMap()
    private var systemDefinitions: Map<String, SystemDefinition> = emptyMap()

    fun ensureInitialized(context: Context) {
        if (isInitialized.get()) {
            return
        }

        synchronized(lock) {
            if (isInitialized.get()) {
                return
            }

            val assetManager = context.applicationContext.assets
            val jsonText = assetManager.open("nodes.json").bufferedReader().use { it.readText() }
            val (nodeDefs, sysDefs) = parseDefinitions(jsonText)
            definitions = nodeDefs
            systemDefinitions = sysDefs
            isInitialized.set(true)
        }
    }

    fun getNodeName(systemCode: String, nodeCode: String, type: NodeDefinitionType): String? {
        val normalizedSystem = systemCode.uppercase()
        val normalizedCode = nodeCode.uppercase()

        // For SYSTEM type, return the system name
        if (type == NodeDefinitionType.SYSTEM) {
            return systemDefinitions[normalizedSystem]?.name
        }

        val nodes = definitions[normalizedSystem] ?: return null
        val matchingNodes =
            nodes.filter { definition ->
                definition.type == type && definition.code == normalizedCode
            }

        return matchingNodes.firstOrNull()?.name
    }

    fun getSystemProviders(systemCode: String): Set<String> {
        val normalizedSystem = systemCode.uppercase()
        return systemDefinitions[normalizedSystem]?.serviceProviders ?: emptySet()
    }

    fun getProvidersForNode(
        systemCode: String,
        nodeCode: String,
        parentCode: String?,
        type: NodeDefinitionType,
    ): Set<String> {
        val normalizedSystem = systemCode.uppercase()
        val normalizedCode = nodeCode.uppercase()
        val normalizedParent = parentCode?.uppercase()

        val nodes = definitions[normalizedSystem] ?: return emptySet()
        val matchingNodes =
            nodes.filter { definition ->
                definition.type == type && definition.code == normalizedCode
            }

        if (matchingNodes.isEmpty()) {
            return emptySet()
        }

        // If no parent provided, return all matching providers
        if (normalizedParent == null) {
            return matchingNodes.flatMap { it.serviceProviders }.toSet()
        }

        // Try exact parent match first
        val exactParentMatches = matchingNodes.filter { it.parent == normalizedParent }
        if (exactParentMatches.isNotEmpty()) {
            return exactParentMatches.flatMap { it.serviceProviders }.toSet()
        }

        // Check if the provided parent is an ancestor of any matching node's parent
        // by tracing up the parent chain in the definitions
        val ancestorMatches =
            matchingNodes.filter { definition ->
                definition.parent != null &&
                    isAncestorOf(nodes, normalizedParent, definition.parent)
            }
        if (ancestorMatches.isNotEmpty()) {
            return ancestorMatches.flatMap { it.serviceProviders }.toSet()
        }

        // Check if any matching node's defined parent is an ancestor of the provided parent
        // This handles cases where the card has a more specific parent than defined
        val descendantMatches =
            matchingNodes.filter { definition ->
                definition.parent != null &&
                    isAncestorOf(nodes, definition.parent, normalizedParent)
            }
        if (descendantMatches.isNotEmpty()) {
            return descendantMatches.flatMap { it.serviceProviders }.toSet()
        }

        // Fall back to nodes with null parent if any, otherwise return empty
        // (don't return all providers when parent doesn't match)
        return matchingNodes.filter { it.parent == null }.flatMap { it.serviceProviders }.toSet()
    }

    /**
     * Checks if potentialAncestor is an ancestor of targetCode in the node hierarchy. Traces up
     * from targetCode through parent links to see if potentialAncestor is reached.
     */
    private fun isAncestorOf(
        nodes: List<NodeDefinition>,
        potentialAncestor: String,
        targetCode: String,
    ): Boolean {
        var currentCode: String? = targetCode
        val visited = mutableSetOf<String>()

        while (currentCode != null && currentCode !in visited) {
            if (currentCode == potentialAncestor) {
                return true
            }
            visited.add(currentCode)
            // Find the node definition for currentCode and get its parent
            currentCode = nodes.find { it.code == currentCode }?.parent
        }
        return false
    }

    fun isReady(): Boolean = isInitialized.get()

    /**
     * Gets all node definitions for a given system code.
     *
     * @param systemCode The system code (e.g., "88B4")
     * @return List of node definitions for the system, or empty list if none found
     */
    fun getNodesForSystemCode(systemCode: String): List<NodeDefinition> {
        val normalizedSystem = systemCode.uppercase()
        return definitions[normalizedSystem] ?: emptyList()
    }

    /**
     * Checks if a system code is known in the database.
     *
     * @param systemCode The system code (e.g., "88B4")
     * @return true if the system code exists in the database
     */
    fun isSystemCodeKnown(systemCode: String): Boolean {
        val normalizedSystem = systemCode.uppercase()
        return definitions.containsKey(normalizedSystem)
    }

    /**
     * Gets the extra blocks defined for a node in a given system. Extra blocks are hidden blocks
     * stashed at the end of the block number space.
     *
     * @param systemCode The system code (e.g., "88B4")
     * @param nodeCode The node code (e.g., "0900")
     * @return Map of block number to block name, or empty map if none defined
     */
    fun getExtraBlocks(systemCode: String, nodeCode: String): Map<Int, String> {
        val normalizedSystem = systemCode.uppercase()
        val normalizedCode = nodeCode.uppercase()

        val nodes = definitions[normalizedSystem] ?: return emptyMap()
        val matchingNode = nodes.firstOrNull { definition -> definition.code == normalizedCode }

        return matchingNode?.extraBlocks ?: emptyMap()
    }

    private fun parseDefinitions(
        jsonText: String
    ): Pair<Map<String, List<NodeDefinition>>, Map<String, SystemDefinition>> {
        val nodeResult = mutableMapOf<String, List<NodeDefinition>>()
        val systemResult = mutableMapOf<String, SystemDefinition>()
        val root = JSONObject(jsonText)
        val keys = root.keys()

        while (keys.hasNext()) {
            val systemCode = keys.next()
            val systemObject = root.optJSONObject(systemCode) ?: continue

            // Parse system-level information
            val systemName =
                systemObject.optString("name").takeIf {
                    systemObject.has("name") &&
                        it.isNotBlank() &&
                        !it.equals("null", ignoreCase = true)
                }
            val systemProvider =
                systemObject.optString("service_provider").takeIf {
                    systemObject.has("service_provider") && it.isNotBlank()
                }
            val systemProviders = systemProvider?.let { mutableSetOf(it) } ?: mutableSetOf<String>()

            if (systemName != null || systemProviders.isNotEmpty()) {
                systemResult[systemCode.uppercase()] =
                    SystemDefinition(
                        systemCode = systemCode.uppercase(),
                        name = systemName,
                        serviceProviders = systemProviders.toSet(),
                    )
            }

            val nodesArray = systemObject.optJSONArray("nodes") ?: JSONArray()
            val definitionsForSystem = mutableListOf<NodeDefinition>()

            for (index in 0 until nodesArray.length()) {
                val nodeObject = nodesArray.optJSONObject(index) ?: continue
                val code = nodeObject.optString("code").takeIf { it.isNotBlank() } ?: continue
                val parent =
                    nodeObject.optString("parent").takeIf {
                        nodeObject.has("parent") && !nodeObject.isNull("parent") && it.isNotBlank()
                    }
                val name =
                    nodeObject.optString("name").takeIf {
                        nodeObject.has("name") &&
                            it.isNotBlank() &&
                            !it.equals("null", ignoreCase = true)
                    }
                val provider =
                    nodeObject.optString("service_provider").takeIf {
                        nodeObject.has("service_provider") && it.isNotBlank()
                    }
                val providers = provider?.let { mutableSetOf(it) } ?: mutableSetOf()

                // Parse extra_blocks if present
                val extraBlocks = mutableMapOf<Int, String>()
                val extraBlocksObject = nodeObject.optJSONObject("extra_blocks")
                if (extraBlocksObject != null) {
                    val extraBlocksKeys = extraBlocksObject.keys()
                    while (extraBlocksKeys.hasNext()) {
                        val blockHex = extraBlocksKeys.next()
                        val blockName = extraBlocksObject.optString(blockHex)
                        val blockNumber = blockHex.toIntOrNull(16)
                        if (blockNumber != null && blockName.isNotBlank()) {
                            extraBlocks[blockNumber] = blockName
                        }
                    }
                }

                val type =
                    if (code.length > 4) NodeDefinitionType.AREA else NodeDefinitionType.SERVICE

                definitionsForSystem +=
                    NodeDefinition(
                        code = code.uppercase(),
                        parent = parent?.uppercase(),
                        serviceProviders = providers.toSet(),
                        type = type,
                        name = name,
                        extraBlocks = extraBlocks.toMap(),
                    )
            }

            if (definitionsForSystem.isNotEmpty()) {
                nodeResult[systemCode.uppercase()] = definitionsForSystem
            }
        }

        return nodeResult to systemResult
    }
}

data class NodeDefinition(
    val code: String,
    val parent: String?,
    val serviceProviders: Set<String>,
    val type: NodeDefinitionType,
    val name: String? = null,
    /** Extra blocks mapping: block number (hex) -> block name */
    val extraBlocks: Map<Int, String> = emptyMap(),
)

data class SystemDefinition(
    val systemCode: String,
    val name: String?,
    val serviceProviders: Set<String>,
)

enum class NodeDefinitionType {
    SYSTEM,
    AREA,
    SERVICE,
}
