package com.kormax.felicatool.util

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.shared.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Loads the shared nodes.json dataset once and exposes lookups for node names and providers. */
object NodeRegistry : NodeMetadataProvider {
    private val json = Json { ignoreUnknownKeys = true }
    private val initializationMutex = Mutex()
    private var initialized = false
    private var definitions: Map<String, List<NodeDefinition>> = emptyMap()
    private var systemDefinitions: Map<String, SystemDefinition> = emptyMap()
    private var filesystemSnapshotsByCard: Map<String, List<Set<FilesystemNodeId>>> = emptyMap()
    private var cardParentByCard: Map<String, String?> = emptyMap()

    override suspend fun ensureReady() {
        if (initialized) {
            return
        }

        initializationMutex.withLock {
            if (!initialized) {
                ensureInitialized(
                    jsonText = Res.readBytes("files/nodes.json").decodeToString(),
                    filesystemJsonText = Res.readBytes("files/filesystems.json").decodeToString(),
                    cardJsonText = Res.readBytes("files/cards.json").decodeToString(),
                )
            }
        }
    }

    fun ensureInitialized(
        jsonText: String,
        filesystemJsonText: String? = null,
        cardJsonText: String? = null,
    ) {
        if (initialized) {
            return
        }

        val (nodeDefinitions, systemDefinitions) = parseDefinitions(jsonText)
        definitions = nodeDefinitions
        this.systemDefinitions = systemDefinitions
        val filesystemIndex = filesystemJsonText?.let(::parseFilesystemIndex)
        filesystemSnapshotsByCard = filesystemIndex?.snapshotsByCard.orEmpty()
        cardParentByCard = cardJsonText?.let(::parseCardTaxonomy).orEmpty()
        initialized = true
    }

    fun getNodeName(systemCode: String, nodeCode: String, type: NodeDefinitionType): String? {
        return getNodeName(systemCode, nodeCode, null, type)
    }

    /**
     * Gets the name for a node, considering the parent area for proper resolution. This is
     * important for services that appear multiple times in different hierarchies with different
     * names.
     */
    fun getNodeName(
        systemCode: String,
        nodeCode: String,
        parentCode: String?,
        type: NodeDefinitionType,
        blockData: Map<Int, ByteArray>? = null,
    ): String? {
        val normalizedSystem = systemCode.uppercase()
        val normalizedCode = nodeCode.uppercase()
        val normalizedParent = parentCode?.uppercase()

        if (type == NodeDefinitionType.SYSTEM) {
            return systemDefinitions[normalizedSystem]?.name
        }

        val nodes = definitions[normalizedSystem] ?: return null
        val matchingNodes = nodes.filter { definition ->
            definition.type == type && definition.code == normalizedCode
        }
        if (matchingNodes.isEmpty()) {
            return null
        }

        return selectNameCandidates(nodes, matchingNodes, normalizedParent)
            .applyBlockDataPatterns(type, blockData)
            .firstOrNull { it.name != null }
            ?.name
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
    ): Set<String> = getProvidersForNode(systemCode, nodeCode, parentCode, type, null)

    fun getProvidersForNode(
        systemCode: String,
        nodeCode: String,
        parentCode: String?,
        type: NodeDefinitionType,
        blockData: Map<Int, ByteArray>?,
    ): Set<String> {
        val normalizedSystem = systemCode.uppercase()
        val normalizedCode = nodeCode.uppercase()
        val normalizedParent = parentCode?.uppercase()

        val nodes = definitions[normalizedSystem] ?: return emptySet()
        val matchingNodes = nodes.filter { definition ->
            definition.type == type && definition.code == normalizedCode
        }

        if (matchingNodes.isEmpty()) {
            return emptySet()
        }

        return selectProviderCandidates(nodes, matchingNodes, normalizedParent)
            .applyBlockDataPatterns(type, blockData)
            .flatMap { it.serviceProviders }
            .toSet()
    }

    override fun isReady(): Boolean = initialized

    override fun getNodesForSystemCode(systemCode: String): List<NodeDefinition> {
        val normalizedSystem = systemCode.uppercase()
        return definitions[normalizedSystem] ?: emptyList()
    }

    override fun isSystemCodeKnown(systemCode: String): Boolean {
        val normalizedSystem = systemCode.uppercase()
        return definitions.containsKey(normalizedSystem)
    }

    override fun getCardsForSystemCode(systemCode: String): Set<String> {
        val normalizedSystem = systemCode.uppercase()
        return systemDefinitions[normalizedSystem]?.cards.orEmpty()
    }

