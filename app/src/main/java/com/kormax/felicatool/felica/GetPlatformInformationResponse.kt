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

    override fun toByteArray(): ByteArray {
        val dataLength = if (statusFlag1 == 0x00.toByte()) 1 + platformInformationData.size else 0
        val length = BASE_LENGTH + 2 + dataLength
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Status Flag1 (1 byte)
        data[offset++] = statusFlag1

        // Status Flag2 (1 byte)
        data[offset++] = statusFlag2

        // For success responses, include length byte and data
        if (statusFlag1 == 0x00.toByte()) {
            // Data length (1 byte)
            data[offset++] = platformInformationData.size.toByte()

            platformInformationData.copyInto(data, offset)
        }

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x3B
        const val MIN_LENGTH: Int =
            BASE_LENGTH + 2 // Length(1) + ResponseCode(1) + IDM(8) + Status1(1) + Status2(1)
        const val MIN_SUCCESS_LENGTH: Int =
            MIN_LENGTH + 1 // + DataLength(1) + at least 0 data bytes

        /** Parse a GetPlatformInformation response from raw bytes */
        fun fromByteArray(data: ByteArray): GetPlatformInformationResponse {
            require(data.size >= MIN_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_LENGTH required"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Response code (1 byte)
            val responseCode = data[offset]
            require(responseCode == RESPONSE_CODE) {
                "Invalid response code: expected $RESPONSE_CODE, got $responseCode"
            }
            offset++

            // IDM (8 bytes)
            val idm = data.sliceArray(offset until offset + 8)
            offset += 8

            // Status Flag1 (1 byte)
            val statusFlag1 = data[offset++]

            // Status Flag2 (1 byte)
            val statusFlag2 = data[offset++]

            // If status indicates error, return early
            if (statusFlag1 != 0x00.toByte()) {
                return GetPlatformInformationResponse(idm, statusFlag1, statusFlag2, ByteArray(0))
            }

            // For success responses, parse length byte and data
            require(data.size >= MIN_SUCCESS_LENGTH) {
                "Success response data too short: ${data.size} bytes, minimum $MIN_SUCCESS_LENGTH required"
            }

            // Data length (1 byte)
            val dataLength = data[offset++].toInt() and 0xFF

            // Validate that we have enough data
            require(data.size == offset + dataLength) {
                "Data length mismatch: expected $dataLength bytes, but ${data.size - offset} bytes remaining"
            }

            val platformInformationData = data.sliceArray(offset until offset + dataLength)

            return GetPlatformInformationResponse(
                idm,
                statusFlag1,
                statusFlag2,
                platformInformationData,
            )
        }
    }
}
