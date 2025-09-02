package com.kormax.felicatool.felica

/**
 * Request Response command used to verify the existence of a card and its Mode
 *
 * This command is used to check if a card is still present and to get its current mode. It's a
 * simple command that only requires the card's IDM and returns the card's current mode.
 */
class RequestResponseCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray
) : FelicaCommandWithIdm<RequestResponseResponse>(idm) {

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        RequestResponseResponse.fromByteArray(data)

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
        override val COMMAND_CODE: Short = 0x04
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val MIN_LENGTH: Int = FelicaCommandWithIdm.BASE_LENGTH // No additional parameters

        /** Parse a Request Response command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestResponseCommand {
            require(data.size >= MIN_LENGTH) { "Data must be at least $MIN_LENGTH bytes" }
            require(data[1] == COMMAND_CODE.toByte()) {
                "Invalid command code: expected $COMMAND_CODE, got ${data[1]}"
            }

            val idm = data.sliceArray(2..9)
            return RequestResponseCommand(idm)
        }
    }
}
