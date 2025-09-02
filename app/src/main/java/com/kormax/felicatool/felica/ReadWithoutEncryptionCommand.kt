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

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Number of service codes (1 byte)
        data.add(serviceCodes.size.toByte())

        // Service codes (2 bytes each, Little Endian)
        serviceCodes.forEach { serviceCode -> data.addAll(serviceCode.toList()) }

        // Number of blocks (1 byte)
        data.add(blockListElements.size.toByte())

        // Block list elements (2 bytes each, Little Endian)
        blockListElements.forEach { blockElement ->
            data.addAll(blockElement.toByteArray().toList())
        }

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x06
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int =
            FelicaCommandWithIdm.BASE_LENGTH +
                1 +
                2 +
                1 +
                2 // + num_services(1) + min 1 service(2) + num_blocks(1) + min 1 block(2)
        const val MAX_SERVICE_CODES =
            15 // FeliCa specification limit for service codes is 16, but it does not make sense
        const val MAX_BLOCKS =
            15 // It is possible to read up to 15 blocks at a time, as it takes 240 bytes, vs 254

        // bytes
        // max for FeliCa protocol

        /** Parse a Read Without Encryption command from raw bytes */
        fun fromByteArray(data: ByteArray): ReadWithoutEncryptionCommand {
            require(data.size >= MIN_LENGTH) {
                "Data must be at least $MIN_LENGTH bytes, got ${data.size}"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Command code (1 byte)
            val commandCode = data[offset]
            require(commandCode == COMMAND_CODE.toByte()) {
                "Invalid command code: expected $COMMAND_CODE, got $commandCode"
            }
            offset++

            // IDM (8 bytes)
            val idm = data.sliceArray(offset until offset + 8)
            offset += 8

            // Number of service codes (1 byte)
            val numberOfServices = data[offset].toInt() and 0xFF
            require(numberOfServices in 1..MAX_SERVICE_CODES) {
                "Number of service codes must be between 1 and $MAX_SERVICE_CODES, got $numberOfServices"
            }
            require(data.size >= FelicaCommandWithIdm.BASE_LENGTH + 1 + numberOfServices * 2 + 1) {
                "Data size insufficient for $numberOfServices service codes"
            }
            offset++

            // Service codes (2 bytes each, Little Endian)
            val serviceCodes = mutableListOf<ByteArray>()
            repeat(numberOfServices) {
                val serviceCode = data.sliceArray(offset until offset + 2)
                serviceCodes.add(serviceCode)
                offset += 2
            }

            // Number of blocks (1 byte)
            val numberOfBlocks = data[offset].toInt() and 0xFF
            require(numberOfBlocks in 1..MAX_BLOCKS) {
                "Number of blocks must be between 1 and $MAX_BLOCKS, got $numberOfBlocks"
            }
            offset++

            // Block list elements (2 or 3 bytes each depending on extended format)
            val blockListElements = mutableListOf<BlockListElement>()
            var totalBlockBytes = 0
            repeat(numberOfBlocks) {
                // Check the Length bit (bit 7 of D0) to determine if extended
                val lengthBit = (data[offset].toInt() and 0x80) != 0
                val isExtended = !lengthBit // 1 = 2-byte, 0 = 3-byte
                val blockSize = if (isExtended) 3 else 2

                require(offset + blockSize <= data.size) {
                    "Insufficient data for block ${blockListElements.size}"
                }

                val blockData = data.sliceArray(offset until offset + blockSize)
                blockListElements.add(BlockListElement.fromByteArray(blockData))
                offset += blockSize
                totalBlockBytes += blockSize
            }

            // Verify we consumed all expected data
            val expectedTotalLength =
                FelicaCommandWithIdm.BASE_LENGTH + 1 + numberOfServices * 2 + 1 + totalBlockBytes
            require(length == expectedTotalLength) {
                "Length mismatch after parsing: expected $expectedTotalLength, got $length"
            }

            return ReadWithoutEncryptionCommand(
                idm,
                serviceCodes.toTypedArray(),
                blockListElements.toTypedArray(),
            )
        }
    }
}
