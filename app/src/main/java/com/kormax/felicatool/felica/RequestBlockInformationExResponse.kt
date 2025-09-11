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

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Response code (1 byte)
        data.add(RESPONSE_CODE)

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Status Flag 1 (1 byte)
        data.add(statusFlag1)

        // Status Flag 2 (1 byte)
        data.add(statusFlag2)

        // Number of block informations (1 byte)
        data.add(assignedBlockCount.size.toByte())

        // Block informations (4 bytes each: assigned(2) + free(2))
        for (i in assignedBlockCount.indices) {
            data.addAll(assignedBlockCount[i].toByteArray().toList())
            data.addAll(freeBlockCount[i].toByteArray().toList())
        }

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x1F
        const val MIN_LENGTH =
            FelicaResponseWithIdm.BASE_LENGTH + 2 + 1 // + status_flags(2) + number_of_blocks(1)

        /** Parse a Request Block Information Ex response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestBlockInformationExResponse {
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

            // Status Flag 1 (1 byte)
            val statusFlag1 = data[offset]
            offset++

            // Status Flag 2 (1 byte)
            val statusFlag2 = data[offset]
            offset++

            // Number of block informations (1 byte)
            val numberOfBlocks = data[offset].toInt() and 0xFF
            offset++

            // Block informations (4 bytes each: assigned(2) + free(2))
            val assignedBlockCount = mutableListOf<CountInformation>()
            val freeBlockCount = mutableListOf<CountInformation>()
            repeat(numberOfBlocks) {
                require(offset + 4 <= data.size) {
                    "Insufficient data for block information ${assignedBlockCount.size}"
                }

                val assignedBytes = data.sliceArray(offset until offset + 2)
                offset += 2
                assignedBlockCount.add(CountInformation(assignedBytes))

                val freeBytes = data.sliceArray(offset until offset + 2)
                offset += 2
                freeBlockCount.add(CountInformation(freeBytes))
            }

            return RequestBlockInformationExResponse(
                idm,
                statusFlag1,
                statusFlag2,
                assignedBlockCount.toTypedArray(),
                freeBlockCount.toTypedArray(),
            )
        }
    }
}
