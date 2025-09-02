package com.kormax.felicatool.felica

/**
 * Reset Mode response from FeliCa cards Contains IDM and status flags indicating the result of the
 * reset operation
 */
class ResetModeResponse(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Status Flag1 (1 byte) - indicates success or failure */
    override val statusFlag1: Byte,

    /** Status Flag2 (1 byte) - detailed error information */
    override val statusFlag2: Byte,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val data = ByteArray(RESPONSE_LENGTH)
        var offset = 0

        // Length (1 byte)
        data[offset++] = RESPONSE_LENGTH.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Status Flag1 (1 byte)
        data[offset++] = statusFlag1

        // Status Flag2 (1 byte)
        data[offset++] = statusFlag2

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x3F
        const val RESPONSE_LENGTH: Int =
            BASE_LENGTH +
                2 // Length(1) + ResponseCode(1) + IDM(8) + StatusFlag1(1) + StatusFlag2(1)

        /** Parse a ResetMode response from raw bytes */
        fun fromByteArray(data: ByteArray): ResetModeResponse {
            require(data.size >= RESPONSE_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $RESPONSE_LENGTH required"
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

            // Status Flag1 (1 byte)
            val statusFlag1 = data[offset++]

            // Status Flag2 (1 byte)
            val statusFlag2 = data[offset++]

            return ResetModeResponse(idm, statusFlag1, statusFlag2)
        }
    }
}
