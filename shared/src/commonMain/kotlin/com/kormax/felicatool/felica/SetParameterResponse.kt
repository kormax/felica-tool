package com.kormax.felicatool.felica

/**
 * Set Parameter response from FeliCa cards Contains IDM and status flags to indicate command
 * execution result
 */
class SetParameterResponse(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Status flag 1 (1 byte) */
    override val statusFlag1: Byte,

    /** Status flag 2 (1 byte) */
    override val statusFlag2: Byte,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = RESPONSE_LENGTH) {
            addByte(statusFlag1)
            addByte(statusFlag2)
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x21
        const val RESPONSE_LENGTH: Int =
            BASE_LENGTH + 2 // Length + Response code + IDM(8) + statusFlag1(1) + statusFlag2(1)

        /** Parse a SetParameter response from raw bytes */
        fun fromByteArray(data: ByteArray): SetParameterResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = RESPONSE_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()
                SetParameterResponse(idm, statusFlag1, statusFlag2)
            }
    }
}
