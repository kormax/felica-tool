package com.kormax.felicatool.felica

/**
 * Request Code List command used to acquire a comprehensive list of all codes from a FeliCa card
 *
 * This command allows you to discover all systems, areas, and services available on a card in a
 * single request, providing a complete overview of the card's structure.
 */
class RequestCodeListCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** The parent node code (2 bytes) to request codes for */
    val parentNodeCode: ByteArray,

    /** The index to start from (as unsigned 16-bit integer) Use 0 to start from the beginning */
    val index: Int,
) : FelicaCommandWithIdm<RequestCodeListResponse>(idm) {

    init {
        require(parentNodeCode.size == 2) { "Parent node code must be exactly 2 bytes" }
        require(index in 0..0xFFFF) { "Index must be between 0 and 65535" }
    }

    /** Alternative constructor that accepts a Node object */
    constructor(idm: ByteArray, parentNode: Node, index: Int) : this(idm, parentNode.code, index)

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        RequestCodeListResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Parent node code (2 bytes)
        data.addAll(parentNodeCode.toList())

        // Index (2 bytes, little endian)
        val indexBytes = byteArrayOf((index and 0xFF).toByte(), ((index shr 8) and 0xFF).toByte())
        data.addAll(indexBytes.toList())

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x1A
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MAX_ITERATOR_INDEX = 0xFFFF

        const val MIN_LENGTH: Int =
            FelicaCommandWithIdm.BASE_LENGTH + 2 + 2 // + parent_node_code(2) + index(2)

        /** Parse a Request Code List command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestCodeListCommand {
            require(data.size >= MIN_LENGTH) { "Data must be at least $MIN_LENGTH bytes" }
            require(data[1] == COMMAND_CODE.toByte()) {
                "Invalid command code: expected $COMMAND_CODE, got ${data[1]}"
            }

            val idm = data.sliceArray(2..9)
            val parentNodeCode = data.sliceArray(10..11)
            val index = ((data[12].toInt() and 0xFF) or ((data[13].toInt() and 0xFF) shl 8))

            return RequestCodeListCommand(idm, parentNodeCode, index)
        }
    }
}
