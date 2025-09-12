package com.kormax.felicatool.felica

/**
 * Authentication 1 AES response received from FeliCa cards
 *
 * Contains the authentication response data from the card. The format is unknown, but the response
 * is 36 bytes long total.
 */
class Authentication1AesResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Response data (36 bytes) - format unknown */
    val data: ByteArray,
) : FelicaResponseWithIdm(idm) {

    init {
        require(data.size == 36) { "Data must be exactly 36 bytes, got ${data.size}" }
    }

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length = FelicaResponseWithIdm.BASE_LENGTH + 36 // + data(36)
        val responseData = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        responseData[offset++] = length.toByte()

        // Response code (1 byte)
        responseData[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(responseData, offset)
        offset += 8

        // Data (36 bytes)
        data.copyInto(responseData, offset)

        return responseData
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x41
        const val EXPECTED_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 36 // + data(36)

        /** Parse an Authentication 1 AES response from raw bytes */
        fun fromByteArray(data: ByteArray): Authentication1AesResponse {
            require(data.size >= EXPECTED_LENGTH) {
                "Response data too short: ${data.size} bytes, expected $EXPECTED_LENGTH"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            require(length == EXPECTED_LENGTH) {
                "Invalid response length: expected $EXPECTED_LENGTH, got $length"
            }
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

            // Data (36 bytes)
            val responseData = data.sliceArray(offset until offset + 36)

            return Authentication1AesResponse(idm, responseData)
        }
    }
}
