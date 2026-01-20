package com.kormax.felicatool.felica

/**
 * Internal Authenticate and Read command (0x34)
 *
 * Reads block data with a 32-byte MAC for authentication. Requires MAC communication to be enabled
 * on the node.
 *
 * Unlike ReadWithoutEncryption:
 * - Service order in the service list does not affect the result
 * - Services can be repeated in the service list
 * - More services can be specified than blocks actually read
 * - Block list elements can be in any order (unsorted by service index or block number)
 * - Response blocks are returned in the exact order requested, not sorted
 */
class InternalAuthenticateAndReadCommand(
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

    /** Challenge data for authentication. 16 bytes. */
    val challenge: ByteArray,

    /** Reserved byte. Should be 0x00 for standard usage. */
    val reserved: Byte = 0x00,
) : FelicaCommandWithIdm<InternalAuthenticateAndReadResponse>(idm) {

    init {
        require(serviceCodes.isNotEmpty()) { "At least one service code must be specified" }
        require(serviceCodes.size <= MAX_SERVICE_CODES) {
            "Maximum $MAX_SERVICE_CODES service codes can be requested at once"
        }
        require(serviceCodes.all { it.size == 2 }) { "Each service code must be exactly 2 bytes" }
        require(blockListElements.isNotEmpty()) {
            "At least one block list element must be specified"
        }
        require(blockListElements.size <= MAX_BLOCKS) {
            "Maximum $MAX_BLOCKS blocks can be requested at once"
        }
        require(challenge.size >= MIN_CHALLENGE_LENGTH) {
            "Challenge must be at least $MIN_CHALLENGE_LENGTH bytes, got ${challenge.size}"
        }

        // Unlike ReadWithoutEncryption, there are no constraints upon
        // ordering or duplication of service codes or block list elements.

        // Validate that block list elements reference valid service indices
        for (block in blockListElements) {
            require(block.serviceCodeListOrder < serviceCodes.size) {
                "Block references service index ${block.serviceCodeListOrder} but only ${serviceCodes.size} services provided"
            }
        }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = blockListElements.size

    override fun responseFromByteArray(data: ByteArray) =
        InternalAuthenticateAndReadResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            COMMAND_CODE,
            idm,
            capacity =
                BASE_LENGTH +
                    1 + // Reserved byte
                    1 + // NumServices
                    (serviceCodes.size * 2) +
                    1 + // NumBlocks
                    blockListElements.sumOf { it.toByteArray().size } +
                    challenge.size,
        ) {
            addByte(reserved)
            addByte(serviceCodes.size)
            serviceCodes.forEach { addBytes(it) }
            addByte(blockListElements.size)
            blockListElements.forEach { addBytes(it.toByteArray()) }
            addBytes(challenge)
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x34
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int =
            BASE_LENGTH +
                1 + // Reserved
                1 + // NumServices
                2 + // Min 1 service (2 bytes)
                1 + // NumBlocks
                2 + // Min 1 block (2 bytes)
                16 // Min challenge (16 bytes)

        const val MAX_SERVICE_CODES = 16 // FeliCa specification limit
        const val MAX_BLOCKS = 13 // Protocol limit for this command
        const val MIN_CHALLENGE_LENGTH = 16 // Minimum challenge size

        /** Parse an Internal Authenticate and Read command from raw bytes */
        fun fromByteArray(data: ByteArray): InternalAuthenticateAndReadCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val reserved = byte()

                val numberOfServices = uByte()
                require(numberOfServices in 1..MAX_SERVICE_CODES) {
                    "Number of service codes must be between 1 and $MAX_SERVICE_CODES, got $numberOfServices"
                }
                require(remaining() >= (numberOfServices * 2) + 1 + 2 + MIN_CHALLENGE_LENGTH) {
                    "Data size insufficient for $numberOfServices service codes, blocks, and challenge"
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

                val challenge = bytes(remaining())
                require(challenge.size >= MIN_CHALLENGE_LENGTH) {
                    "Challenge must be at least $MIN_CHALLENGE_LENGTH bytes, got ${challenge.size}"
                }

                InternalAuthenticateAndReadCommand(
                    idm,
                    serviceCodes,
                    blockListElements.toTypedArray(),
                    challenge,
                    reserved,
                )
            }
    }
}
