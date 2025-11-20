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

    override fun toByteArray(): ByteArray = buildFelicaMessage(COMMAND_CODE, idm) {}

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x0C
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int = BASE_LENGTH // No additional parameters

        /** Parse a Request System Code command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestSystemCodeCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                RequestSystemCodeCommand(idm)
            }
    }
}
