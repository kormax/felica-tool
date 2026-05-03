package com.kormax.felicatool.felica

/**
 * Write Without Encryption response received from FeliCa cards
 *
 * Contains status flags indicating whether the write operation was successful.
 */
class WriteWithoutEncryptionResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag 1 - indicates success or error type */
    override val statusFlag1: Byte,

    /** Status Flag 2 - provides detailed error information */
    override val statusFlag2: Byte,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = RESPONSE_LENGTH) {
            addByte(statusFlag1)
            addByte(statusFlag2)
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x09
        const val RESPONSE_LENGTH = BASE_LENGTH + 2 // + status_flags(2)

        /** Parse a Write Without Encryption response from raw bytes */
        fun fromByteArray(data: ByteArray): WriteWithoutEncryptionResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = RESPONSE_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()

                WriteWithoutEncryptionResponse(idm, statusFlag1, statusFlag2)
            }
    }
}
