package com.kormax.felicatool.felica

/**
 * Authentication 1 AES command used for AES authentication with FeliCa cards.
 *
 * This command performs the first phase of AES authentication by sending node codes and challenge1A
 * to the card. The card responds with data. Unlike the DES version, this combines areas and
 * services into a single node codes field with a flag byte (default 0x00) as the first byte of the
 * command data structure.
 */
class Authentication1AesCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** Array of Node codes to authenticate (2 bytes each) */
    val nodeCodes: Array<ByteArray>,

    /** Challenge1A (16 bytes) sent to the card for authentication */
    val challenge1A: ByteArray,

    /** Flag byte (default 0x00) */
    val flag: Byte = 0x00,
) : FelicaCommandWithIdm<Authentication1AesResponse>(idm) {

    init {
        require(nodeCodes.isNotEmpty()) { "At least one node code must be specified" }
        require(nodeCodes.size <= MAX_NODES) {
            "Maximum $MAX_NODES nodes can be authenticated at once"
        }
        require(nodeCodes.all { it.size == 2 }) { "Each node code must be exactly 2 bytes" }
        require(challenge1A.size == 16) {
            "Challenge1A must be exactly 16 bytes, got ${challenge1A.size}"
        }
    }

    /**
     * Alternative constructor that accepts lists of Node objects and combines their codes into a
     * single node codes array.
     *
     * @param idm The 8-byte IDM of the target card
     * @param nodes List of Node objects to authenticate
     * @param challenge1A Challenge1A (16 bytes) sent to the card for authentication
     * @param flag Flag byte (default 0x00) - first byte of the data structure
     */
    constructor(
        idm: ByteArray,
        nodes: List<Node>,
        challenge1A: ByteArray,
        flag: Byte = 0x00,
    ) : this(idm, nodes.map { it.code }.toTypedArray(), challenge1A, flag)

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = nodeCodes.size

    override fun responseFromByteArray(data: ByteArray) =
        Authentication1AesResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Flag byte (replaces area count from DES version)
        data.add(flag)

        // Add length
        data.add(nodeCodes.size.toByte())

        // Node codes (2 bytes each) - combines nodes
        nodeCodes.forEach { nodeCode -> data.addAll(nodeCode.toList()) }

        // Challenge1A (16 bytes)
        data.addAll(challenge1A.toList())

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x40
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int =
            FelicaCommandWithIdm.BASE_LENGTH +
                1 +
                1 +
                2 +
                16 // + flag(1) + nodeCount(1) + min 1 node(2) + challenge1A(16)
        const val MAX_NODES = 16

        /** Parse an Authentication 1 AES command from raw bytes */
        fun fromByteArray(data: ByteArray): Authentication1AesCommand {
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

            // Flag byte (replaces area count)
            val flag = data[offset]
            offset++

            // Length of node codes (1 byte)
            val numberOfNodes = data[offset].toInt() and 0xFF
            offset++
            require(numberOfNodes > 0) { "At least one node code must be specified" }
            require(numberOfNodes <= MAX_NODES) {
                "Maximum $MAX_NODES nodes can be authenticated at once"
            }

            // Verify we have enough bytes for the specified number of node codes + challenge1A
            require(offset + (numberOfNodes * 2) + 16 <= data.size) {
                "Data size insufficient for $numberOfNodes node codes and challenge1A"
            }

            // Node codes (2 bytes each)
            val nodeCodes = mutableListOf<ByteArray>()
            repeat(numberOfNodes) {
                val nodeCode = data.sliceArray(offset until offset + 2)
                nodeCodes.add(nodeCode)
                offset += 2
            }

            // Challenge1A (16 bytes)
            require(offset + 16 <= data.size) {
                "Data size insufficient for challenge1A at offset $offset"
            }
            val challenge1A = data.sliceArray(offset until offset + 16)

            return Authentication1AesCommand(idm, nodeCodes.toTypedArray(), challenge1A, flag)
        }
    }
}
