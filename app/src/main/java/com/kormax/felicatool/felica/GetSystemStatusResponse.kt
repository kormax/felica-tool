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

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length = FelicaResponseWithIdm.BASE_LENGTH + 1 + 1 + 1 + 1 + data.size
        val dataArray = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        dataArray[offset++] = length.toByte()

        // Response code (1 byte)
        dataArray[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(dataArray, offset)
        offset += 8

        // Status Flag 1 (1 byte)
        dataArray[offset++] = statusFlag1

        // Status Flag 2 (1 byte)
        dataArray[offset++] = statusFlag2

        // Flag (1 byte)
        dataArray[offset++] = flag

        // Data Length (1 byte)
        dataArray[offset++] = data.size.toByte()

        // Data (variable length)
        data.copyInto(dataArray, offset)

        return dataArray
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x39
        const val MIN_LENGTH =
            FelicaResponseWithIdm.BASE_LENGTH +
                1 +
                1 +
                1 +
                1 // + status1(1) + status2(1) + flag(1) + dataLength(1)

        /** Parse a Get System Status response from raw bytes */
        fun fromByteArray(data: ByteArray): GetSystemStatusResponse {
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

            // Flag (1 byte)
            val flag = data[offset]
            offset++

            // Data Length (1 byte)
            val dataLength = data[offset]
            offset++

            // Data (variable length)
            val responseData =
                if (dataLength > 0) {
                    require(offset + dataLength <= data.size) {
                        "Insufficient data for response data: expected $dataLength bytes, but only ${data.size - offset} available"
                    }
                    data.sliceArray(offset until offset + dataLength)
                } else {
                    byteArrayOf()
                }

            // Verify we consumed all expected data
            require(offset + dataLength == data.size) {
                "Data parsing incomplete: consumed ${offset + dataLength} bytes out of ${data.size}"
            }

            return GetSystemStatusResponse(idm, statusFlag1, statusFlag2, flag, responseData)
        }
    }
}
