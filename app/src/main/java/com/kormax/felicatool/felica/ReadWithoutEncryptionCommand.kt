package com.kormax.felicatool.felica

/**
 * Read Without Encryption command used to read data from FeliCa cards without encryption
 *
 * This command reads data blocks from specified services without requiring authentication. The card
 * returns the raw block data (16 bytes per block).
 *
 * IMPORTANT: Service codes must be provided in ascending order, and block list elements must be
 * ordered by service code (all blocks for first service, then second service, etc.) with block
 * numbers in ascending order within each service.
 */
class ReadWithoutEncryptionCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /**
     * Array of Service Codes to read from. Each service code is 2 bytes in Little Endian format.
     */
    val serviceCodes: Array<ByteArray>,

    /**
     * Array of Block List Elements to read. Each block list element contains the block number and
     * access mode.
     */
    val blockListElements: Array<BlockListElement>,
) : FelicaCommandWithIdm<ReadWithoutEncryptionResponse>(idm) {

    init {
        require(serviceCodes.isNotEmpty()) { "At least one service code must be specified" }
        require(serviceCodes.size <= MAX_SERVICE_CODES) {
            "Maximum 16 service codes can be requested at once"
        }
        require(serviceCodes.all { it.size == 2 }) { "Each service code must be exactly 2 bytes" }
        require(blockListElements.isNotEmpty()) {
            "At least one block list element must be specified"
        }
        require(blockListElements.size <= MAX_BLOCKS) {
            "Maximum $MAX_BLOCKS blocks can be requested at once"
        }

        // Validate service code list order - service codes must be in ascending order
        for (i in 1 until serviceCodes.size) {

            val prevServiceCode =
                (serviceCodes[i - 1][1].toInt() and 0xFF) shl
                    8 or
                    (serviceCodes[i - 1][0].toInt() and 0xFF)
            val currentServiceCode =
                (serviceCodes[i][1].toInt() and 0xFF) shl 8 or (serviceCodes[i][0].toInt() and 0xFF)
            require(prevServiceCode <= currentServiceCode) {
                "Service codes must be in ascending order. Service code at index ${i - 1} ($prevServiceCode) >= service code at index $i ($currentServiceCode)"
            }
        }

        // Validate block list order - blocks must be ordered by service code, then by block number
        // Group blocks by service code list order
        val blocksByService = blockListElements.groupBy { it.serviceCodeListOrder }

        // Validate that all service codes have corresponding blocks
        for (serviceIndex in serviceCodes.indices) {
            require(blocksByService.containsKey(serviceIndex)) {
                "Missing blocks for service at index $serviceIndex"
            }
        }

        // Validate that blocks within each service are in ascending order
        for ((serviceIndex, blocks) in blocksByService) {
            val sortedBlocks = blocks.sortedBy { it.blockNumber }
            for (i in 1 until blocks.size) {
                val prevBlockNumber = sortedBlocks[i - 1].blockNumber
                val currentBlockNumber = sortedBlocks[i].blockNumber
                require(prevBlockNumber <= currentBlockNumber) {
                    "Block numbers for service $serviceIndex must be in ascending order. Block $prevBlockNumber >= $currentBlockNumber"
                }
            }
        }

        // Validate that blocks appear in service code list order
        var expectedServiceOrder = 0
        var currentServiceOrder = -1
        for (block in blockListElements) {
            if (block.serviceCodeListOrder != currentServiceOrder) {
                require(block.serviceCodeListOrder >= expectedServiceOrder) {
                    "Service code list order must be non-decreasing. Expected >= $expectedServiceOrder, got ${block.serviceCodeListOrder}"
                }
                currentServiceOrder = block.serviceCodeListOrder
                expectedServiceOrder = currentServiceOrder
            }
        }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = blockListElements.size

    override fun responseFromByteArray(data: ByteArray) =
        ReadWithoutEncryptionResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            COMMAND_CODE,
            idm,
            capacity =
                BASE_LENGTH +
                    1 +
                    (serviceCodes.size * 2) +
                    1 +
                    blockListElements.sumOf { it.toByteArray().size },
        ) {
            addByte(serviceCodes.size)
            serviceCodes.forEach { addBytes(it) }
            addByte(blockListElements.size)
            blockListElements.forEach { addBytes(it.toByteArray()) }
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x06
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int =
            BASE_LENGTH +
                1 +
                2 +
                1 +
                2 // + num_services(1) + min 1 service(2) + num_blocks(1) + min 1 block(2)
        const val MAX_SERVICE_CODES = 16 // FeliCa specification limit
        const val MAX_BLOCKS =
            16 // Full protocol limit; large reads should stay below card payload caps

        /** Parse a Read Without Encryption command from raw bytes */
        fun fromByteArray(data: ByteArray): ReadWithoutEncryptionCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val numberOfServices = uByte()
                require(numberOfServices in 1..MAX_SERVICE_CODES) {
                    "Number of service codes must be between 1 and $MAX_SERVICE_CODES, got $numberOfServices"
                }
                require(remaining() >= (numberOfServices * 2) + 1) {
                    "Data size insufficient for $numberOfServices service codes"
                }

                val serviceCodes = Array(numberOfServices) { bytes(2) }

                val numberOfBlocks = uByte()
                require(numberOfBlocks in 1..MAX_BLOCKS) {
                    "Number of blocks must be between 1 and $MAX_BLOCKS, got $numberOfBlocks"
                }

                val blockListElements = mutableListOf<BlockListElement>()
                repeat(numberOfBlocks) {
                    val first = byte()
                    val isExtended = (first.toInt() and 0x80) == 0
                    val blockSize = if (isExtended) 3 else 2
                    val rest = bytes(blockSize - 1)
                    blockListElements.add(BlockListElement.fromByteArray(byteArrayOf(first) + rest))
                }

                ReadWithoutEncryptionCommand(idm, serviceCodes, blockListElements.toTypedArray())
            }
    }
}
