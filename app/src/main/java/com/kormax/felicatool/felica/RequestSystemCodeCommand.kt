package com.kormax.felicatool.felica

/**
 * Request System Code command used to acquire System Code registered to the card
 *
 * This command allows you to discover what system codes are available on a card. If a card is
 * divided into more than one System, this command acquires System Code of each System existing in
 * the card.
 */
class RequestSystemCodeCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray
) : FelicaCommandWithIdm<RequestSystemCodeResponse>(idm) {

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        RequestSystemCodeResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x0C
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int = FelicaCommandWithIdm.BASE_LENGTH // No additional parameters

        /** Parse a Request System Code command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestSystemCodeCommand {
            require(data.size >= MIN_LENGTH) { "Data must be at least $MIN_LENGTH bytes" }
            require(data[1] == COMMAND_CODE.toByte()) {
                "Invalid command code: expected $COMMAND_CODE, got ${data[1]}"
            }

            val idm = data.sliceArray(2..9)
            return RequestSystemCodeCommand(idm)
        }
    }
}
