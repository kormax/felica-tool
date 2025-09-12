package com.kormax.felicatool.felica

/**
 * Authentication 1 DES response received from FeliCa cards
 *
 * Contains the authentication response data including challenge1B and challenge2A that are returned
 * after sending the initial authentication request.
 */
class Authentication1DesResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Challenge1B (8 bytes) - response challenge from the card */
    val challenge1B: ByteArray,

    /** Challenge2A (8 bytes) - second challenge for authentication */
    val challenge2A: ByteArray,
) : FelicaResponseWithIdm(idm) {

    init {
        require(challenge1B.size == 8) {
            "Challenge1B must be exactly 8 bytes, got ${challenge1B.size}"
        }
        require(challenge2A.size == 8) {
            "Challenge2A must be exactly 8 bytes, got ${challenge2A.size}"
        }
    }

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length = FelicaResponseWithIdm.BASE_LENGTH + 16 // + challenge1B(8) + challenge2A(8)
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Challenge1B (8 bytes)
        challenge1B.copyInto(data, offset)
        offset += 8

        // Challenge2A (8 bytes)
        challenge2A.copyInto(data, offset)

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x11
        const val EXPECTED_LENGTH =
            FelicaResponseWithIdm.BASE_LENGTH + 16 // + challenge1B(8) + challenge2A(8)

        /** Parse an Authentication 1 DES response from raw bytes */
        fun fromByteArray(data: ByteArray): Authentication1DesResponse {
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

            // Challenge1B (8 bytes)
            val challenge1B = data.sliceArray(offset until offset + 8)
            offset += 8

            // Challenge2A (8 bytes)
            val challenge2A = data.sliceArray(offset until offset + 8)

            return Authentication1DesResponse(idm, challenge1B, challenge2A)
        }
    }
}
