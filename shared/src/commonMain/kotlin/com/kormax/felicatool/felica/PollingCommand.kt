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

    /** Non-standard bytes after the regular Polling command payload. */
    trailingData: ByteArray = ByteArray(0),
) : FelicaCommandWithoutIdm<PollingResponse>(trailingData) {

    init {
        require(systemCode.size == 2) { "System code must be exactly 2 bytes" }
        requireFrameLength(MIN_COMMAND_LENGTH)
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) = PollingResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaCommandMessage(COMMAND_CODE, capacity = MIN_COMMAND_LENGTH) {
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
        const val MAX_FRAME_LENGTH: Int = FELICA_FRAME_MAX_LENGTH

        /** Parse a polling command from raw bytes */
        fun fromByteArray(data: ByteArray): PollingCommand =
            parseFelicaCommandWithoutIdm(
                data,
                COMMAND_CODE,
                minLength = MIN_COMMAND_LENGTH,
                maxLength = MAX_FRAME_LENGTH,
            ) {
                val systemCode = bytes(2)

                val requestCodeValue = byte()
                val requestCode =
                    RequestCode.fromValue(requestCodeValue)
                        ?: throw IllegalArgumentException("Unknown request code: $requestCodeValue")

                val timeSlotValue = byte()
                val timeSlot =
                    TimeSlot.fromValue(timeSlotValue)
                        ?: throw IllegalArgumentException("Unknown time slot: $timeSlotValue")

                val trailingData = bytes(remaining())

                PollingCommand(systemCode, requestCode, timeSlot, trailingData)
            }
    }
}
