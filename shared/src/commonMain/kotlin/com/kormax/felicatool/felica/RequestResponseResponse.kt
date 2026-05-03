package com.kormax.felicatool.felica

/**
 * Request Response response received from FeliCa cards
 *
 * Contains the card's IDM and current mode information. This response verifies the card's existence
 * and provides its current operational mode.
 */
class RequestResponseResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Current mode of the card */
    val mode: CardMode,
) : FelicaResponseWithIdm(idm) {

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = MIN_LENGTH) { addByte(mode.value) }

    companion object {
        const val RESPONSE_CODE: Short = 0x05
        const val MIN_LENGTH = BASE_LENGTH + 1 // + Mode(1)

        /** Parse a Request Response response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestResponseResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val modeValue = uByte()
                val mode =
                    when (modeValue) {
                        0 -> CardMode.INITIAL
                        1 -> CardMode.AUTHENTICATION_PENDING
                        2 -> CardMode.AUTHENTICATED
                        3 -> CardMode.ISSUANCE
                        else -> throw IllegalArgumentException("Unknown mode value: $modeValue")
                    }

                RequestResponseResponse(idm, mode)
            }
    }
}
