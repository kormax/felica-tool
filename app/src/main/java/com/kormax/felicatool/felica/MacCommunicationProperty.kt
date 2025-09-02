package com.kormax.felicatool.felica

/**
 * Represents Communication-with-MAC-enabled Service property
 *
 * This property contains information about whether a service has Communication with MAC enabled or
 * disabled.
 */
data class MacCommunicationProperty(
    /** Communication with MAC flag 0x01: Enabled 0x00: Disabled */
    val enabled: Boolean
) : NodeProperty {
    /** Convert to byte array (1 byte) */
    override fun toByteArray(): ByteArray {
        return byteArrayOf(if (enabled) 0x01 else 0x00)
    }

    /** Get the size of the property in bytes */
    override val sizeBytes: Int = SIZE_BYTES

    /** Get the type of this node property */
    override val type: NodePropertyType = NodePropertyType.MAC_COMMUNICATION

    companion object {
        const val SIZE_BYTES = 1

        /** Parse from byte array (1 byte expected) */
        fun fromByteArray(data: ByteArray): MacCommunicationProperty {
            require(data.size == SIZE_BYTES) { "Expected $SIZE_BYTES bytes, got ${data.size}" }

            val enabled = data[0] == 0x01.toByte()
            return MacCommunicationProperty(enabled)
        }
    }
}
