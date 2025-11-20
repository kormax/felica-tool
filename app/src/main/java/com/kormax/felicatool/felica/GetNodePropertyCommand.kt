package com.kormax.felicatool.felica

/**
 * Get Node Property command used to acquire Node Property information
 *
 * This command can acquire the Node Property of Value-Limited Purse Service option or Communication
 * with MAC option. The command specifies the type of property to retrieve and the node codes to
 * query.
 */
class GetNodePropertyCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** Type of Node Property to retrieve */
    val nodePropertyType: NodePropertyType,

    /** Array of Node Codes to query. Each code is 2 bytes in Little Endian format. */
    val nodeCodes: Array<ByteArray>,
) : FelicaCommandWithIdm<GetNodePropertyResponse>(idm) {

    init {
        require(nodeCodes.isNotEmpty()) { "At least one node code must be specified" }
        require(nodeCodes.size <= MAX_NODES) { "Maximum 16 node codes can be requested at once" }
        require(nodeCodes.all { it.size == 2 }) { "Each node code must be exactly 2 bytes" }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = nodeCodes.size

    /**
     * Alternative constructor that accepts a list of Node objects and extracts their codes
     *
     * @param idm The 8-byte IDM of the target card
     * @param nodePropertyType Type of Node Property to retrieve
     * @param nodes List of Node objects (Area, Service, or System) to query
     */
    constructor(
        idm: ByteArray,
        nodePropertyType: NodePropertyType,
        nodes: List<Node>,
    ) : this(idm, nodePropertyType, nodes.map { it.code }.toTypedArray())

    override fun responseFromByteArray(data: ByteArray) =
        GetNodePropertyResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = BASE_LENGTH + 2 + (nodeCodes.size * 2)) {
            addByte(nodePropertyType.value.toByte())
            addByte(nodeCodes.size)
            nodeCodes.forEach { addBytes(it) }
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x28
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int =
            BASE_LENGTH + 1 + 1 + 2 // + type(1) + number_of_nodes(1) + min 1 node(2)
        const val MAX_NODES = 16

        /** Parse a Get Node Property command from raw bytes */
        fun fromByteArray(data: ByteArray): GetNodePropertyCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val nodePropertyTypeValue = uByte()
                val nodePropertyType =
                    NodePropertyType.fromValue(nodePropertyTypeValue)
                        ?: throw IllegalArgumentException(
                            "Unknown node property type: 0x${nodePropertyTypeValue.toString(16)}"
                        )

                val numberOfNodes = uByte()
                require(numberOfNodes in 1..MAX_NODES) {
                    "Number of nodes must be between 1 and $MAX_NODES, got $numberOfNodes"
                }
                require(remaining() >= numberOfNodes * 2) {
                    "Data size insufficient for $numberOfNodes nodes"
                }

                val nodeCodes = Array(numberOfNodes) { bytes(2) }

                GetNodePropertyCommand(idm, nodePropertyType, nodeCodes)
            }
    }
}
