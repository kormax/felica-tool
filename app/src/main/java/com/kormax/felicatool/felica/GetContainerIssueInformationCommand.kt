package com.kormax.felicatool.felica

/**
 * Get Container Issue Information command for FeliCa cards This command retrieves
 * container-specific information including format version and mobile phone model
 */
class GetContainerIssueInformationCommand(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Reserved bytes (2 bytes, must be all 0x00) */
    val reserved: ByteArray = ByteArray(2),
) : FelicaCommandWithIdm<GetContainerIssueInformationResponse>(idm) {

    init {
        require(reserved.size == 2) { "Reserved bytes must be exactly 2 bytes" }
        require(reserved.all { it == 0x00.toByte() }) {
            "Reserved bytes must be all 0x00, got: ${reserved.toHexString()}"
        }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        GetContainerIssueInformationResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = COMMAND_LENGTH) { addBytes(reserved) }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x22
        override val COMMAND_CLASS: CommandClass = CommandClass.OTHER

        const val COMMAND_LENGTH: Int =
            BASE_LENGTH + 2 // Length(1) + CommandCode(1) + IDM(8) + Reserved(2)

        /** Parse a GetContainerIssueInformation command from raw bytes */
        fun fromByteArray(data: ByteArray): GetContainerIssueInformationCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = COMMAND_LENGTH) { idm ->
                val reserved = bytes(2)
                require(reserved.all { it == 0x00.toByte() }) {
                    "Reserved bytes must be 0x00, got: ${reserved.toHexString()}"
                }

                GetContainerIssueInformationCommand(idm, reserved)
            }
    }
}
