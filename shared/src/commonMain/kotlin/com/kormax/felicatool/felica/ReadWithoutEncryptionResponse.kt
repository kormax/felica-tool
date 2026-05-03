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

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity = BASE_LENGTH + 2 + (if (isStatusSuccessful) 1 else 0) + (blockData.size * 16),
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            if (isStatusSuccessful) {
                addByte(blockData.size)
            }
            blockData.forEach { addBytes(it) }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x07
        const val MIN_LENGTH = BASE_LENGTH + 2 // + status_flags(2)

        /** Parse a Read Without Encryption response from raw bytes */
        fun fromByteArray(data: ByteArray): ReadWithoutEncryptionResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()

                val numberOfBlocks =
                    if (statusFlag1 == 0x00.toByte()) {
                        uByte()
                    } else 0

                require(remaining() == numberOfBlocks * 16) {
                    "Block data length mismatch: expected ${numberOfBlocks * 16} bytes, got ${remaining()} remaining bytes"
                }

                val blockData = Array(numberOfBlocks) { bytes(16) }

                ReadWithoutEncryptionResponse(idm, statusFlag1, statusFlag2, blockData)
            }
    }
}
