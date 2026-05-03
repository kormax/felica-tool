package com.kormax.felicatool.felica

/**
 * Get Container ID response received from FeliCa cards
 *
 * Contains the container's IDM. The response is essentially just the IDM of the container without
 * any additional data.
 */
class GetContainerIdResponse(
    /** The container's IDM (8 bytes) - unique identifier */
    val containerIdm: ByteArray
) : FelicaResponseWithoutIdm() {

    init {
        require(containerIdm.size == 8) {
            "Container IDM must be exactly 8 bytes, got ${containerIdm.size}"
        }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, capacity = MIN_LENGTH) { addBytes(containerIdm) }

    companion object {
        const val RESPONSE_CODE: Short = 0x71
        const val MIN_LENGTH = FelicaResponseWithoutIdm.BASE_LENGTH + 8 // + container_idm(8)

        /** Parse a Get Container ID response from raw bytes */
        fun fromByteArray(data: ByteArray): GetContainerIdResponse =
            parseFelicaResponseWithoutIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) {
                GetContainerIdResponse(bytes(8))
            }
    }
}
