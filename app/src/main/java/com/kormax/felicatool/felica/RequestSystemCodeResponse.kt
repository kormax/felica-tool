package com.kormax.felicatool.felica

/**
 * Request System Code response received from FeliCa cards
 *
 * Contains the card's IDM and list of system codes existing in the card. System codes are
 * enumerated in ascending order starting from System 0.
 */
class RequestSystemCodeResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /**
     * List of system codes (each 2 bytes) System codes are enumerated in ascending order starting
     * from System 0
     */
    val systemCodes: List<ByteArray>,
) : FelicaResponseWithIdm(idm) {

    init {
        require(systemCodes.isNotEmpty()) { "System codes list cannot be empty" }
        systemCodes.forEach { systemCode ->
            require(systemCode.size == 2) { "Each system code must be exactly 2 bytes" }
        }
    }

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length = FelicaResponseWithIdm.BASE_LENGTH + 1 + systemCodes.size * 2
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Number of System Code (1 byte)
        data[offset++] = systemCodes.size.toByte()

        // System Code List (2 bytes each)
        systemCodes.forEach { systemCode ->
            systemCode.copyInto(data, offset)
            offset += 2
        }

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x0D
        const val MIN_LENGTH =
            FelicaResponseWithIdm.BASE_LENGTH +
                1 +
                2 // + number_of_system_codes(1) + min 1 system_code(2)

        /** Parse a Request System Code response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestSystemCodeResponse {
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

            // Number of System Code (1 byte)
            val numberOfSystemCodes = data[offset].toInt() and 0xFF
            offset++

            // Validate data length matches number of system codes
            val expectedLength = FelicaResponseWithIdm.BASE_LENGTH + 1 + numberOfSystemCodes * 2
            require(data.size == expectedLength) {
                "Data length mismatch: expected $expectedLength bytes for $numberOfSystemCodes system codes, got ${data.size}"
            }

            // System Code List (2 bytes each)
            val systemCodes = mutableListOf<ByteArray>()
            for (i in 0 until numberOfSystemCodes) {
                val systemCode = data.sliceArray(offset until offset + 2)
                systemCodes.add(systemCode)
                offset += 2
            }

            return RequestSystemCodeResponse(idm, systemCodes)
        }
    }
}
