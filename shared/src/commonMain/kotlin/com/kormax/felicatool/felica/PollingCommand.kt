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

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, capacity = MIN_COMMAND_LENGTH) {
            addBytes(systemCode)
            addByte(requestCode.value)
            addByte(timeSlot.value)
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x00
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val MIN_COMMAND_LENGTH: Int =
            FelicaCommandWithoutIdm.BASE_LENGTH +
                4 // + system_code(2) + request_code(1) + time_slot(1)

        /** Parse a polling command from raw bytes */
        fun fromByteArray(data: ByteArray): PollingCommand =
            parseFelicaCommandWithoutIdm(data, COMMAND_CODE, minLength = MIN_COMMAND_LENGTH) {
                val systemCode = bytes(2)

                val requestCodeValue = byte()
                val requestCode =
                    RequestCode.fromValue(requestCodeValue)
                        ?: throw IllegalArgumentException("Unknown request code: $requestCodeValue")

                val timeSlotValue = byte()
                val timeSlot =
                    TimeSlot.fromValue(timeSlotValue)
                        ?: throw IllegalArgumentException("Unknown time slot: $timeSlotValue")

                PollingCommand(systemCode, requestCode, timeSlot)
            }
    }
}
