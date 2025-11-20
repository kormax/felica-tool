package com.kormax.felicatool.felica

/** Reset Mode command for FeliCa cards This command resets the Mode to Mode0 */
class ResetModeCommand(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Reserved bytes (2 bytes, must be all 0x00) */
    val reserved: ByteArray = ByteArray(2),
) : FelicaCommandWithIdm<ResetModeResponse>(idm) {

    init {
        require(reserved.size == 2) { "Reserved bytes must be exactly 2 bytes" }
        require(reserved.all { it == 0x00.toByte() }) {
            "Reserved bytes must be all 0x00, got: ${reserved.toHexString()}"
        }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) = ResetModeResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = COMMAND_LENGTH) { addBytes(reserved) }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x3E
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val COMMAND_LENGTH: Int =
            BASE_LENGTH + 2 // Length(1) + CommandCode(1) + IDM(8) + Reserved(2)

        /** Parse a ResetMode command from raw bytes */
        fun fromByteArray(data: ByteArray): ResetModeCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = COMMAND_LENGTH) { idm ->
                val reserved = bytes(2)
                require(reserved.all { it == 0x00.toByte() }) {
                    "Reserved bytes must be 0x00, got: ${reserved.toHexString()}"
                }

                ResetModeCommand(idm, reserved)
            }
    }
}
