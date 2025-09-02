package com.kormax.felicatool.felica

/**
 * Request Service response received from FeliCa cards
 *
 * Contains the Key Versions for the requested Node Codes. If a Node doesn't exist, the key version
 * will be 0xFFFF.
 */
class RequestServiceResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /**
     * Array of Key Versions for the requested nodes Each key version is 2 bytes in Little Endian
     * format. 0xFFFF indicates the node doesn't exist.
     */
    val keyVersions: Array<KeyVersion>,
) : FelicaResponseWithIdm(idm) {

    init {
        require(keyVersions.isNotEmpty()) { "At least one key version must be present" }
        require(keyVersions.size <= 32) { "Maximum 32 key versions can be returned" }
    }

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length = FelicaResponseWithIdm.BASE_LENGTH + 1 + keyVersions.size * 2
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Number of nodes (1 byte)
        data[offset++] = keyVersions.size.toByte()

        // Key versions (2 bytes each, Little Endian)
        keyVersions.forEach { keyVersion ->
            keyVersion.toByteArray().copyInto(data, offset)
            offset += 2
        }

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x03
        const val MIN_LENGTH =
            FelicaResponseWithIdm.BASE_LENGTH + 1 + 2 // + number_of_nodes(1) + min 1 key_version(2)

        /** Parse a Request Service response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestServiceResponse {
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

            // Number of nodes (1 byte)
            val numberOfNodes = data[offset].toInt() and 0xFF
            require(numberOfNodes in 1..32) {
                "Number of nodes must be between 1 and 32, got $numberOfNodes"
            }
            require(data.size >= FelicaResponseWithIdm.BASE_LENGTH + 1 + numberOfNodes * 2) {
                "Data size insufficient for $numberOfNodes key versions"
            }
            offset++

            // Key versions (2 bytes each, Little Endian)
            val keyVersions = mutableListOf<KeyVersion>()
            repeat(numberOfNodes) {
                val keyVersionBytes = data.sliceArray(offset until offset + 2)
                keyVersions.add(KeyVersion(keyVersionBytes))
                offset += 2
            }

            return RequestServiceResponse(idm, keyVersions.toTypedArray())
        }
    }
}
