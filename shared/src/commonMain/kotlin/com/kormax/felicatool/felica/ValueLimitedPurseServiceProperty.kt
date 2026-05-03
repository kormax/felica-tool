package com.kormax.felicatool.felica

/**
 * Represents Value-Limited Purse Service property
 *
 * This property contains information about a Value-Limited Purse Service including the service
 * status flag, upper and lower limits, and generation number.
 */
data class ValueLimitedPurseServiceProperty(
    /** Value-Limited Purse Service flag 0x01: Enabled 0x00: Disabled */
    val enabled: Boolean,

    /**
     * Upper Limit (4 bytes, Little Endian, Two's complement) When the Value-Limited Purse Service
     * flag is 0x00, returns 0xFFFFFFFF
     */
    val upperLimit: Int,

    /**
     * Lower Limit (4 bytes, Little Endian, Two's complement) When the Value-Limited Purse Service
     * flag is 0x00, returns 0xFFFFFFFF
     */
    val lowerLimit: Int,

    /**
     * Generation Number (1 byte) When the Value-Limited Purse Service flag is 0x00, returns 0xFF
     */
    val generationNumber: Int,
) : NodeProperty {

    /** Get the size of the property in bytes */
    override val sizeBytes: Int = SIZE_BYTES

    /** Get the type of this node property */
    override val type: NodePropertyType = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE

    /** Convert to byte array (10 bytes) */
    override fun toByteArray(): ByteArray {
        val data = ByteArray(SIZE_BYTES)
        var offset = 0

        // Flag
        data[offset++] = if (enabled) 0x01 else 0x00

        // Upper Limit (Little Endian)
        data[offset++] = (upperLimit and 0xFF).toByte()
        data[offset++] = ((upperLimit shr 8) and 0xFF).toByte()
        data[offset++] = ((upperLimit shr 16) and 0xFF).toByte()
        data[offset++] = ((upperLimit shr 24) and 0xFF).toByte()

        // Lower Limit (Little Endian)
        data[offset++] = (lowerLimit and 0xFF).toByte()
        data[offset++] = ((lowerLimit shr 8) and 0xFF).toByte()
        data[offset++] = ((lowerLimit shr 16) and 0xFF).toByte()
        data[offset++] = ((lowerLimit shr 24) and 0xFF).toByte()

        // Generation Number (1 byte)
        data[offset++] = (generationNumber and 0xFF).toByte()

        return data
    }

    companion object {
        const val SIZE_BYTES =
            10 // 1 + 4 + 4 + 1 (according to spec: flag + upper + lower + generation)

        /** Parse from byte array (10 bytes expected) */
        fun fromByteArray(data: ByteArray): ValueLimitedPurseServiceProperty {
            require(data.size == SIZE_BYTES) { "Expected $SIZE_BYTES bytes, got ${data.size}" }

            val enabled = data[0] == 0x01.toByte()

            // Little Endian parsing for 4-byte integers
            val upperLimit =
                (data[1].toInt() and 0xFF) or
                    ((data[2].toInt() and 0xFF) shl 8) or
                    ((data[3].toInt() and 0xFF) shl 16) or
                    ((data[4].toInt() and 0xFF) shl 24)

            val lowerLimit =
                (data[5].toInt() and 0xFF) or
                    ((data[6].toInt() and 0xFF) shl 8) or
                    ((data[7].toInt() and 0xFF) shl 16) or
                    ((data[8].toInt() and 0xFF) shl 24)

            val generationNumber = data[9].toInt() and 0xFF // Only 1 byte for generation number

            return ValueLimitedPurseServiceProperty(
                enabled,
                upperLimit,
                lowerLimit,
                generationNumber,
            )
        }
    }
}
