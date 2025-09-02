package com.kormax.felicatool.felica

/**
 * Contains detailed information about blocks in the requested services, including block count,
 * block attributes, and other block-related properties.
 */
class RequestBlockInformationResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Array of size information for each requested service */
    val assignedBlockCountInformation: Array<CountInformation>,
) : FelicaResponseWithIdm(idm) {

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Response code (1 byte)
        data.add(RESPONSE_CODE)

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Number of block informations (1 byte)
        data.add(assignedBlockCountInformation.size.toByte())

        // Block informations (2 bytes each)
        assignedBlockCountInformation.forEach { sizeInfo ->
            data.addAll(sizeInfo.toByteArray().toList())
        }

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x0F
        const val MIN_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 1 // + number_of_blocks(1)

        /** Parse a Request Block Information response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestBlockInformationResponse {
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

            // Number of block informations (1 byte)
            val numberOfBlocks = data[offset].toInt() and 0xFF
            offset++

            // Block informations (2 bytes each)
            val assignedBlockCountInformation = mutableListOf<CountInformation>()
            repeat(numberOfBlocks) {
                require(offset + 2 <= data.size) {
                    "Insufficient data for size information ${assignedBlockCountInformation.size}"
                }

                val info = data.sliceArray(offset until offset + 2)
                offset += 2

                assignedBlockCountInformation.add(CountInformation(info))
            }

            return RequestBlockInformationResponse(
                idm,
                assignedBlockCountInformation.toTypedArray(),
            )
        }
    }
}
