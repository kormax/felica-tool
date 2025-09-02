package com.kormax.felicatool.felica

/**
 * Request Response response received from FeliCa cards
 *
 * Contains the card's IDM and current mode information. This response verifies the card's existence
 * and provides its current operational mode.
 */
class RequestResponseResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Current mode of the card */
    val mode: CardMode,
) : FelicaResponseWithIdm(idm) {

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length = MIN_LENGTH
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Mode (1 byte)
        data[offset] = mode.value.toByte()

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x05
        const val MIN_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 1 // + Mode(1)

        /** Parse a Request Response response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestResponseResponse {
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

            // Mode (1 byte)
            val modeValue = data[offset].toInt() and 0xFF
            val mode =
                when (modeValue) {
                    0 -> CardMode.INITIAL
                    1 -> CardMode.AUTHENTICATION_PENDING
                    2 -> CardMode.AUTHENTICATED
                    3 -> CardMode.ISSUANCE
                    else -> throw IllegalArgumentException("Unknown mode value: $modeValue")
                }

            return RequestResponseResponse(idm, mode)
        }
    }
}
