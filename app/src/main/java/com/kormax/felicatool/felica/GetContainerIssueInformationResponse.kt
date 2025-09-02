package com.kormax.felicatool.felica

/**
 * Get Container Issue Information response from FeliCa cards Contains IDM and container information
 * data
 */
class GetContainerIssueInformationResponse(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Container information (16 bytes total: 5 bytes format version + 11 bytes model info) */
    val containerInformation: ContainerInformation,
) : FelicaResponseWithIdm(idm) {

    override fun toByteArray(): ByteArray {
        val data = ByteArray(RESPONSE_LENGTH)
        var offset = 0

        // Length (1 byte)
        data[offset++] = RESPONSE_LENGTH.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Container information (16 bytes)
        containerInformation.formatVersionCarrierInformation.copyInto(data, offset)
        offset += 5
        containerInformation.mobilePhoneModelInformation.copyInto(data, offset)

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x23
        const val RESPONSE_LENGTH: Int =
            BASE_LENGTH + 16 // Length(1) + ResponseCode(1) + IDM(8) + ContainerInfo(16)

        /** Parse a GetContainerIssueInformation response from raw bytes */
        fun fromByteArray(data: ByteArray): GetContainerIssueInformationResponse {
            require(data.size >= RESPONSE_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $RESPONSE_LENGTH required"
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

            // Container information (16 bytes)
            val containerInfoData = data.sliceArray(offset until offset + 16)
            require(containerInfoData.size == 16) {
                "Container information must be exactly 16 bytes, got ${containerInfoData.size}"
            }

            val formatVersionCarrierInfo = containerInfoData.sliceArray(0 until 5)
            val mobilePhoneModelInfo = containerInfoData.sliceArray(5 until 16)

            val containerInformation =
                ContainerInformation(
                    formatVersionCarrierInformation = formatVersionCarrierInfo,
                    mobilePhoneModelInformation = mobilePhoneModelInfo,
                )

            return GetContainerIssueInformationResponse(idm, containerInformation)
        }
    }
}
