package com.kormax.felicatool.felica

/**
 * Search Service Code command used to acquire Area Code and Service Code from a FeliCa card
 *
 * This command allows you to discover what services and areas are available on a card. It's
 * typically used after initial card discovery to understand the card's structure.
 */
class SearchServiceCodeCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /**
     * The index to start searching from (as unsigned 16-bit integer) Use 0 to start from the
     * beginning
     */
    val index: Int = 0,
) : FelicaCommandWithIdm<SearchServiceCodeResponse>(idm) {

    init {
        require(index in 0..0xFFFF) { "Index must be between 0 and 65535" }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        SearchServiceCodeResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = MIN_LENGTH) {
            addByte(index and 0xFF)
            addByte((index shr 8) and 0xFF)
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x0A
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int = BASE_LENGTH + 2 // + index (2 bytes)
        const val MAX_ITERATOR_INDEX: Int = 0xFFFF

        fun fromByteArray(data: ByteArray): SearchServiceCodeCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val index = uByte() or (uByte() shl 8)
                require(index in 0..MAX_ITERATOR_INDEX) {
                    "Index must be between 0 and $MAX_ITERATOR_INDEX, got $index"
                }
                SearchServiceCodeCommand(idm, index)
            }
    }
}
