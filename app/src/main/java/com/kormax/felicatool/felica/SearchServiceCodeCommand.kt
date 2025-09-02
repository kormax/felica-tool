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

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Index (2 bytes, little endian)
        val indexBytes = byteArrayOf((index and 0xFF).toByte(), ((index shr 8) and 0xFF).toByte())
        data.addAll(indexBytes.toList())

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x0A
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int = FelicaCommandWithIdm.BASE_LENGTH + 2 // + index (2 bytes)
        const val MAX_ITERATOR_INDEX: Int = 0xFFFF

        fun fromByteArray(data: ByteArray): SearchServiceCodeCommand {
            require(data.size >= MIN_LENGTH) { "Data must be at least $MIN_LENGTH bytes" }
            require(data[1] == COMMAND_CODE.toByte()) { "Invalid command code" }
            val idm = data.sliceArray(2..9)
            val indexBytes = data.sliceArray(10..11)
            val index = ((indexBytes[1].toInt() and 0xFF) shl 8) or (indexBytes[0].toInt() and 0xFF)
            return SearchServiceCodeCommand(idm, index)
        }
    }
}
