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

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length =
            FelicaResponseWithoutIdm.BASE_LENGTH +
                1 +
                data.size // +1 for second byte of response code + 2 for reserved bytes
        val dataArray = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        dataArray[offset++] = length.toByte()

        // Response code (2 bytes)
        dataArray[offset++] = (RESPONSE_CODE.toInt() shr 8).toByte() // High byte
        dataArray[offset++] = RESPONSE_CODE.toByte() // Low byte

        // Echoed data
        data.copyInto(dataArray, offset)
        offset += data.size
        return dataArray
    }

    companion object {
        // ECHO COMMAND HAS THE SAME RESPONSE CODE
        const val RESPONSE_CODE: Short = 0xF000.toShort()
        const val MIN_LENGTH = FelicaResponseWithoutIdm.BASE_LENGTH + 1

        /** Parse an Echo response from raw bytes */
        fun fromByteArray(data: ByteArray): EchoResponse {
            require(data.size >= MIN_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_LENGTH required"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Response code (2 bytes for Echo)
            val responseCode =
                ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            require(responseCode.toShort() == RESPONSE_CODE.toShort()) {
                "Invalid response code: expected $RESPONSE_CODE, got ${responseCode.toShort()}"
            }
            offset += 2

            val echoedData =
                if (data.size > offset) {
                    data.sliceArray(offset until data.size)
                } else {
                    byteArrayOf()
                }

            return EchoResponse(echoedData)
        }
    }
}
