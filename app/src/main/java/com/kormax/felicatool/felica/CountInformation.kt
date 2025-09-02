package com.kormax.felicatool.felica

/**
 * Value class representing count information in FeliCa protocol. Count information is a 2-byte
 * value stored as a short integer.
 *
 * @property value The short integer representing the count
 */
@JvmInline
value class CountInformation(val value: Short) {

    /**
     * Alternative constructor that creates a CountInformation from a byte array (little-endian).
     *
     * @param bytes The 2-byte array representing the count in little-endian format
     */
    constructor(
        byteArray: ByteArray
    ) : this(
        byteArray
            .also {
                require(it.size == 2) { "CountInformation must be exactly 2 bytes, got ${it.size}" }
            }
            .let { ((it[1].toInt() and 0xFF) shl 8 or (it[0].toInt() and 0xFF)).toShort() }
    )

    /** Returns the count as an integer value. */
    fun toInt(): Int = value.toInt() and 0xFFFF

    /** Returns the count as a hex string (little-endian byte order). */
    fun toHexString(): String {
        val lsb = (value.toInt() and 0xFF).toByte()
        val msb = ((value.toInt() shr 8) and 0xFF).toByte()
        return "%02x%02x".format(lsb, msb)
    }

    /** Returns the count as a byte array in little-endian format. */
    fun toByteArray(): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(), // LSB
            ((value.toInt() shr 8) and 0xFF).toByte(), // MSB
        )
    }

    /** Returns true if this count represents a zero count. */
    val isZero: Boolean
        get() = value == 0.toShort()

    /** Returns true if this count is invalid (negative or 0xFFFF). */
    val isInvalid: Boolean
        get() = value < 0 || value == INVALID.value

    companion object {
        /**
         * Creates a CountInformation from an integer value.
         *
         * @param value The integer value (0-65535)
         * @return A new CountInformation instance
         */
        fun fromInt(value: Int): CountInformation {
            require(value in 0..65535) {
                "CountInformation value must be in range 0-65535, got: $value"
            }
            return CountInformation(value.toShort())
        }

        /**
         * Creates a CountInformation from a byte array (little-endian).
         *
         * @param bytes The 2-byte array representing the count
         * @return A new CountInformation instance
         */
        fun fromByteArray(bytes: ByteArray): CountInformation = CountInformation(bytes)

        /** Zero count information. */
        val ZERO = CountInformation(0x0000.toShort())

        /** Invalid count information (0xFFFF). */
        val INVALID = CountInformation(0xFFFF.toShort())
    }
}
