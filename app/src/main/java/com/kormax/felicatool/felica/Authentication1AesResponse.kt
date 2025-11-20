package com.kormax.felicatool.felica

/**
 * Authentication 1 AES response received from FeliCa cards
 *
 * Contains the authentication response data from the card. The format is unknown, but the response
 * is 36 bytes long total.
 */
class Authentication1AesResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Response data (36 bytes) - format unknown */
    val data: ByteArray,
) : FelicaResponseWithIdm(idm) {

    init {
        require(data.size == 36) { "Data must be exactly 36 bytes, got ${data.size}" }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = EXPECTED_LENGTH) { addBytes(data) }

    companion object {
        const val RESPONSE_CODE: Short = 0x41
        const val EXPECTED_LENGTH = BASE_LENGTH + 36 // + data(36)

        /** Parse an Authentication 1 AES response from raw bytes */
        fun fromByteArray(data: ByteArray): Authentication1AesResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = EXPECTED_LENGTH) { idm ->
                Authentication1AesResponse(idm, bytes(36))
            }
    }
}
