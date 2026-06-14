package com.kormax.felicatool.felica

/**
 * Contains block count information for the requested nodes. The non-extended command reports free
 * blocks for areas and assigned blocks for services.
 */
class RequestBlockInformationResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Array of block count information for each requested node */
    val blockCountInformation: Array<CountInformation>,
) : FelicaResponseWithIdm(idm) {

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity = BASE_LENGTH + 1 + (blockCountInformation.size * 2),
        ) {
            addByte(blockCountInformation.size)
            blockCountInformation.forEach { addBytes(it.toByteArray()) }
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

                val blockCountInformation = Array(numberOfBlocks) { CountInformation(bytes(2)) }

                RequestBlockInformationResponse(idm, blockCountInformation)
            }
    }
}