    override fun getSystemCodesForCard(cardId: String): Set<String> {
        val normalizedCardId =
            cardId.trim().uppercase().takeIf { it.isNotBlank() } ?: return emptySet()
        val matchingSystemCodes = mutableSetOf<String>()

        systemDefinitions.forEach { (systemCode, systemDefinition) ->
            if (normalizedCardId in systemDefinition.cards) {
                matchingSystemCodes += systemCode
            }
        }

        definitions.forEach { (systemCode, nodeDefinitions) ->
            if (nodeDefinitions.any { normalizedCardId in it.cards }) {
                matchingSystemCodes += systemCode
            }
        }

        return matchingSystemCodes
    }

    override fun getFilesystemSnapshotsForCard(cardId: String): List<Set<FilesystemNodeId>> {
        val normalizedCardId =
            cardId.trim().uppercase().takeIf { it.isNotBlank() } ?: return emptyList()
        return filesystemSnapshotsByCard[normalizedCardId].orEmpty()
    }

    override fun commonCardAncestor(cardIds: Set<String>): String? {
        val normalizedCardIds =
            cardIds.mapNotNullTo(linkedSetOf()) {
                it.trim().uppercase().takeIf { cardId -> cardId.isNotBlank() }
            }
        if (normalizedCardIds.isEmpty()) {
            return null
        }

        val lineages = normalizedCardIds.map { cardId -> cardLineage(cardId) ?: return null }
        val shortestLineageSize = lineages.minOf { it.size }
        var commonAncestor: String? = null

        for (index in 0 until shortestLineageSize) {
            val candidate = lineages.first()[index]
            if (lineages.all { it[index] == candidate }) {
                commonAncestor = candidate
            } else {
                break
            }
        }

        return commonAncestor
    }

    override fun getExtraBlocks(systemCode: String, nodeCode: String): Map<Int, String> {
        val normalizedSystem = systemCode.uppercase()
        val normalizedCode = nodeCode.uppercase()

        val nodes = definitions[normalizedSystem] ?: return emptyMap()
        val extraBlocks = linkedMapOf<Int, String>()
        nodes
            .filter { definition ->
                definition.type == NodeDefinitionType.SERVICE && definition.code == normalizedCode
            }
            .forEach { definition ->
                definition.extraBlocks.forEach { (blockNumber, blockName) ->
                    extraBlocks.putIfAbsent(blockNumber, blockName)
                }
            }

        return extraBlocks
    }

    private fun selectNameCandidates(
        nodes: List<NodeDefinition>,
        matchingNodes: List<NodeDefinition>,
        normalizedParent: String?,
    ): List<NodeDefinition> {
        if (normalizedParent == null) {
            return matchingNodes
        }

        val exactParentMatches = matchingNodes.filter {
            it.parent == normalizedParent && it.name != null
        }
        if (exactParentMatches.isNotEmpty()) {
            return exactParentMatches
        }

        val ancestorMatches = matchingNodes.filter { definition ->
            val definitionParent = definition.parent
            definitionParent != null &&
                definition.name != null &&
                isAncestorOf(nodes, normalizedParent, definitionParent)
        }
        if (ancestorMatches.isNotEmpty()) {
            return ancestorMatches
        }

        val descendantMatches = matchingNodes.filter { definition ->
            val definitionParent = definition.parent
            definitionParent != null &&
                definition.name != null &&
                isAncestorOf(nodes, definitionParent, normalizedParent)
        }
        if (descendantMatches.isNotEmpty()) {
            return descendantMatches
        }

        return matchingNodes.filter { it.name != null }
    }

    private fun selectProviderCandidates(
        nodes: List<NodeDefinition>,
        matchingNodes: List<NodeDefinition>,
        normalizedParent: String?,
    ): List<NodeDefinition> {
        if (normalizedParent == null) {
            return matchingNodes
        }

        val exactParentMatches = matchingNodes.filter { it.parent == normalizedParent }
        if (exactParentMatches.isNotEmpty()) {
            return exactParentMatches
        }

        val ancestorMatches = matchingNodes.filter { definition ->
            val definitionParent = definition.parent
            definitionParent != null && isAncestorOf(nodes, normalizedParent, definitionParent)
        }
        if (ancestorMatches.isNotEmpty()) {
            return ancestorMatches
        }

        val descendantMatches = matchingNodes.filter { definition ->
            val definitionParent = definition.parent
            definitionParent != null && isAncestorOf(nodes, definitionParent, normalizedParent)
        }
        if (descendantMatches.isNotEmpty()) {
            return descendantMatches
        }

        return matchingNodes.filter { it.parent == null }
    }

