package com.kormax.felicatool.felica

/**
 * Get Area Information response received from FeliCa cards
 *
 * Contains the card's IDM and area information including status flags, node code, and unknown data.
 * Status flags indicate different conditions:
 * - FF E0 for area 0
 * - FF E7 if two higher bits of first byte of the code are high
 * - FF E2 for codes that don't represent areas
 */
class GetAreaInformationResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag1 (see section 4.5 "Status Flag") */
    override val statusFlag1: Byte,

    /** Status Flag2 (see section 4.5 "Status Flag") */
    override val statusFlag2: Byte,

    /** The 2-byte node code (only present if status is success) */
    val nodeCode: ByteArray = ByteArray(0),

    /** Data field with unknown meaning (2 bytes, only present if status is success) */
    val data: ByteArray = ByteArray(0),
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        // If status is success, nodeCode and data should be present
        if (statusFlag1 == 0x00.toByte()) {
            require(nodeCode.size == 2) {
                "Node code must be exactly 2 bytes for successful response"
            }
            require(data.size == 2) { "Data must be exactly 2 bytes for successful response" }
        } else {
            // If status is error, nodeCode and data should be empty
            require(nodeCode.isEmpty()) { "Node code should be empty for error response" }
            require(data.isEmpty()) { "Data should be empty for error response" }
        }
    }

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val baseLength = FelicaResponseWithIdm.BASE_LENGTH + 2 // + status flags

        // For error responses, only include base data and status flags
        if (statusFlag1 != 0x00.toByte()) {
            val totalLength = baseLength
            val dataArray = ByteArray(totalLength)
            var offset = 0

            // Length (1 byte)
            dataArray[offset++] = totalLength.toByte()

            // Response code (1 byte)
            dataArray[offset++] = RESPONSE_CODE

            // IDM (8 bytes)
            idm.copyInto(dataArray, offset)
            offset += 8

            // Status flags (2 bytes)
            dataArray[offset++] = statusFlag1
            dataArray[offset++] = statusFlag2

            return dataArray
        }

        // Success response
        val totalLength = baseLength + nodeCode.size + data.size
        val dataArray = ByteArray(totalLength)
        var offset = 0

        // Length (1 byte)
        dataArray[offset++] = totalLength.toByte()

        // Response code (1 byte)
        dataArray[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(dataArray, offset)
        offset += 8

        // Status flags (2 bytes)
        dataArray[offset++] = statusFlag1
        dataArray[offset++] = statusFlag2

        // Node code (2 bytes)
        nodeCode.copyInto(dataArray, offset)
        offset += nodeCode.size

        // Data (2 bytes)
        data.copyInto(dataArray, offset)

        return dataArray
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x25
        const val MIN_ERROR_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 2 // + status_flags(2)
        const val MIN_SUCCESS_LENGTH =
            FelicaResponseWithIdm.BASE_LENGTH +
                2 +
                2 +
                2 // + status_flags(2) + node_code(2) + data(2)

        /** Parse a Get Area Information response from raw bytes */
        fun fromByteArray(data: ByteArray): GetAreaInformationResponse {
            require(data.size >= MIN_ERROR_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_ERROR_LENGTH required"
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

            // Status flags (2 bytes)
            val statusFlag1 = data[offset++]
            val statusFlag2 = data[offset++]

            // If status indicates error, return early
            if (statusFlag1 != 0x00.toByte()) {
                return GetAreaInformationResponse(idm, statusFlag1, statusFlag2)
            }

            // Success case - parse remaining fields
            require(data.size == MIN_SUCCESS_LENGTH) {
                "Success response data must be exactly $MIN_SUCCESS_LENGTH bytes, got ${data.size}"
            }

            // Node code (2 bytes)
            val nodeCode = data.sliceArray(offset until offset + 2)
            offset += 2

            // Data (2 bytes)
            val responseData = data.sliceArray(offset until offset + 2)

            return GetAreaInformationResponse(idm, statusFlag1, statusFlag2, nodeCode, responseData)
        }
    }
}
