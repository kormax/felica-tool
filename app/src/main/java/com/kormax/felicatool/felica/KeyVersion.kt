package com.kormax.felicatool.felica

/**
 * Value class representing a Key Version in FeliCa protocol. A Key Version is a 2-byte value stored
 * as a short integer.
 *
 * @property value The short integer representing the key version
 */
@JvmInline
value class KeyVersion(val value: Short) {

    /**
     * Alternative constructor that creates a KeyVersion from a byte array (little-endian).
     *
     * @param bytes The 2-byte array representing the key version in little-endian format
     */
    constructor(
        byteArray: ByteArray
    ) : this(
        byteArray
            .also { require(it.size == 2) { "KeyVersion must be exactly 2 bytes, got ${it.size}" } }
            .let { ((it[1].toInt() and 0xFF) shl 8 or (it[0].toInt() and 0xFF)).toShort() }
    )

    /** Returns the key version as an integer value. */
    fun toInt(): Int = value.toInt() and 0xFFFF

    /** Returns the key version as a hex string (little-endian byte order). */
    fun toHexString(): String {
        val lsb = (value.toInt() and 0xFF).toByte()
        val msb = ((value.toInt() shr 8) and 0xFF).toByte()
        return "%02x%02x".format(lsb, msb)
    }

    /** Returns the key version as a byte array in little-endian format. */
    fun toByteArray(): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(), // LSB
            ((value.toInt() shr 8) and 0xFF).toByte(), // MSB
        )
    }

    /** Returns true if this key version represents a missing/non-existent key (0xFFFF). */
    val isMissing: Boolean
        get() = value == MISSING.value

    companion object {
        /**
         * Creates a KeyVersion from an integer value.
         *
         * @param value The integer value (0-65535)
         * @return A new KeyVersion instance
         */
        fun fromInt(value: Int): KeyVersion {
            require(value in 0..65535) { "KeyVersion value must be in range 0-65535, got: $value" }
            return KeyVersion(value.toShort())
        }

        /**
         * Creates a KeyVersion from a byte array (little-endian).
         *
         * @param bytes The 2-byte array representing the key version
         * @return A new KeyVersion instance
         */
        fun fromByteArray(bytes: ByteArray): KeyVersion = KeyVersion(bytes)

        /** The missing key version (0xFFFF). */
        val MISSING = KeyVersion(0xFFFF.toShort())

        /** Initial key version (0x0000). */
        val INITIAL = KeyVersion(0x0000.toShort())
        val ZERO = INITIAL
    }
}
