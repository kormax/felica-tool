package com.kormax.felicatool.felica

/**
 * Time Slot enumeration for Polling command Specifies the maximum number of time slots for
 * anti-collision
 */
enum class TimeSlot(val value: Byte, val numberOfSlots: Int, val description: String) {
    /** Single time slot - no anti-collision, high collision probability */
    SLOT_1(0x00, 1, "1 slot (#0)"),

    /** 2 time slots for anti-collision */
    SLOT_2(0x01, 2, "2 slots (#0, #1)"),

    /** 4 time slots for anti-collision */
    SLOT_4(0x03, 4, "4 slots (#0, #1, #2, #3)"),

    /** 8 time slots for anti-collision */
    SLOT_8(0x07, 8, "8 slots (#0-#7)"),

    /** 16 time slots for anti-collision */
    SLOT_16(0x0F, 16, "16 slots (#0-#15)");

    /** Get all available slot numbers for this time slot configuration */
    fun getAvailableSlots(): IntRange {
        return 0 until numberOfSlots
    }

    companion object {
        /** Get TimeSlot from byte value */
        fun fromValue(value: Byte): TimeSlot? {
            return values().find { it.value == value }
        }

        /**
         * Recommended time slot for environments with multiple cards Use SLOT_4 as a good balance
         * between collision avoidance and efficiency
         */
        val RECOMMENDED_MULTI_CARD = SLOT_4

        /** For single card environments where collision is not expected */
        val SINGLE_CARD = SLOT_1
    }
}
