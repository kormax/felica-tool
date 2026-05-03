package com.kormax.felicatool.felica

/**
 * Get System Status command used to acquire the current system status from the card
 *
 * This command allows you to retrieve the current status information from a FeliCa card. The
 * command payload contains two reserved bytes that should be set to zero.
 */
class GetSystemStatusCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** Reserved bytes (2 bytes) - should be set to 0x0000 */
    val reserved: ByteArray = byteArrayOf(0x00, 0x00),
) : FelicaCommandWithIdm<GetSystemStatusResponse>(idm) {

    init {
        require(reserved.size == 2) { "Reserved bytes must be exactly 2 bytes" }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        GetSystemStatusResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = MIN_LENGTH) { addBytes(reserved) }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x38
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val MIN_LENGTH: Int = BASE_LENGTH + 2 // + reserved(2)

        /** Parse a Get System Status command from raw bytes */
        fun fromByteArray(data: ByteArray): GetSystemStatusCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                GetSystemStatusCommand(idm, bytes(2))
            }
    }
}
