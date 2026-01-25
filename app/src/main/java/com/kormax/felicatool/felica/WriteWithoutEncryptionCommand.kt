package com.kormax.felicatool.felica

/**
 * Write Without Encryption command used to write data to FeliCa cards without encryption
 *
 * This command writes data blocks to specified services without requiring authentication. Use this
 * command to write Block Data to authentication-not-required Service.
 *
 * IMPORTANT: Service codes must be provided in ascending order, and block list elements must be
 * ordered by service code (all blocks for first service, then second service, etc.) with block
 * numbers in ascending order within each service.
 */
class WriteWithoutEncryptionCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** Array of Service Codes to write to. Each service code is 2 bytes in Little Endian format. */
    val serviceCodes: Array<ByteArray>,

    /**
     * Array of Block List Elements to write. Each block list element contains the block number and
     * access mode.
     */
    val blockListElements: Array<BlockListElement>,

    /**
     * Array of block data to write. Each block is 16 bytes. The number of blocks must match the
     * number of block list elements.
     */
    val blockData: Array<ByteArray>,
) : FelicaCommandWithIdm<WriteWithoutEncryptionResponse>(idm) {

    init {
        require(serviceCodes.isNotEmpty()) { "At least one service code must be specified" }
        require(serviceCodes.size <= MAX_SERVICE_CODES) {
            "Maximum $MAX_SERVICE_CODES service codes can be specified at once"
        }
        require(serviceCodes.all { it.size == 2 }) { "Each service code must be exactly 2 bytes" }
        require(blockListElements.isNotEmpty()) {
            "At least one block list element must be specified"
        }
        require(blockListElements.size <= MAX_BLOCKS) {
            "Maximum $MAX_BLOCKS blocks can be written at once"
        }
        require(blockData.size == blockListElements.size) {
            "Number of block data (${blockData.size}) must match number of block list elements (${blockListElements.size})"
        }
        require(blockData.all { it.size == BLOCK_SIZE }) {
            "Each block data must be exactly $BLOCK_SIZE bytes"
        }

        // Validate command payload budget
        // Command structure: length(1) + command_code(1) + IDM(8) + num_services(1) +
        //                    service_codes(2*n) + num_blocks(1) + block_list + block_data(16*m)
        val blockListSize = blockListElements.sumOf { it.toByteArray().size }
        val blockDataSize = blockData.size * BLOCK_SIZE
        val payloadSize =
            1 + 1 + 8 + 1 + (serviceCodes.size * 2) + 1 + blockListSize + blockDataSize
        require(payloadSize <= MAX_COMMAND_LENGTH) {
            "Command payload size ($payloadSize bytes) exceeds maximum allowed ($MAX_COMMAND_LENGTH bytes). " +
                "Try reducing the number of blocks (current: ${blockListElements.size}) or using 2-byte block list format."
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

        // Validate the order that blocks appear in service code list order
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
        WriteWithoutEncryptionResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            COMMAND_CODE,
            idm,
            capacity =
                BASE_LENGTH +
                    1 +
                    (serviceCodes.size * 2) +
                    1 +
                    blockListElements.sumOf { it.toByteArray().size } +
                    (blockData.size * BLOCK_SIZE),
        ) {
            addByte(serviceCodes.size)
            serviceCodes.forEach { addBytes(it) }
            addByte(blockListElements.size)
            blockListElements.forEach { addBytes(it.toByteArray()) }
            blockData.forEach { addBytes(it) }
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x08
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val BLOCK_SIZE: Int = 16
        const val MIN_LENGTH: Int =
            BASE_LENGTH +
                1 +
                2 +
                1 +
                2 +
                16 // + num_services(1) + min 1 service(2) + num_blocks(1) + min 1 block(2) + min 1
        // block_data(16)
        const val MAX_SERVICE_CODES = 16 // FeliCa specification limit
        const val MAX_BLOCKS =
            13 // Physical limit for this command: 1 + 1 + 8 + 1 + 2 + 1 + 13 * (16 + 2) = 248
        const val MAX_COMMAND_LENGTH = 255 // Maximum FeliCa command length

        /** Parse a Write Without Encryption command from raw bytes */
        fun fromByteArray(data: ByteArray): WriteWithoutEncryptionCommand =
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

                require(remaining() == numberOfBlocks * BLOCK_SIZE) {
                    "Block data length mismatch: expected ${numberOfBlocks * BLOCK_SIZE} bytes, got ${remaining()} remaining bytes"
                }

                val blockData = Array(numberOfBlocks) { bytes(BLOCK_SIZE) }

                WriteWithoutEncryptionCommand(
                    idm,
                    serviceCodes,
                    blockListElements.toTypedArray(),
                    blockData,
                )
            }
    }
}
