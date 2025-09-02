package com.kormax.felicatool.felica

/**
 * Polling command used to discover and communicate with FeliCa cards This is a command without IDM
 * as it's used for discovery
 */
class PollingCommand(
    /**
     * System Code (2 bytes) - specifies which system to poll Common values:
     * - 0xFFFF: Wildcard (any system)
     * - 0x8008: Common system code for many cards
     */
    val systemCode: ByteArray = byteArrayOf(0xFF.toByte(), 0xFF.toByte()),

    /** Request Code - specifies what information to request */
    val requestCode: RequestCode = RequestCode.NO_REQUEST,

    /** Time Slot - specifies the time slot for anti-collision */
    val timeSlot: TimeSlot = TimeSlot.SLOT_1,
) : FelicaCommandWithoutIdm<PollingResponse>() {

    init {
        require(systemCode.size == 2) { "System code must be exactly 2 bytes" }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) = PollingResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // System code (2 bytes)
        data.addAll(systemCode.toList())

        // Request code (1 byte)
        data.add(requestCode.value)

        // Time slot (1 byte)
        data.add(timeSlot.value)

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x00
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val MIN_COMMAND_LENGTH: Int =
            FelicaCommandWithoutIdm.BASE_LENGTH +
                4 // + system_code(2) + request_code(1) + time_slot(1)

        /** Parse a polling command from raw bytes */
        fun fromByteArray(data: ByteArray): PollingCommand {
            require(data.size >= MIN_COMMAND_LENGTH) {
                "Command data too short: ${data.size} bytes, minimum $MIN_COMMAND_LENGTH required"
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

            // System code (2 bytes)
            val systemCode = data.sliceArray(offset until offset + 2)
            offset += 2

            // Request code (1 byte)
            val requestCodeValue = data[offset]
            val requestCode =
                RequestCode.fromValue(requestCodeValue)
                    ?: throw IllegalArgumentException("Unknown request code: $requestCodeValue")
            offset++

            // Time slot (1 byte)
            val timeSlotValue = data[offset]
            val timeSlot =
                TimeSlot.fromValue(timeSlotValue)
                    ?: throw IllegalArgumentException("Unknown time slot: $timeSlotValue")

            return PollingCommand(systemCode, requestCode, timeSlot)
        }
    }
}
