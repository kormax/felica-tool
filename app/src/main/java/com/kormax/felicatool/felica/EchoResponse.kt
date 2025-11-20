package com.kormax.felicatool.felica

/**
 * Echo response received from FeliCa cards
 *
 * Contains the data that was sent in the Echo command. The card echoes back the same data that was
 * sent to it.
 */
class EchoResponse(
    /** The echoed data received from the card */
    val data: ByteArray
) : FelicaResponseWithoutIdm() {

    init {
        require(data.size <= 252) { "Data must be at most 252 bytes, got ${data.size}" }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            capacity = FelicaResponseWithoutIdm.BASE_LENGTH + 1 + data.size,
        ) {
            addBytes(data)
        }

    companion object {
        // ECHO COMMAND HAS THE SAME RESPONSE CODE
        const val RESPONSE_CODE: Short = 0xF000.toShort()
        const val MIN_LENGTH = FelicaResponseWithoutIdm.BASE_LENGTH + 1

        /** Parse an Echo response from raw bytes */
        fun fromByteArray(data: ByteArray): EchoResponse =
            parseFelicaResponseWithoutIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) {
                val echoedData = if (remaining() > 0) bytes(remaining()) else byteArrayOf()
                EchoResponse(echoedData)
            }
    }
}
