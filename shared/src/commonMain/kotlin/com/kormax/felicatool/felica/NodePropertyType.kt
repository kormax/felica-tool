package com.kormax.felicatool.felica

/** Represents the type of node property that can be queried with Get Node Property command */
enum class NodePropertyType(val value: Int) {
    /**
     * Value-Limited Purse Service property
     *
     * Returns property information for Value-Limited Purse Service including:
     * - Value-Limited Purse Service flag (1 byte)
     * - Upper Limit (4 bytes, Little Endian, Two's complement)
     * - Lower Limit (4 bytes, Little Endian, Two's complement)
     * - Generation Number (4 bytes, Little Endian)
     */
    VALUE_LIMITED_PURSE_SERVICE(0x00),

    /**
     * Communication-with-MAC-enabled Service property
     *
     * Returns property information for Communication-with-MAC-enabled Service including:
     * - Communication with MAC flag (1 byte)
     */
    MAC_COMMUNICATION(0x01);

    companion object {
        /** Find NodePropertyType by its value */
        fun fromValue(value: Int): NodePropertyType? {
            return values().find { it.value == value }
        }
    }
}
