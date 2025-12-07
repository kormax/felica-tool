package com.kormax.felicatool.felica

/**
 * Service class representing a Service in the FeliCa file system. A Service is a group of Blocks
 * that provides access control to data.
 *
 * @property number The Service Number (upper 10 bits of Service Code)
 * @property attribute The Service Attribute (lower 6 bits of Service Code)
 */
data class Service(override val number: Int, val attribute: ServiceAttribute) : Node {

    init {
        require(number in 0..1023) {
            "Service number must be in range 0-1023 (10 bits), got: $number"
        }
    }

    /** Returns the service code as a byte array (2 bytes, big-endian). */
    override val code: ByteArray
        get() {
            val serviceCode = getServiceCode()
            return byteArrayOf(serviceCode.toByte(), (serviceCode.toInt() shr 8).toByte())
        }

    /** Returns the Service Code (2 bytes) combining number and attribute. */
    fun getServiceCode(): Short {
        return ((number shl 6) or attribute.value).toShort()
    }

    override fun belongsTo(other: Node): Boolean {
        return when (other) {
            is Area -> {
                // Service belongs to area if service number is within area's range
                this.number >= other.number && this.number <= other.endNumber
            }
            is Service -> {
                // Service cannot belong to another service
                false
            }
            is System -> {
                // Service belongs to System (root)
                true
            }
            else -> false
        }
    }

    override fun toString(): String {
        return "Service(number=$number, attribute=$attribute)"
    }

    companion object {
        /**
         * Creates a Service from a byte array (2 bytes, big-endian).
         *
         * @param byteArray The 2-byte Service Code
         * @return A new Service instance
         */
        fun fromByteArray(byteArray: ByteArray): Service {
            require(byteArray.size == 2) { "Service Code must be exactly 2 bytes" }
            val serviceCode =
                ((byteArray[1].toInt() and 0b11111111) shl 8) or
                    (byteArray[0].toInt() and 0b11111111)
            val attrValue = serviceCode and 0b111111
            val attribute = ServiceAttribute.fromValue(attrValue)
            return Service(
                number = (serviceCode shr 6) and 0b1111111111, // Upper 10 bits
                attribute = attribute,
            )
        }

        fun fromHexString(string: String) = fromByteArray(string.hexToByteArray())
    }
}
