package com.kormax.felicatool.felica

/**
 * Value class representing an Option Version in FeliCa protocol. An Option Version is a 2-byte
 * value stored in little-endian format, representing version in BCD notation.
 */
data class OptionVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val reserved: Int = 0b1000,
) {

    init {
        require(major in 0..15) { "Major version must be in range 0-15, got: $major" }
        require(minor in 0..15) { "Minor version must be in range 0-15, got: $minor" }
        require(patch in 0..15) { "Patch version must be in range 0-15, got: $patch" }
    }

    /** Returns the option version as an integer value (little-endian). */
    fun toInt(): Int {
        val bytes = toByteArray()
        return (bytes[1].toInt() and 0xFF) shl 8 or (bytes[0].toInt() and 0xFF)
    }

    /** Returns the option version as a hex string (little-endian byte order). */
    fun toHexString(): String {
        return toByteArray().toHexString()
    }

    /** Converts the option version to a 2-byte array in little-endian format. */
    fun toByteArray(): ByteArray {
        val value = ((major and 0x0F) or 0x80) shl 8 or (minor shl 4) or patch
        return byteArrayOf(
            (value and 0xFF).toByte(), // LSB
            ((value shr 8) and 0xFF).toByte(), // MSB
        )
    }

    override fun toString(): String {
        return "$major.$minor.$patch"
    }

    companion object {
        /**
         * Creates an OptionVersion from BCD version components.
         *
         * @param major Major version (0-15)
         * @param minor Minor version (0-15)
         * @param patch Patch version (0-15)
         * @return A new OptionVersion instance
         */
        fun fromBcd(major: Int, minor: Int, patch: Int): OptionVersion {
            return OptionVersion(major, minor, patch)
        }

        /**
         * Creates an OptionVersion from a byte array (little-endian format).
         *
         * @param bytes The 2-byte array representing the option version
         * @return A new OptionVersion instance
         */
        fun fromByteArray(bytes: ByteArray): OptionVersion {
            require(bytes.size == 2) { "Byte array must be exactly 2 bytes, got ${bytes.size}" }
            val major = (bytes[1].toInt() and 0x0F) // major (lower 4 bits only)
            val minor = (bytes[0].toInt() and 0xF0) shr 4 // minor
            val patch = (bytes[0].toInt() and 0x0F) // patch
            return OptionVersion(major, minor, patch)
        }

        /** The missing option version (0.0.0). */
        val MISSING = OptionVersion(0, 0, 0)

        /** Initial option version (0.0.0). */
        val INITIAL = MISSING
        val ZERO = MISSING
    }
}
