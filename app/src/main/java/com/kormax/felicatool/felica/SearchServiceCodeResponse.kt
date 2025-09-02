package com.kormax.felicatool.felica

/**
 * Search Service Code response received from FeliCa cards Contains a single service code or area
 * entry found on the card
 */
class SearchServiceCodeResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Service code or area entry found on the card, or null if none */
    val node: Node?,
) : FelicaResponseWithIdm(idm) {

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val nodeBytes = node?.toByteArray() ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val length = FelicaResponseWithIdm.BASE_LENGTH + nodeBytes.size
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Node data
        nodeBytes.copyInto(data, offset)

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x0B
        const val MIN_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 2 // At least a Service or System

        /** Parse a search service code response from raw bytes */
        fun fromByteArray(data: ByteArray): SearchServiceCodeResponse {
            require(data.size >= MIN_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_LENGTH required"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
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

            // Parse the node data
            val remaining = data.size - offset
            val node: Node? =
                when (remaining) {
                    2 -> {
                        val bytes = data.sliceArray(offset until offset + 2)
                        if (bytes.contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()))) {
                            System
                        } else {
                            Service.fromByteArray(bytes)
                        }
                    }
                    4 -> Area.fromByteArray(data.sliceArray(offset until offset + 4))
                    else ->
                        throw IllegalArgumentException(
                            "Invalid remaining data length: $remaining bytes"
                        )
                }

            return SearchServiceCodeResponse(idm, node)
        }
    }
}