    private fun List<NodeDefinition>.applyBlockDataPatterns(
        type: NodeDefinitionType,
        blockData: Map<Int, ByteArray>?,
    ): List<NodeDefinition> {
        if (type != NodeDefinitionType.SERVICE || isEmpty()) {
            return this
        }

        val dataPatternDefinitions = filter { it.blockDataPatterns.isNotEmpty() }
        if (dataPatternDefinitions.isEmpty()) {
            return this
        }

        val genericDefinitions = filter { it.blockDataPatterns.isEmpty() }
        if (blockData.isNullOrEmpty()) {
            return genericDefinitions
        }

        val dataMatches = dataPatternDefinitions.filter { definition ->
            definition.blockDataPatterns.all { pattern -> pattern.matches(blockData) }
        }
        return dataMatches.ifEmpty { genericDefinitions }
    }

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
            currentCode = nodes.find { it.code == currentCode }?.parent
        }
        return false
    }

    private fun parseDefinitions(
        jsonText: String
    ): Pair<Map<String, List<NodeDefinition>>, Map<String, SystemDefinition>> {
        val nodeResult = mutableMapOf<String, List<NodeDefinition>>()
        val systemResult = mutableMapOf<String, SystemDefinition>()
        val root = json.parseToJsonElement(jsonText).jsonObject

        for ((systemCode, systemElement) in root) {
            val systemObject = systemElement.jsonObject
            val normalizedSystemCode = systemCode.uppercase()
            val systemName = systemObject.optionalRegistryString("name")
            val systemProvider = systemObject.optionalRegistryString("service_provider")
            val systemProviders = systemProvider?.let { setOf(it) } ?: emptySet()
            val systemCards = systemObject.registryStringSet("cards")

            if (systemName != null || systemProviders.isNotEmpty() || systemCards.isNotEmpty()) {
                systemResult[normalizedSystemCode] =
                    SystemDefinition(
                        systemCode = normalizedSystemCode,
                        name = systemName,
                        serviceProviders = systemProviders,
                        cards = systemCards,
                    )
            }

            val nodesArray = systemObject["nodes"]?.jsonArray ?: emptyList()
            val definitionsForSystem = mutableListOf<NodeDefinition>()

            for (nodeElement in nodesArray) {
                val nodeObject = nodeElement.jsonObject
                val code = nodeObject.optionalRegistryString("code") ?: continue
                val parent = nodeObject.optionalRegistryString("parent")
                val name = nodeObject.optionalRegistryString("name")
                val provider = nodeObject.optionalRegistryString("service_provider")
                val providers = provider?.let { setOf(it) } ?: emptySet()
                val cards = nodeObject.registryStringSet("cards")
                val mediums = nodeObject.cardMediums()
                val extraBlocks = nodeObject.extraBlocks()
                val blockDataPatterns = nodeObject.blockDataPatterns()
                val type =
                    if (code.length > 4) NodeDefinitionType.AREA else NodeDefinitionType.SERVICE

                definitionsForSystem +=
                    NodeDefinition(
                        code = code.uppercase(),
                        parent = parent?.uppercase(),
                        serviceProviders = providers,
                        type = type,
                        name = name,
                        cards = cards,
                        mediums = mediums,
                        extraBlocks = extraBlocks,
                        blockDataPatterns = blockDataPatterns,
                    )
            }

            if (definitionsForSystem.isNotEmpty()) {
                nodeResult[normalizedSystemCode] = definitionsForSystem
            }
        }

        return nodeResult to systemResult
    }

    private fun parseFilesystemIndex(jsonText: String): FilesystemIndex {
        val root = json.parseToJsonElement(jsonText).jsonArray
        val snapshotsByCard = linkedMapOf<String, MutableList<Set<FilesystemNodeId>>>()

        for (entryElement in root) {
            val entryObject = entryElement.jsonObject
            val cards = entryObject.registryStringSet("cards")
            if (cards.isEmpty()) {
                continue
            }

            val signatureObject = entryObject["signature"]?.jsonObject ?: continue
            val snapshotNodes =
                signatureObject
                    .flatMap { (systemCode, nodeArrayElement) ->
                        val normalizedSystemCode = systemCode.uppercase()
                        val nodeCodes =
                            nodeArrayElement.jsonArray
                                .mapNotNull { nodeElement ->
                                    nodeElement.jsonPrimitive.contentOrNull
                                        ?.trim()
                                        ?.takeIf { it.isNotBlank() }
                                        ?.uppercase()
                                }
                                .toSet()
                        filesystemNodeIdsForSnapshot(normalizedSystemCode, nodeCodes)
                    }
                    .toSet()
            if (snapshotNodes.isEmpty()) {
                continue
            }

            cards.forEach { card ->
                snapshotsByCard.getOrPut(card) { mutableListOf() } += snapshotNodes
            }
        }

        return FilesystemIndex(
            snapshotsByCard = snapshotsByCard.mapValues { (_, snapshots) -> snapshots.toList() }
        )
    }

    private fun parseCardTaxonomy(jsonText: String): Map<String, String?> {
        val root = json.parseToJsonElement(jsonText).jsonObject
        val result = linkedMapOf<String, String?>()

        fun collectCards(cardObject: JsonObject, parent: String?) {
            cardObject.forEach { (cardId, childElement) ->
                val normalizedCardId = cardId.uppercase()
                result[normalizedCardId] = parent
                collectCards(childElement.jsonObject, normalizedCardId)
            }
        }

        collectCards(root, parent = null)
        return result
    }

    private fun filesystemNodeIdsForSnapshot(
        systemCode: String,
        nodeCodes: Set<String>,
    ): Set<FilesystemNodeId> {
        val parsedNodes = nodeCodes.mapNotNull { code ->
            parseFilesystemNode(code)?.let { node -> code.uppercase() to node }
        }
        val areas = parsedNodes.mapNotNull { (_, node) -> node as? Area }

        return parsedNodes
            .map { (code, node) ->
                val parentCode =
                    when (node) {
                        is Area -> findParentArea(node, areas)?.fullCode?.toHexString()?.uppercase()
                        is Service ->
                            findContainingArea(node, areas)?.fullCode?.toHexString()?.uppercase()
                        else -> null
                    }
                FilesystemNodeId(
                    systemCode = systemCode.uppercase(),
                    parentCode = parentCode,
                    code = code.uppercase(),
                )
            }
            .toSet()
    }

    private fun parseFilesystemNode(code: String): Node? =
        when (code.length) {
            8 -> runCatching { Area.fromHexString(code) }.getOrNull()
            4 -> runCatching { Service.fromHexString(code) }.getOrNull()
            else -> null
        }

    private fun cardLineage(cardId: String): List<String>? {
        if (cardId !in cardParentByCard) {
            return null
        }

        val lineage = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        var currentCardId: String? = cardId

        while (currentCardId != null && currentCardId !in visited) {
            lineage += currentCardId
            visited += currentCardId
            currentCardId = cardParentByCard[currentCardId]
        }

        return lineage.asReversed()
    }

    private fun findParentArea(area: Area, areas: List<Area>): Area? {
        if (area.isRoot) {
            return null
        }

        val parentAreas = areas.filter { candidate ->
            candidate != area &&
                area.belongsTo(candidate) &&
                !area.hasSameRangeButCannotNestIn(candidate)
        }
        return parentAreas.minWithOrNull(areaContainmentComparator)
    }

    private fun Area.hasSameRangeButCannotNestIn(candidate: Area): Boolean =
        number == candidate.number &&
            endNumber == candidate.endNumber &&
            attribute.canCreateSubArea &&
            !candidate.attribute.canCreateSubArea

    private fun findContainingArea(node: Node, areas: List<Area>): Area? =
        areas
            .filter { candidate -> node.belongsTo(candidate) }
            .minWithOrNull(areaContainmentComparator)

    private val areaContainmentComparator =
        compareBy<Area> { it.endNumber - it.number }
            .thenByDescending { it.number }
            .thenBy { it.attribute.canCreateSubArea }
            .thenBy { it.fullCode.toHexString() }

    private data class FilesystemIndex(
        val snapshotsByCard: Map<String, List<Set<FilesystemNodeId>>>
    )

    private fun JsonObject.extraBlocks(): Map<Int, String> {
        val extraBlocksObject = this["extra_blocks"]?.jsonObject ?: return emptyMap()
        return extraBlocksObject
            .mapNotNull { (blockHex, blockNameElement) ->
                val blockNumber = blockHex.toIntOrNull(16)
                val blockName = blockNameElement.jsonPrimitive.contentOrNull?.trim()
                if (blockNumber != null && !blockName.isNullOrBlank()) {
                    blockNumber to blockName
                } else {
                    null
                }
            }
            .toMap()
    }

    private fun JsonObject.blockDataPatterns(): List<NodeBlockDataPattern> {
        val dataObject = this["data"]?.jsonObject ?: return emptyList()
        return dataObject.mapNotNull { (blockHex, patternElement) ->
            val blockNumber = blockHex.toIntOrNull(16)
            val pattern = patternElement.jsonPrimitive.contentOrNull?.trim()
            if (blockNumber != null && !pattern.isNullOrBlank()) {
                NodeBlockDataPattern(blockNumber, pattern)
            } else {
                null
            }
        }
    }

    private fun JsonObject.registryStringSet(key: String): Set<String> {
        val values = this[key]?.jsonArray ?: return emptySet()
        return values
            .mapNotNull { element ->
                element.jsonPrimitive.contentOrNull?.trim()?.takeIf { it.isNotBlank() }
            }
            .map { it.uppercase() }
            .toSet()
    }

    private fun JsonObject.cardMediums(): Set<CardMedium> =
        registryStringSet("mediums").mapNotNull(CardMedium::fromRegistryValue).toSet()

    private fun JsonObject.optionalRegistryString(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("null", ignoreCase = true)
        }
    }
}
