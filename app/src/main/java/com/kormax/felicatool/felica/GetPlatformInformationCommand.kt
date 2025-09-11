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

    override fun toByteArray(): ByteArray {
        val data = ByteArray(COMMAND_LENGTH)
        var offset = 0

        // Length (1 byte)
        data[offset++] = COMMAND_LENGTH.toByte()

        // Command code (1 byte)
        data[offset++] = COMMAND_CODE.toByte()

        // IDM (8 bytes)
        idm.copyInto(data, offset)

        return data
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x3A
        override val COMMAND_CLASS: CommandClass = CommandClass.OTHER

        const val COMMAND_LENGTH: Int = BASE_LENGTH // Length(1) + CommandCode(1) + IDM(8)

        /** Parse a GetPlatformInformation command from raw bytes */
        fun fromByteArray(data: ByteArray): GetPlatformInformationCommand {
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

            return GetPlatformInformationCommand(idm)
        }
    }
}
