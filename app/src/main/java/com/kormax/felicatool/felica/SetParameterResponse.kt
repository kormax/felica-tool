package com.kormax.felicatool.felica

/**
 * Set Parameter response from FeliCa cards Contains IDM and status flags to indicate command
 * execution result
 */
class SetParameterResponse(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Status flag 1 (1 byte) */
    override val statusFlag1: Byte,

    /** Status flag 2 (1 byte) */
    override val statusFlag2: Byte,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

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

        // Status 1 (1 byte)
        data[offset++] = statusFlag1

        // Status 2 (1 byte)
        data[offset++] = statusFlag2

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x21
        const val RESPONSE_LENGTH: Int =
            BASE_LENGTH + 2 // Length + Response code + IDM(8) + statusFlag1(1) + statusFlag2(1)

        /** Parse a SetParameter response from raw bytes */
        fun fromByteArray(data: ByteArray): SetParameterResponse {
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

            // Status 1 (1 byte)
            val statusFlag1 = data[offset]
            offset++

            // Status 2 (1 byte)
            val statusFlag2 = data[offset]

            return SetParameterResponse(idm, statusFlag1, statusFlag2)
        }
    }
}
