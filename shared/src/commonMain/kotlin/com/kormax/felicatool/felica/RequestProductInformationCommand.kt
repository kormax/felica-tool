package com.kormax.felicatool.felica

/** Request Product Information command for FeliCa cards. */
class RequestProductInformationCommand(
    /** Card IDM (8 bytes) */
    idm: ByteArray
) : FelicaCommandWithIdm<RequestProductInformationResponse>(idm) {

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        RequestProductInformationResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray = buildFelicaMessage(COMMAND_CODE, idm, COMMAND_LENGTH) {}

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x3A
        override val COMMAND_CLASS: CommandClass = CommandClass.OTHER

        const val COMMAND_LENGTH: Int = BASE_LENGTH // Length(1) + CommandCode(1) + IDM(8)

        /** Parse a RequestProductInformation command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestProductInformationCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = COMMAND_LENGTH) { idm ->
                RequestProductInformationCommand(idm)
            }
    }
}
