package com.kormax.felicatool.felica

/**
 * Request Service v2 command used to verify the existence of Area and Service, and to acquire Key
 * Version
 *
 * This command is used to check if specified Areas or Services exist on the card and to retrieve
 * their Key Versions for each supported encryption type. When the specified Area or Service exists
 * and the Key is assigned, the card returns its Key Version. When it doesn't exist or the Key is
 * not assigned, the card returns 0xFFFF as Key Version.
 *
 * The main difference from RequestService is that this command returns key versions for both AES
 * and DES encryption types when supported by the card.
 */
class RequestServiceV2Command(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /**
     * Array of Node Codes (Area Code or Service Code) to query. Each code is 2 bytes in Little
     * Endian format. For System Key Version, use 0xFFFF.
     */
    val nodeCodes: Array<ByteArray>,
) : FelicaCommandWithIdm<RequestServiceV2Response>(idm) {

    init {
        require(nodeCodes.isNotEmpty()) { "At least one node code must be specified" }
        require(nodeCodes.size <= MAX_NODES) { "Maximum 32 node codes can be requested at once" }
        require(nodeCodes.all { it.size == 2 }) { "Each node code must be exactly 2 bytes" }
    }

    /**
     * Alternative constructor that accepts a list of Node objects and extracts their codes
     *
     * @param idm The 8-byte IDM of the target card
     * @param nodes List of Node objects (Area, Service, or System) to query
     */
    constructor(idm: ByteArray, nodes: List<Node>) : this(idm, nodes.map { it.code }.toTypedArray())

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = nodeCodes.size

    override fun responseFromByteArray(data: ByteArray) =
        RequestServiceV2Response.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Number of nodes (1 byte)
        data.add(nodeCodes.size.toByte())

        // Node codes (2 bytes each, Little Endian)
        nodeCodes.forEach { nodeCode -> data.addAll(nodeCode.toList()) }

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x32
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int =
            FelicaCommandWithIdm.BASE_LENGTH + 1 + 2 // + number_of_nodes(1) + min 1 node(2)
        const val MAX_NODES = 32

        /** Parse a Request Service v2 command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestServiceV2Command {
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

            // Number of nodes (1 byte)
            val numberOfNodes = data[offset].toInt() and 0xFF
            require(numberOfNodes in 1..32) {
                "Number of nodes must be between 1 and 32, got $numberOfNodes"
            }
            require(data.size >= FelicaCommandWithIdm.BASE_LENGTH + 1 + numberOfNodes * 2) {
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

            return RequestServiceV2Command(idm, nodeCodes.toTypedArray())
        }
    }
}
