package com.kormax.felicatool.felica

/**
 * Get Platform Information command for FeliCa cards This command retrieves information about the
 * secure element
 */
class GetPlatformInformationCommand(
    /** Card IDM (8 bytes) */
    idm: ByteArray
) : FelicaCommandWithIdm<GetPlatformInformationResponse>(idm) {

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        GetPlatformInformationResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray = buildFelicaMessage(COMMAND_CODE, idm, COMMAND_LENGTH) {}

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x3A
        override val COMMAND_CLASS: CommandClass = CommandClass.OTHER

        const val COMMAND_LENGTH: Int = BASE_LENGTH // Length(1) + CommandCode(1) + IDM(8)

        /** Parse a GetPlatformInformation command from raw bytes */
        fun fromByteArray(data: ByteArray): GetPlatformInformationCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = COMMAND_LENGTH) { idm ->
                GetPlatformInformationCommand(idm)
            }
    }
}
