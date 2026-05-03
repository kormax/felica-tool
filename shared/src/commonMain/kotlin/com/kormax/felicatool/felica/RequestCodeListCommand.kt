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

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = MIN_LENGTH) {
            addBytes(parentNodeCode)
            addByte(index and 0xFF)
            addByte((index shr 8) and 0xFF)
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x1A
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MAX_ITERATOR_INDEX = 0xFFFF

        const val MIN_LENGTH: Int = BASE_LENGTH + 2 + 2 // + parent_node_code(2) + index(2)

        /** Parse a Request Code List command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestCodeListCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val parentNodeCode = bytes(2)
                val index = uByte() or (uByte() shl 8)
                require(index in 0..MAX_ITERATOR_INDEX) {
                    "Index must be between 0 and $MAX_ITERATOR_INDEX, got $index"
                }
                RequestCodeListCommand(idm, parentNodeCode, index)
            }
    }
}
