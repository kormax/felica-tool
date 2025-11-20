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

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = RESPONSE_LENGTH) {
            addBytes(containerInformation.formatVersionCarrierInformation)
            addBytes(containerInformation.mobilePhoneModelInformation)
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x23
        const val RESPONSE_LENGTH: Int =
            BASE_LENGTH + 16 // Length(1) + ResponseCode(1) + IDM(8) + ContainerInfo(16)

        /** Parse a GetContainerIssueInformation response from raw bytes */
        fun fromByteArray(data: ByteArray): GetContainerIssueInformationResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = RESPONSE_LENGTH) { idm ->
                val formatVersionCarrierInfo = bytes(5)
                val mobilePhoneModelInfo = bytes(11)

                val containerInformation =
                    ContainerInformation(
                        formatVersionCarrierInformation = formatVersionCarrierInfo,
                        mobilePhoneModelInformation = mobilePhoneModelInfo,
                    )

                GetContainerIssueInformationResponse(idm, containerInformation)
            }
    }
}
