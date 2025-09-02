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

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Type of Node Property (1 byte)
        data.add(nodePropertyType.value.toByte())

        // Number of nodes (1 byte)
        data.add(nodeCodes.size.toByte())

        // Node codes (2 bytes each, Little Endian)
        nodeCodes.forEach { nodeCode -> data.addAll(nodeCode.toList()) }

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x28
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int =
            FelicaCommandWithIdm.BASE_LENGTH +
                1 +
                1 +
                2 // + type(1) + number_of_nodes(1) + min 1 node(2)
        const val MAX_NODES = 16

        /** Parse a Get Node Property command from raw bytes */
        fun fromByteArray(data: ByteArray): GetNodePropertyCommand {
            require(data.size >= MIN_LENGTH) {
                "Data must be at least $MIN_LENGTH bytes, got ${data.size}"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Command code (1 byte)
            val commandCode = data[offset]
            require(commandCode == COMMAND_CODE.toByte()) {
                "Invalid command code: expected $COMMAND_CODE, got $commandCode"
            }
            offset++

            // IDM (8 bytes)
            val idm = data.sliceArray(offset until offset + 8)
            offset += 8

            // Type of Node Property (1 byte)
            val nodePropertyTypeValue = data[offset].toInt() and 0xFF
            val nodePropertyType =
                NodePropertyType.fromValue(nodePropertyTypeValue)
                    ?: throw IllegalArgumentException(
                        "Unknown node property type: 0x${nodePropertyTypeValue.toUByte().toString(16)}"
                    )
            offset++

            // Number of nodes (1 byte)
            val numberOfNodes = data[offset].toInt() and 0xFF
            require(numberOfNodes in 1..MAX_NODES) {
                "Number of nodes must be between 1 and $MAX_NODES, got $numberOfNodes"
            }
            require(data.size >= FelicaCommandWithIdm.BASE_LENGTH + 2 + numberOfNodes * 2) {
                "Data size insufficient for $numberOfNodes nodes"
            }
            offset++

            // Node codes (2 bytes each, Little Endian)
            val nodeCodes = mutableListOf<ByteArray>()
            repeat(numberOfNodes) {
                val nodeCode = data.sliceArray(offset until offset + 2)
                nodeCodes.add(nodeCode)
                offset += 2
            }

            return GetNodePropertyCommand(idm, nodePropertyType, nodeCodes.toTypedArray())
        }
    }
}
