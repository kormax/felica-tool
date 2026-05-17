package com.kormax.felicatool.util

interface NodeMetadataProvider {
    suspend fun ensureReady()

    fun isReady(): Boolean

    fun isSystemCodeKnown(systemCode: String): Boolean

    fun getNodesForSystemCode(systemCode: String): List<NodeDefinition>

    fun getExtraBlocks(systemCode: String, nodeCode: String): Map<Int, String>
}

object EmptyNodeMetadataProvider : NodeMetadataProvider {
    override suspend fun ensureReady() = Unit

    override fun isReady(): Boolean = false

    override fun isSystemCodeKnown(systemCode: String): Boolean = false

    override fun getNodesForSystemCode(systemCode: String): List<NodeDefinition> = emptyList()

    override fun getExtraBlocks(systemCode: String, nodeCode: String): Map<Int, String> = emptyMap()
}

data class NodeDefinition(
    val code: String,
    val parent: String?,
    val serviceProviders: Set<String>,
    val type: NodeDefinitionType,
    val name: String? = null,
    val extraBlocks: Map<Int, String> = emptyMap(),
    val blockDataPatterns: List<NodeBlockDataPattern> = emptyList(),
)

data class SystemDefinition(
    val systemCode: String,
    val name: String?,
    val serviceProviders: Set<String>,
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
