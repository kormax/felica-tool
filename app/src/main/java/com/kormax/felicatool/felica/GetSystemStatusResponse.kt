package com.kormax.felicatool.felica

/**
 * Get System Status response received from FeliCa cards
 *
 * Contains the card's IDM and system status information including status flags, a flag byte, and
 * variable-length data.
 */
class GetSystemStatusResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag 1 from the response */
    override val statusFlag1: Byte,

    /** Status Flag 2 from the response */
    override val statusFlag2: Byte,

    /** Flag byte from the response */
    val flag: Byte,

    /** Variable-length data from the response */
    val data: ByteArray,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        require(data.size <= 255) { "Data size must be at most 255 bytes for 1-byte length field" }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = BASE_LENGTH + 4 + data.size) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            addByte(flag)
            addByte(data.size)
            addBytes(data)
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x39
        const val MIN_LENGTH =
            BASE_LENGTH + 1 + 1 + 1 + 1 // + status1(1) + status2(1) + flag(1) + dataLength(1)

        /** Parse a Get System Status response from raw bytes */
        fun fromByteArray(data: ByteArray): GetSystemStatusResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()
                val flag = byte()
                val dataLength = uByte()

                require(remaining() >= dataLength) {
                    "Insufficient data for response data: expected $dataLength bytes, but only ${remaining()} available"
                }

                val responseData = if (dataLength > 0) bytes(dataLength) else byteArrayOf()

                GetSystemStatusResponse(idm, statusFlag1, statusFlag2, flag, responseData)
            }
    }
}
