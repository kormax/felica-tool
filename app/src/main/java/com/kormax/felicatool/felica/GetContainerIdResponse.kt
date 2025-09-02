package com.kormax.felicatool.felica

/**
 * Get Container ID response received from FeliCa cards
 *
 * Contains the container's IDM. The response is essentially just the IDM of the container without
 * any additional data.
 */
class GetContainerIdResponse(
    /** The container's IDM (8 bytes) - unique identifier */
    val containerIdm: ByteArray
) : FelicaResponseWithoutIdm() {

    init {
        require(containerIdm.size == 8) {
            "Container IDM must be exactly 8 bytes, got ${containerIdm.size}"
        }
    }

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length = FelicaResponseWithoutIdm.BASE_LENGTH + 8
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // Container IDM (8 bytes)
        containerIdm.copyInto(data, offset)

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x71
        const val MIN_LENGTH = FelicaResponseWithoutIdm.BASE_LENGTH + 8 // + container_idm(8)

        /** Parse a Get Container ID response from raw bytes */
        fun fromByteArray(data: ByteArray): GetContainerIdResponse {
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

            // Container IDM (8 bytes)
            val containerIdm = data.sliceArray(offset until offset + 8)

            return GetContainerIdResponse(containerIdm)
        }
    }
}
