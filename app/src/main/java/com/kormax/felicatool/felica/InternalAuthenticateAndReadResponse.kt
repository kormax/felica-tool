package com.kormax.felicatool.felica

/**
 * Internal Authenticate and Read response (0x35)
 *
 * Contains the block data read from the card along with authentication data. The authentication
 * data consists of a 16-byte challenge response followed by a 16-byte MAC.
 */
class InternalAuthenticateAndReadResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag 1 - indicates success (0x00) or error (0xFF) */
    override val statusFlag1: Byte,

    /** Status Flag 2 - provides detailed error information */
    override val statusFlag2: Byte,

    /** Array of block data read from the card. Each block is 16 bytes of raw data. */
    val blockData: Array<ByteArray>,

    /** Challenge response data (16 bytes) */
    val challenge: ByteArray,

    /** Message Authentication Code (16 bytes) */
    val mac: ByteArray,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        require(blockData.isEmpty() || blockData.all { it.size == BLOCK_SIZE }) {
            "Each block must be exactly $BLOCK_SIZE bytes"
        }
        require(challenge.isEmpty() || challenge.size == CHALLENGE_SIZE) {
            "Challenge must be exactly $CHALLENGE_SIZE bytes, got ${challenge.size}"
        }
        require(mac.isEmpty() || mac.size == MAC_SIZE) {
            "MAC must be exactly $MAC_SIZE bytes, got ${mac.size}"
        }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity =
                BASE_LENGTH +
                    2 + // Status flags
                    (if (isStatusSuccessful) 1 else 0) + // NumBlocks
                    (blockData.size * BLOCK_SIZE) +
                    challenge.size +
                    mac.size,
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            if (isStatusSuccessful) {
                addByte(blockData.size)
                blockData.forEach { addBytes(it) }
                addBytes(challenge)
                addBytes(mac)
            }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x35
        const val MIN_LENGTH = BASE_LENGTH + 2 // + status_flags(2)
        const val BLOCK_SIZE = 16 // 16 bytes per block
        const val CHALLENGE_SIZE = 16 // 16-byte challenge response
        const val MAC_SIZE = 16 // 16-byte MAC

        /** Parse an Internal Authenticate and Read response from raw bytes */
        fun fromByteArray(data: ByteArray): InternalAuthenticateAndReadResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()

                val numberOfBlocks: Int
                val blockData: Array<ByteArray>
                val mac: ByteArray

                val challenge: ByteArray

                if (statusFlag1 == 0x00.toByte()) {
                    numberOfBlocks = uByte()

                    val expectedBlockDataSize = numberOfBlocks * BLOCK_SIZE
                    val expectedTotalSize = expectedBlockDataSize + CHALLENGE_SIZE + MAC_SIZE
                    require(remaining() == expectedTotalSize) {
                        "Data length mismatch: expected $expectedTotalSize bytes (${numberOfBlocks} blocks × $BLOCK_SIZE + $CHALLENGE_SIZE challenge + $MAC_SIZE MAC), got ${remaining()} remaining bytes"
                    }

                    blockData = Array(numberOfBlocks) { bytes(BLOCK_SIZE) }
                    challenge = bytes(CHALLENGE_SIZE)
                    mac = bytes(MAC_SIZE)
                } else {
                    numberOfBlocks = 0
                    blockData = emptyArray()
                    challenge = ByteArray(0)
                    mac = ByteArray(0)
                }

                InternalAuthenticateAndReadResponse(
                    idm,
                    statusFlag1,
                    statusFlag2,
                    blockData,
                    challenge,
                    mac,
                )
            }
    }
}
