package com.kormax.felicatool.felica

/**
 * Authentication 1 AES response received from FeliCa cards
 *
 * Contains the three challenges returned by the card during AES authentication.
 */
class Authentication1AesResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Challenge1B (16 bytes) - reader challenge response from the card */
    val challenge1B: ByteArray,

    /** Challenge2A (16 bytes) - card challenge */
    val challenge2A: ByteArray,

    /** Nonce (4 bytes) - authentication and secure session nonce */
    val nonce: ByteArray,
) : FelicaResponseWithIdm(idm) {

    init {
        require(challenge1B.size == 16) {
            "Challenge1B must be exactly 16 bytes, got ${challenge1B.size}"
        }
        require(challenge2A.size == 16) {
            "Challenge2A must be exactly 16 bytes, got ${challenge2A.size}"
        }
        require(nonce.size == 4) {
            "Nonce must be exactly 4 bytes, got ${nonce.size}"
        }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = EXPECTED_LENGTH) {
            addBytes(challenge1B)
            addBytes(challenge2A)
            addBytes(nonce)
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x41
        const val EXPECTED_LENGTH = BASE_LENGTH + 36 // + data(36)

        /** Parse an Authentication 1 AES response from raw bytes */
        fun fromByteArray(data: ByteArray): Authentication1AesResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = EXPECTED_LENGTH) { idm ->
                val challenge1B = bytes(16)
                val challenge2A = bytes(16)
                val nonce = bytes(4)
                Authentication1AesResponse(idm, challenge1B, challenge2A, nonce)
            }
    }
}
