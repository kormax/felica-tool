package com.kormax.felicatool.felica

/**
 * Get Container Property response received from FeliCa cards
 *
 * Contains container property data without IDM. The response includes a variable-length data array
 * that is at least 1 byte long.
 */
class GetContainerPropertyResponse(
    /** Property data array (at least 1 byte) */
    val data: ByteArray
) : FelicaResponseWithoutIdm() {

    init {
        require(data.isNotEmpty()) { "Data array must contain at least 1 byte" }
    }

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val totalLength = FelicaResponseWithoutIdm.BASE_LENGTH + data.size
        val dataArray = ByteArray(totalLength)
        var offset = 0

        // Length (1 byte)
        dataArray[offset++] = totalLength.toByte()

        // Response code (1 byte)
        dataArray[offset++] = RESPONSE_CODE

        // Data (variable length, at least 1 byte)
        data.copyInto(dataArray, offset)

        return dataArray
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x2F
        const val MIN_LENGTH = FelicaResponseWithoutIdm.BASE_LENGTH + 1 // + data(1 minimum)

        /** Parse a Get Container Property response from raw bytes */
        fun fromByteArray(data: ByteArray): GetContainerPropertyResponse {
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

            // Data (remaining bytes, at least 1)
            val responseData = data.sliceArray(offset until data.size)
            require(responseData.isNotEmpty()) { "Response data must contain at least 1 byte" }

            return GetContainerPropertyResponse(responseData)
        }
    }
}
