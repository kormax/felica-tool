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

    override fun toByteArray(): ByteArray {
        val data = ByteArray(COMMAND_LENGTH)
        var offset = 0

        // Length (1 byte)
        data[offset++] = COMMAND_LENGTH.toByte()

        // Command code (1 byte)
        data[offset++] = COMMAND_CODE.toByte()

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Reserved bytes (2 bytes)
        reserved.copyInto(data, offset)

        return data
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x3E
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val COMMAND_LENGTH: Int =
            BASE_LENGTH + 2 // Length(1) + CommandCode(1) + IDM(8) + Reserved(2)

        /** Parse a ResetMode command from raw bytes */
        fun fromByteArray(data: ByteArray): ResetModeCommand {
            require(data.size >= COMMAND_LENGTH) {
                "Command data too short: ${data.size} bytes, minimum $COMMAND_LENGTH required"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Command code (1 byte)
            val commandCode = data[offset]
            require(commandCode == COMMAND_CODE.toByte()) {
                "Invalid command code: expected $COMMAND_CODE, got $commandCode"
            }
            offset++

            // IDM (8 bytes)
            val idm = data.sliceArray(offset until offset + 8)
            offset += 8

            // Reserved bytes (2 bytes) - validate they are all 0x00
            val reserved = data.sliceArray(offset until offset + 2)
            require(reserved.all { it == 0x00.toByte() }) {
                "Reserved bytes must be 0x00, got: ${reserved.toHexString()}"
            }

            return ResetModeCommand(idm, reserved)
        }
    }
}
