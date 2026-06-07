package com.kormax.felicatool.felica

/** Request Product Information response from FeliCa cards. */
class RequestProductInformationResponse(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Status Flag1 (see section 4.5 "Status Flag") */
    override val statusFlag1: Byte,

    /** Status Flag2 (see section 4.5 "Status Flag") */
    override val statusFlag2: Byte,

    /** Product information data (only present if status is success) */
    val productInformationData: ByteArray = ByteArray(0),
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        // If status is success, product data should be present and properly sized
        if (statusFlag1 == 0x00.toByte()) {
            require(productInformationData.isNotEmpty()) {
                "Product info must be present for successful response"
            }
        } else {
            // For error responses, data should be empty
            require(productInformationData.isEmpty()) {
                "Product info should be empty for error response"
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
                    if (statusFlag1 == 0x00.toByte()) 1 + productInformationData.size else 0,
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            if (statusFlag1 == 0x00.toByte()) {
                addByte(productInformationData.size)
                addBytes(productInformationData)
            }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x3B
        const val MIN_LENGTH: Int =
            BASE_LENGTH + 2 // Length(1) + ResponseCode(1) + IDM(8) + Status1(1) + Status2(1)
        const val MIN_SUCCESS_LENGTH: Int =
            MIN_LENGTH + 1 // + DataLength(1) + at least 0 data bytes

        /** Parse a RequestProductInformation response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestProductInformationResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()

                if (statusFlag1 != 0x00.toByte()) {
                    return RequestProductInformationResponse(
                        idm,
                        statusFlag1,
                        statusFlag2,
                        ByteArray(0),
                    )
                }

                val dataLength = uByte()
                require(dataLength >= 0) { "Product info length cannot be negative" }
                require(remaining() == dataLength) {
                    "Data length mismatch: expected $dataLength bytes, but ${remaining()} bytes remain"
                }

                val productInformationData = bytes(dataLength)
                RequestProductInformationResponse(
                    idm,
                    statusFlag1,
                    statusFlag2,
                    productInformationData,
                )
            }
    }
}
