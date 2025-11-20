package com.kormax.felicatool.felica

/**
 * Get Platform Information response from FeliCa cards Contains IDM, status flags, and secure
 * element information data
 */
class GetPlatformInformationResponse(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Status Flag1 (see section 4.5 "Status Flag") */
    override val statusFlag1: Byte,

    /** Status Flag2 (see section 4.5 "Status Flag") */
    override val statusFlag2: Byte,

    /** Platform information data (only present if status is success) */
    val platformInformationData: ByteArray = ByteArray(0),
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        // If status is success, platform data should be present and properly sized
        if (statusFlag1 == 0x00.toByte()) {
            require(platformInformationData.isNotEmpty()) {
                "Platform info must be present for successful response"
            }
        } else {
            // For error responses, data should be empty
            require(platformInformationData.isEmpty()) {
                "Platform info should be empty for error response"
            }
        }
    }

    /** Indicates if the response indicates success */
    val success: Boolean
        get() = statusFlag1 == 0x00.toByte()

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity =
                BASE_LENGTH +
                    2 +
                    if (statusFlag1 == 0x00.toByte()) 1 + platformInformationData.size else 0,
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            if (statusFlag1 == 0x00.toByte()) {
                addByte(platformInformationData.size)
                addBytes(platformInformationData)
            }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x3B
        const val MIN_LENGTH: Int =
            BASE_LENGTH + 2 // Length(1) + ResponseCode(1) + IDM(8) + Status1(1) + Status2(1)
        const val MIN_SUCCESS_LENGTH: Int =
            MIN_LENGTH + 1 // + DataLength(1) + at least 0 data bytes

        /** Parse a GetPlatformInformation response from raw bytes */
        fun fromByteArray(data: ByteArray): GetPlatformInformationResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()

                if (statusFlag1 != 0x00.toByte()) {
                    return GetPlatformInformationResponse(
                        idm,
                        statusFlag1,
                        statusFlag2,
                        ByteArray(0),
                    )
                }

                val dataLength = uByte()
                require(dataLength >= 0) { "Platform info length cannot be negative" }
                require(remaining() == dataLength) {
                    "Data length mismatch: expected $dataLength bytes, but ${remaining()} bytes remain"
                }

                val platformInformationData = bytes(dataLength)
                GetPlatformInformationResponse(
                    idm,
                    statusFlag1,
                    statusFlag2,
                    platformInformationData,
                )
            }
    }
}
