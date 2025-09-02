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
) : FelicaCommandWithIdm<RequestSpecificationVersionResponse>(idm) {

    init {
        require(reserved.size == 2) { "Reserved field must be exactly 2 bytes" }
        require(reserved.contentEquals(byteArrayOf(0x00, 0x00))) { "Reserved field must be 0000h" }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        RequestSpecificationVersionResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = ByteArray(LENGTH)

        // Length (1 byte)
        data[0] = LENGTH.toByte()

        // Command code (1 byte)
        data[1] = COMMAND_CODE.toByte()

        // IDM (8 bytes)
        idm.copyInto(data, 2)

        // Reserved (2 bytes)
        reserved.copyInto(data, 10)

        return data
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x3C
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val LENGTH: Int = FelicaCommandWithIdm.BASE_LENGTH + 2 // + reserved(2)

        /** Parse a Request Specification Version command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestSpecificationVersionCommand {
            require(data.size == LENGTH) {
                "Command data must be exactly $LENGTH bytes, got ${data.size}"
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

            // Reserved (2 bytes)
            val reserved = data.sliceArray(offset until offset + 2)

            return RequestSpecificationVersionCommand(idm, reserved)
        }
    }
}
