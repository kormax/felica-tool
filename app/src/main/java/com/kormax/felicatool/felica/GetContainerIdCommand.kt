package com.kormax.felicatool.felica

/**
 * Get Container ID command - retrieves the container ID without requiring IDM
 *
 * This command is used to get the container ID from mobile FeliCa cards. Unlike most FeliCa
 * commands, this command does not require an IDM.
 */
class GetContainerIdCommand(
    /** Reserved data (2 bytes) - typically set to 0x0000 */
    val reserved: ByteArray = byteArrayOf(0x00, 0x00)
) : FelicaCommandWithoutIdm<GetContainerIdResponse>() {

    init {
        require(reserved.size == 2) {
            "Reserved data must be exactly 2 bytes, got ${reserved.size}"
        }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) = GetContainerIdResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = ByteArray(COMMAND_LENGTH)
        var offset = 0

        // Length (1 byte)
        data[offset++] = COMMAND_LENGTH.toByte()

        // Command code (1 byte)
        data[offset++] = COMMAND_CODE.toByte()

        // Reserved bytes (2 bytes)
        reserved.copyInto(data, offset)

        return data
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x70
        override val COMMAND_CLASS: CommandClass = CommandClass.OTHER

        const val COMMAND_LENGTH: Int =
            FelicaCommandWithoutIdm.BASE_LENGTH + 2 // length(1) + command_code(1) + reserved(2)

        /** Parse a Get Container ID command from raw bytes */
        fun fromByteArray(data: ByteArray): GetContainerIdCommand {
            require(data.size >= COMMAND_LENGTH) { "Data must be at least $COMMAND_LENGTH bytes" }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Command code (1 byte)
            require(data[offset] == COMMAND_CODE.toByte()) {
                "Invalid command code: expected $COMMAND_CODE, got ${data[offset]}"
            }
            offset++

            // Reserved bytes (2 bytes)
            val reserved = data.sliceArray(offset until offset + 2)

            return GetContainerIdCommand(reserved)
        }
    }
}
