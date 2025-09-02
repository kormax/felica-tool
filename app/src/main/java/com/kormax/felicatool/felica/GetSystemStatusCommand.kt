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

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Reserved bytes (2 bytes)
        data.addAll(reserved.toList())

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x38
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val MIN_LENGTH: Int = FelicaCommandWithIdm.BASE_LENGTH + 2 // + reserved(2)

        /** Parse a Get System Status command from raw bytes */
        fun fromByteArray(data: ByteArray): GetSystemStatusCommand {
            require(data.size >= MIN_LENGTH) { "Data must be at least $MIN_LENGTH bytes" }

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

            // Reserved bytes (2 bytes)
            val reserved = data.sliceArray(offset until offset + 2)

            return GetSystemStatusCommand(idm, reserved)
        }
    }
}
