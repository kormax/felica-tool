package com.kormax.felicatool.felica

/**
 * Authentication 1 DES response received from FeliCa cards
 *
 * Contains the authentication response data including challenge1B and challenge2A that are returned
 * after sending the initial authentication request.
 */
class Authentication1DesResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Challenge1B (8 bytes) - response challenge from the card */
    val challenge1B: ByteArray,

    /** Challenge2A (8 bytes) - second challenge for authentication */
    val challenge2A: ByteArray,
) : FelicaResponseWithIdm(idm) {

    init {
        require(challenge1B.size == 8) {
            "Challenge1B must be exactly 8 bytes, got ${challenge1B.size}"
        }
        require(challenge2A.size == 8) {
            "Challenge2A must be exactly 8 bytes, got ${challenge2A.size}"
        }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = EXPECTED_LENGTH) {
            addBytes(challenge1B)
            addBytes(challenge2A)
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x11
        const val EXPECTED_LENGTH = BASE_LENGTH + 16 // + challenge1B(8) + challenge2A(8)

        /** Parse an Authentication 1 DES response from raw bytes */
        fun fromByteArray(data: ByteArray): Authentication1DesResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = EXPECTED_LENGTH) { idm ->
                val challenge1B = bytes(8)
                val challenge2A = bytes(8)
                Authentication1DesResponse(idm, challenge1B, challenge2A)
            }
    }
}
