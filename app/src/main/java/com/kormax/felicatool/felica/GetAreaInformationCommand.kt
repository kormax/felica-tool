package com.kormax.felicatool.felica

/**
 * Get Area Information command (preliminary name - no sources available)
 *
 * This command retrieves information about a specific area on the FeliCa card. The exact purpose
 * and functionality are not well documented, hence the preliminary naming.
 */
class GetAreaInformationCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** The 2-byte node code (area code) to request information for */
    val nodeCode: ByteArray,
) : FelicaCommandWithIdm<GetAreaInformationResponse>(idm) {

    init {
        require(nodeCode.size == 2) { "Node code must be exactly 2 bytes" }
    }

    /**
     * Alternative constructor that accepts a Node object and extracts its code
     *
     * @param idm The 8-byte IDM of the target card
     * @param node Node object to request area information for
     */
    constructor(idm: ByteArray, node: Node) : this(idm, node.code)

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        GetAreaInformationResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = ByteArray(COMMAND_LENGTH)
        var offset = 0

        // Length (1 byte)
        data[offset++] = COMMAND_LENGTH.toByte()

        // Command code (1 byte)
        data[offset++] = COMMAND_CODE.toByte()

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Node code (2 bytes)
        nodeCode.copyInto(data, offset)

        return data
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x24
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val COMMAND_LENGTH: Int = FelicaCommandWithIdm.BASE_LENGTH + 2 // + node_code(2)

        /** Parse a Get Area Information command from raw bytes */
        fun fromByteArray(data: ByteArray): GetAreaInformationCommand {
            require(data.size == COMMAND_LENGTH) {
                "Command data must be exactly $COMMAND_LENGTH bytes, got ${data.size}"
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

            // Node code (2 bytes)
            val nodeCode = data.sliceArray(offset until offset + 2)

            return GetAreaInformationCommand(idm, nodeCode)
        }
    }
}
