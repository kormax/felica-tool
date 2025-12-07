package com.kormax.felicatool.felica

/**
 * Area class representing an Area in the FeliCa file system. An Area is a hierarchical grouping
 * that can contain Services and Sub-Areas.
 *
 * @property number The Area Number
 * @property attribute The Area Attribute
 * @property endNumber The End Area Number
 * @property endAttribute The End Area Attribute
 */
data class Area(
    override val number: Int,
    val attribute: AreaAttribute,
    val endNumber: Int,
    val endAttribute: AreaAttribute,
) : Node {

    init {
        require(number in 0..1023) { "Area number must be in range 0-1023 (10 bits), got: $number" }
        require(endNumber in 0..1023) {
            "End area number must be in range 0-1023 (10 bits), got: $endNumber"
        }
    }

    val isRoot: Boolean
        get() = number == 0 && endNumber == 1023

    /**
     * Returns the area code as a byte array (2 bytes, little-endian). This is the start area code.
     */
    override val code: ByteArray = startCode

    /** Returns the start area code as a byte array (2 bytes, little-endian). */
    val startCode: ByteArray
        get() {
            val areaCode = getAreaCode()
            return byteArrayOf(
                areaCode.toByte(), // Area Code LSB
                (areaCode.toInt() shr 8).toByte(), // Area Code MSB
            )
        }

    /** Returns the end area code as a byte array (2 bytes, little-endian). */
    val endCode: ByteArray
        get() {
            val endAreaCode = getEndAreaCode()
            return byteArrayOf(
                endAreaCode.toByte(), // End Area Code LSB
                (endAreaCode.toInt() shr 8).toByte(), // End Area Code MSB
            )
        }

    /**
     * Returns the full area code as a byte array (4 bytes, little-endian). Includes both area code
     * and end area code.
     */
    override val fullCode: ByteArray = startCode + endCode

    /** Returns the Area Code (2 bytes) combining number and attribute. */
    private fun getAreaCode(): Short {
        return ((number shl 6) or attribute.value).toShort()
    }

    /** Returns the End Area Code (2 bytes) combining endNumber and endAttribute. */
    private fun getEndAreaCode(): Short {
        return ((endNumber shl 6) or endAttribute.value).toShort()
    }

    override fun belongsTo(other: Node): Boolean {
        return when (other) {
            is Area -> {
                // Area belongs to other area if its range is within the other's range
                this.number >= other.number && this.endNumber <= other.endNumber
            }
            is Service -> {
                // Area cannot belong to service
                false
            }
            is System -> {
                // Area belongs to System (root)
                true
            }
            else -> false
        }
    }

    override fun toString(): String {
        return "Area(number=$number, attribute=$attribute, endNumber=$endNumber, endAttribute=$endAttribute)[${fullCode.toHexString()}]"
    }

    companion object {
        /** Area 0 covering the full range 0000-FEFF (numbers 0-1023). */
        val ROOT =
            Area(
                number = 0,
                attribute = AreaAttribute.CAN_CREATE_SUB_AREA,
                endNumber = 1023,
                endAttribute = AreaAttribute.END_ROOT_AREA,
            )

        /**
         * Creates an Area from a byte array (4 bytes, little-endian). Format: [Area Code (2
         * bytes)] + [End Area Code (2 bytes)]
         *
         * @param byteArray The 4-byte Area data
         * @return A new Area instance
         */
        fun fromByteArray(byteArray: ByteArray): Area {
            require(byteArray.size == 4) { "Area data must be exactly 4 bytes" }

            // Parse Area Code (first 2 bytes, little-endian)
            val areaCode =
                ((byteArray[1].toInt() and 0b11111111) shl 8) or
                    (byteArray[0].toInt() and 0b11111111)
            val attributeValue = areaCode and 0b111111 // Upper 6 bits
            val number = (areaCode shr 6) and 0b1111111111 // Lower 10 bits
            val attribute = AreaAttribute.fromValue(attributeValue)

            // Parse End Area Code (last 2 bytes, little-endian)
            val endAreaCode =
                ((byteArray[3].toInt() and 0b11111111) shl 8) or
                    (byteArray[2].toInt() and 0b11111111)
            val endAttributeValue = endAreaCode and 0b111111 // Upper 6 bits
            val endNumber = (endAreaCode shr 6) and 0b1111111111 // Lower 10 bits
            val endAttribute = AreaAttribute.fromValue(endAttributeValue)

            return Area(number, attribute, endNumber, endAttribute)
        }

        fun fromHexString(string: String) = fromByteArray(string.hexToByteArray())
    }
}
