package com.kormax.felicatool.felica

/**
 * Request Code enumeration for Polling command Specifies what information to request from the card
 */
enum class RequestCode(val value: Byte, val description: String) {
    /** No request - only basic IDm and PMm are returned */
    NO_REQUEST(0x00, "No request"),

    /** System Code request - returns the System Code of the acquired System */
    SYSTEM_CODE_REQUEST(0x01, "System Code request"),

    /** Communication performance request - returns communication capabilities */
    COMMUNICATION_PERFORMANCE_REQUEST(0x02, "Communication performance request");

    companion object {
        /** Get RequestCode from byte value */
        fun fromValue(value: Byte): RequestCode? {
            return values().find { it.value == value }
        }
    }
}
