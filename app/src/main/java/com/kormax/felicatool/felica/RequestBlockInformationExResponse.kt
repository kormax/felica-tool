package com.kormax.felicatool.felica

/**
 * Response to Request Block Information Ex command.
 *
 * Contains detailed information about blocks in the requested services, including assigned block
 * count and free block count for each service.
 */
class RequestBlockInformationExResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag 1 from the response */
    override val statusFlag1: Byte,

    /** Status Flag 2 from the response */
    override val statusFlag2: Byte,

    /** Array of assigned block counts for each requested service */
    val assignedBlockCount: Array<CountInformation>,

    /** Array of free block counts for each requested service */
    val freeBlockCount: Array<CountInformation>,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        require(assignedBlockCount.size == freeBlockCount.size) {
            "Assigned and free block count arrays must have the same size"
        }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity = BASE_LENGTH + 2 + 1 + (assignedBlockCount.size * 4),
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            addByte(assignedBlockCount.size)
            for (i in assignedBlockCount.indices) {
                addBytes(assignedBlockCount[i].toByteArray())
                addBytes(freeBlockCount[i].toByteArray())
            }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x1F
        const val MIN_LENGTH = BASE_LENGTH + 2 + 1 // + status_flags(2) + number_of_blocks(1)

        /** Parse a Request Block Information Ex response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestBlockInformationExResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()
                val numberOfBlocks = uByte()

                require(remaining() >= numberOfBlocks * 4) {
                    "Insufficient data for block information $numberOfBlocks entries"
                }

                val assignedBlockCount = mutableListOf<CountInformation>()
                val freeBlockCount = mutableListOf<CountInformation>()
                repeat(numberOfBlocks) {
                    assignedBlockCount.add(CountInformation(bytes(2)))
                    freeBlockCount.add(CountInformation(bytes(2)))
                }

                RequestBlockInformationExResponse(
                    idm,
                    statusFlag1,
                    statusFlag2,
                    assignedBlockCount.toTypedArray(),
                    freeBlockCount.toTypedArray(),
                )
            }
    }
}
