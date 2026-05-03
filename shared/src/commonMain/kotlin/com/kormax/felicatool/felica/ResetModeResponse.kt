package com.kormax.felicatool.felica

/**
 * Reset Mode response from FeliCa cards Contains IDM and status flags indicating the result of the
 * reset operation
 */
class ResetModeResponse(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Status Flag1 (1 byte) - indicates success or failure */
    override val statusFlag1: Byte,

    /** Status Flag2 (1 byte) - detailed error information */
    override val statusFlag2: Byte,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = RESPONSE_LENGTH) {
            addByte(statusFlag1)
            addByte(statusFlag2)
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x3F
        const val RESPONSE_LENGTH: Int =
            BASE_LENGTH +
                2 // Length(1) + ResponseCode(1) + IDM(8) + StatusFlag1(1) + StatusFlag2(1)

        /** Parse a ResetMode response from raw bytes */
        fun fromByteArray(data: ByteArray): ResetModeResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = RESPONSE_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()
                ResetModeResponse(idm, statusFlag1, statusFlag2)
            }
    }
}
