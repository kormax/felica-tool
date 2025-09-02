package com.kormax.felicatool.felica

/**
 * Read Without Encryption response received from FeliCa cards
 *
 * Contains the block data read from the card. Each block is 16 bytes of raw data.
 */
class ReadWithoutEncryptionResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag 1 - indicates success or error type */
    override val statusFlag1: Byte,

    /** Status Flag 2 - provides detailed error information */
    override val statusFlag2: Byte,

    /** Array of block data read from the card Each block is 16 bytes of raw data. */
    val blockData: Array<ByteArray>,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        require(blockData.isEmpty() || blockData.all { it.size == 16 }) {
            "Each block must be exactly 16 bytes"
        }
    }

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length =
            FelicaResponseWithIdm.BASE_LENGTH +
                2 +
                (if (isStatusSuccessful) 1 else 0) +
                blockData.size * 16
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Status Flag 1 (1 byte)
        data[offset++] = statusFlag1

        // Status Flag 2 (1 byte)
        data[offset++] = statusFlag2

        // Number of blocks (1 byte, only if successful)
        if (isStatusSuccessful) {
            data[offset++] = blockData.size.toByte()
        }

        // Block data (16 bytes per block)
        blockData.forEach { block ->
            block.copyInto(data, offset)
            offset += 16
        }

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x07
        const val MIN_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 2 // + status_flags(2)

        /** Parse a Read Without Encryption response from raw bytes */
        fun fromByteArray(data: ByteArray): ReadWithoutEncryptionResponse {
            require(data.size >= MIN_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_LENGTH required"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Response code (1 byte)
            val responseCode = data[offset]
            require(responseCode == RESPONSE_CODE) {
                "Invalid response code: expected $RESPONSE_CODE, got $responseCode"
            }
            offset++

            // IDM (8 bytes)
            val idm = data.sliceArray(offset until offset + 8)
            offset += 8

            // Status Flag 1 (1 byte)
            val statusFlag1 = data[offset]
            offset++

            // Status Flag 2 (1 byte)
            val statusFlag2 = data[offset]
            offset++

            // If successful, read number of blocks
            val numberOfBlocks: Int
            if (statusFlag1 == 0x00.toByte()) {
                numberOfBlocks = data[offset].toInt() and 0xFF
                offset++
            } else {
                numberOfBlocks = 0
            }

            // Block data (16 bytes per block)
            val remainingBytes = data.size - offset
            require(remainingBytes == numberOfBlocks * 16) {
                "Block data length mismatch: expected ${numberOfBlocks * 16} bytes, got $remainingBytes remaining bytes"
            }

            val blockData = mutableListOf<ByteArray>()
            repeat(numberOfBlocks) {
                val block = data.sliceArray(offset until offset + 16)
                blockData.add(block)
                offset += 16
            }

            return ReadWithoutEncryptionResponse(
                idm,
                statusFlag1,
                statusFlag2,
                blockData.toTypedArray(),
            )
        }
    }
}
