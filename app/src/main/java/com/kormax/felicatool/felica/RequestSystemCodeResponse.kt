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

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = BASE_LENGTH + 1 + systemCodes.size * 2) {
            addByte(systemCodes.size)
            systemCodes.forEach { addBytes(it) }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x0D
        const val MIN_LENGTH =
            BASE_LENGTH + 1 + 2 // + number_of_system_codes(1) + min 1 system_code(2)

        /** Parse a Request System Code response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestSystemCodeResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val numberOfSystemCodes = uByte()
                require(numberOfSystemCodes > 0) { "System codes list cannot be empty" }
                require(remaining() == numberOfSystemCodes * 2) {
                    "Data length mismatch: expected ${numberOfSystemCodes * 2} bytes for $numberOfSystemCodes system codes, got ${remaining()}"
                }

                val systemCodes = List(numberOfSystemCodes) { bytes(2) }
                RequestSystemCodeResponse(idm, systemCodes)
            }
    }
}
