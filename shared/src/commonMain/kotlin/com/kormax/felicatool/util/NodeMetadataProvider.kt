package com.kormax.felicatool.util

interface NodeMetadataProvider {
    suspend fun ensureReady()

    fun isReady(): Boolean

    fun isSystemCodeKnown(systemCode: String): Boolean

    fun getCardsForSystemCode(systemCode: String): Set<String>

    fun getSystemCodesForCard(cardId: String): Set<String>

    fun getFilesystemSnapshotsForCard(cardId: String): List<Set<FilesystemNodeId>>

    fun commonCardAncestor(cardIds: Set<String>): String?

    fun getNodesForSystemCode(systemCode: String): List<NodeDefinition>

    fun getExtraBlocks(systemCode: String, nodeCode: String): Map<Int, String>
}

object EmptyNodeMetadataProvider : NodeMetadataProvider {
    override suspend fun ensureReady() = Unit

    override fun isReady(): Boolean = false

    override fun isSystemCodeKnown(systemCode: String): Boolean = false

    override fun getCardsForSystemCode(systemCode: String): Set<String> = emptySet()

    override fun getSystemCodesForCard(cardId: String): Set<String> = emptySet()

    override fun getFilesystemSnapshotsForCard(cardId: String): List<Set<FilesystemNodeId>> =
        emptyList()

    override fun commonCardAncestor(cardIds: Set<String>): String? = null

    override fun getNodesForSystemCode(systemCode: String): List<NodeDefinition> = emptyList()

    override fun getExtraBlocks(systemCode: String, nodeCode: String): Map<Int, String> = emptyMap()
}

data class NodeDefinition(
    val code: String,
    val parent: String?,
    val serviceProviders: Set<String>,
    val type: NodeDefinitionType,
    val name: String? = null,
    val cards: Set<String> = emptySet(),
    val mediums: Set<CardMedium> = emptySet(),
    val extraBlocks: Map<Int, String> = emptyMap(),
    val blockDataPatterns: List<NodeBlockDataPattern> = emptyList(),
)

data class SystemDefinition(
    val systemCode: String,
    val name: String?,
    val serviceProviders: Set<String>,
    val cards: Set<String> = emptySet(),
)

data class FilesystemNodeId(
    val systemCode: String,
    val parentCode: String?,
    val code: String,
)

data class NodeBlockDataPattern(val blockNumber: Int, val pattern: String) {
    private val regex = Regex(pattern, RegexOption.IGNORE_CASE)

    fun matches(blockData: Map<Int, ByteArray>): Boolean {
        val blockBytes = blockData[blockNumber] ?: return false
        return regex.matches(blockBytes.toLowerHexString())
    }

    private fun ByteArray.toLowerHexString(): String =
        joinToString(separator = "") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }
}

enum class NodeDefinitionType {
    SYSTEM,
    AREA,
    SERVICE,
}

enum class CardMedium {
    CARD,
    MOBILE;

    companion object {
        fun fromRegistryValue(value: String): CardMedium? = entries.firstOrNull {
            it.name == value.trim().uppercase()
        }
    }
}
