package com.kormax.felicatool.felica

/**
 * Request Specification Version command used to acquire the version of card OS
 *
 * This command requests the card to return its OS version information including basic version and
 * various option versions.
 */
class RequestSpecificationVersionCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** Reserved field (2 bytes, should be 0000h) */
    val reserved: ByteArray = byteArrayOf(0x00, 0x00),
    trailingData: ByteArray = ByteArray(0),
) : FelicaCommandWithIdm<RequestSpecificationVersionResponse>(idm, trailingData) {

    init {
        require(reserved.size == 2) { "Reserved field must be exactly 2 bytes" }
        require(reserved.contentEquals(byteArrayOf(0x00, 0x00))) { "Reserved field must be 0000h" }
        requireFrameLength(LENGTH)
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        RequestSpecificationVersionResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaCommandMessage(COMMAND_CODE, idm, capacity = LENGTH) { addBytes(reserved) }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x3C
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val LENGTH: Int = BASE_LENGTH + 2 // + reserved(2)

        /** Parse a Request Specification Version command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestSpecificationVersionCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = LENGTH) { idm ->
                RequestSpecificationVersionCommand(idm, bytes(2), bytes(remaining()))
            }
    }
}
