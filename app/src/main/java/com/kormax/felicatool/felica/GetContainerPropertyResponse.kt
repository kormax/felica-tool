package com.kormax.felicatool.felica

/**
 * Get Container Property response received from FeliCa cards
 *
 * Contains container property data without IDM. The response includes a variable-length data array
 * that is at least 1 byte long.
 */
class GetContainerPropertyResponse(
    /** Property data array (at least 1 byte) */
    val data: ByteArray
) : FelicaResponseWithoutIdm() {

    init {
        require(data.isNotEmpty()) { "Data array must contain at least 1 byte" }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            capacity = FelicaResponseWithoutIdm.BASE_LENGTH + data.size,
        ) {
            addBytes(data)
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x2F
        const val MIN_LENGTH = FelicaResponseWithoutIdm.BASE_LENGTH + 1 // + data(1 minimum)

        /** Parse a Get Container Property response from raw bytes */
        fun fromByteArray(data: ByteArray): GetContainerPropertyResponse =
            parseFelicaResponseWithoutIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) {
                val responseData = bytes(remaining())
                require(responseData.isNotEmpty()) { "Response data must contain at least 1 byte" }
                GetContainerPropertyResponse(responseData)
            }
    }
}
