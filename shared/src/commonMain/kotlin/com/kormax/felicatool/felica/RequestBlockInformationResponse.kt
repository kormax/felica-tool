package com.kormax.felicatool.felica

/**
 * Contains detailed information about blocks in the requested services, including block count,
 * block attributes, and other block-related properties.
 */
class RequestBlockInformationResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Array of size information for each requested service */
    val assignedBlockCountInformation: Array<CountInformation>,
) : FelicaResponseWithIdm(idm) {

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity = BASE_LENGTH + 1 + (assignedBlockCountInformation.size * 2),
        ) {
            addByte(assignedBlockCountInformation.size)
            assignedBlockCountInformation.forEach { addBytes(it.toByteArray()) }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x0F
        const val MIN_LENGTH = BASE_LENGTH + 1 // + number_of_blocks(1)

        /** Parse a Request Block Information response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestBlockInformationResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val numberOfBlocks = uByte()
                require(remaining() >= numberOfBlocks * 2) {
                    "Insufficient data for size information $numberOfBlocks blocks"
                }

                val assignedBlockCountInformation =
                    Array(numberOfBlocks) { CountInformation(bytes(2)) }

                RequestBlockInformationResponse(idm, assignedBlockCountInformation)
            }
    }
}
