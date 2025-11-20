package com.kormax.felicatool.felica

/**
 * Search Service Code response received from FeliCa cards Contains a single service code or area
 * entry found on the card
 */
class SearchServiceCodeResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Service code or area entry found on the card, or null if none */
    val node: Node,
) : FelicaResponseWithIdm(idm) {

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = BASE_LENGTH + node.toByteArray().size) {
            addBytes(node.toByteArray())
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x0B
        const val MIN_LENGTH = BASE_LENGTH + 2 // At least a Service or System

        /** Parse a search service code response from raw bytes */
        fun fromByteArray(data: ByteArray): SearchServiceCodeResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val node: Node =
                    when (val remaining = remaining()) {
                        2 -> {
                            val bytes = bytes(2)
                            if (bytes.contentEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()))) {
                                System
                            } else {
                                Service.fromByteArray(bytes)
                            }
                        }
                        4 -> Area.fromByteArray(bytes(4))
                        else ->
                            throw IllegalArgumentException(
                                "Invalid remaining data length: $remaining bytes"
                            )
                    }

                SearchServiceCodeResponse(idm, node)
            }
    }
}
