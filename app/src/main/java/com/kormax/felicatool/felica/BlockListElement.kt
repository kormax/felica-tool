package com.kormax.felicatool.felica

/**
 * Represents a block list element for FeliCa commands Contains the service code list order, access
 * mode, and block number
 *
 * Format according to FeliCa specification:
 * - 2-byte format: D0 (Length + Access Mode + Service Code List Order) + D1 (Block Number)
 * - 3-byte format: D0 (Length + Access Mode + Service Code List Order) + D1-D2 (Block Number)
 */
data class BlockListElement(
    /** Service code list order (0-15, where 0 is the first service in the list) */
    val serviceCodeListOrder: Int,

    /** The block number */
    val blockNumber: Int,

    /** The access mode for this block */
    val accessMode: AccessMode = AccessMode.NORMAL,

    /** Whether this is an extended block list element (3 bytes vs 2 bytes) */
    val extended: Boolean = false,
) {
    init {
        require(serviceCodeListOrder in 0..15) {
            "Service code list order must be between 0 and 15"
        }
        require(blockNumber >= 0) { "Block number must be non-negative" }
        if (!extended) {
            require(blockNumber <= 0xFF) { "Block number must fit in 1 byte for normal format" }
        } else {
            require(blockNumber <= 0xFFFF) {
                "Block number must fit in 2 bytes for extended format"
            }
        }
    }

    /** Access mode for block list elements */
    enum class AccessMode(val value: Int) {
        /** Normal read/write access */
        NORMAL(0),

        /** Cashback access to Purse Service */
        CASHBACK(1),
    }

    /** Converts to byte array representation */
    fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // D0: Length (bit 7) + Access Mode (bits 6-4) + Service Code List Order (bits 3-0)
        val d0 =
            ((if (extended) 0 else 1) shl 7) or // Length bit
                ((accessMode.value) shl 4) or // Access Mode
                serviceCodeListOrder // Service Code List Order
        data.add(d0.toByte())

        // Block Number (Little Endian)
        data.add((blockNumber and 0xFF).toByte()) // D1
        if (extended) {
            data.add(((blockNumber shr 8) and 0xFF).toByte()) // D2
        }

        return data.toByteArray()
    }

    companion object {
        /** Creates a BlockListElement from byte array, parsing according to FeliCa specification */
        fun fromByteArray(data: ByteArray): BlockListElement {
            require(data.size == 2 || data.size == 3) { "Block list element must be 2 or 3 bytes" }

            val d0 = data[0].toInt() and 0xFF

            // Parse D0: Length (bit 7) + Access Mode (bits 6-4) + Service Code List Order (bits
            // 3-0)
            val extended = (d0 and 0x80) == 0 // Length bit: 1 = 2-byte, 0 = 3-byte
            val accessModeValue = (d0 and 0x70) shr 4 // Access Mode bits 6-4
            val serviceCodeListOrder = d0 and 0x0F // Service Code List Order bits 3-0

            val accessMode =
                when (accessModeValue) {
                    0 -> AccessMode.NORMAL
                    1 -> AccessMode.CASHBACK
                    else -> throw IllegalArgumentException("Invalid access mode: $accessModeValue")
                }

            // Parse block number (Little Endian)
            val blockNumber =
                if (extended) {
                    require(data.size == 3) {
                        "Extended block list element must be exactly 3 bytes"
                    }
                    (data[2].toInt() and 0xFF) shl 8 or (data[1].toInt() and 0xFF)
                } else {
                    require(data.size == 2) { "Normal block list element must be exactly 2 bytes" }
                    data[1].toInt() and 0xFF // Only D1 contains block number for 2-byte format
                }

            return BlockListElement(
                serviceCodeListOrder = serviceCodeListOrder,
                accessMode = accessMode,
                blockNumber = blockNumber,
                extended = extended,
            )
        }
    }

    override fun toString(): String {
        return "BlockListElement(serviceCodeListOrder=$serviceCodeListOrder, blockNumber=$blockNumber, accessMode=$accessMode, extended=$extended)"
    }
}
