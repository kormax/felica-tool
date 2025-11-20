package com.kormax.felicatool.felica

/**
 * Request Specification Version response received from FeliCa cards
 *
 * Contains the card's OS version information including basic version and option versions.
 */
class RequestSpecificationVersionResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag1 - indicates success or error location */
    override val statusFlag1: Byte,

    /** Status Flag2 - indicates detailed error information */
    override val statusFlag2: Byte,

    /** Specification version information (only present if statusFlag1 == 0x00) */
    val specificationVersion: SpecificationVersion? = null,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity = BASE_LENGTH + 2 + (specificationVersion?.toByteArray()?.size ?: 0),
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            if (statusFlag1 == 0x00.toByte()) {
                specificationVersion?.toByteArray()?.let { addBytes(it) }
            }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x3D
        const val MIN_LENGTH = BASE_LENGTH + 2 // + status_flags(2)

        /** Parse a Request Specification Version response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestSpecificationVersionResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()

                val specificationVersion =
                    if (statusFlag1 == 0x00.toByte() && remaining() > 0) {
                        SpecificationVersion.fromByteArray(bytes(remaining()))
                    } else null

                RequestSpecificationVersionResponse(
                    idm,
                    statusFlag1,
                    statusFlag2,
                    specificationVersion,
                )
            }
    }
}
