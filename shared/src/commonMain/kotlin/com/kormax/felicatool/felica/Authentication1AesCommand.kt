package com.kormax.felicatool.felica

/**
 * Authentication 1 AES command used for AES authentication with FeliCa cards.
 *
 * This command performs the first phase of AES authentication by sending node codes and challenge1A
 * to the card. The card responds with data. Unlike the DES version, this combines areas and
 * services into a single node codes field with a flag byte (default 0x00) as the first byte of the
 * command data structure.
 */
class Authentication1AesCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** Array of Node codes to authenticate (2 bytes each) */
    val nodeCodes: Array<ByteArray>,

    /** Challenge1A (16 bytes) sent to the card for authentication */
    val challenge1A: ByteArray,

    /** Flag byte (default 0x00) */
    val flag: Byte = 0x00,
) : FelicaCommandWithIdm<Authentication1AesResponse>(idm) {

    init {
        require(nodeCodes.isNotEmpty()) { "At least one node code must be specified" }
        require(nodeCodes.size <= MAX_NODES) {
            "Maximum $MAX_NODES nodes can be authenticated at once"
        }
        require(nodeCodes.all { it.size == 2 }) { "Each node code must be exactly 2 bytes" }
        require(challenge1A.size == 16) {
            "Challenge1A must be exactly 16 bytes, got ${challenge1A.size}"
        }
    }

    /**
     * Alternative constructor that accepts lists of Node objects and combines their codes into a
     * single node codes array.
     *
     * @param idm The 8-byte IDM of the target card
     * @param nodes List of Node objects to authenticate
     * @param challenge1A Challenge1A (16 bytes) sent to the card for authentication
     * @param flag Flag byte (default 0x00) - first byte of the data structure
     */
    constructor(
        idm: ByteArray,
        nodes: List<Node>,
        challenge1A: ByteArray,
        flag: Byte = 0x00,
    ) : this(idm, nodes.map { it.code }.toTypedArray(), challenge1A, flag)

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = nodeCodes.size

    override fun responseFromByteArray(data: ByteArray) =
        Authentication1AesResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            COMMAND_CODE,
            idm,
            capacity = BASE_LENGTH + 2 + (nodeCodes.size * 2) + challenge1A.size,
        ) {
            addByte(flag)
            addByte(nodeCodes.size)
            nodeCodes.forEach { addBytes(it) }
            addBytes(challenge1A)
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x40
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int =
            BASE_LENGTH +
                1 +
                1 +
                2 +
                16 // + flag(1) + nodeCount(1) + min 1 node(2) + challenge1A(16)
        const val MAX_NODES = 16

        /** Parse an Authentication 1 AES command from raw bytes */
        fun fromByteArray(data: ByteArray): Authentication1AesCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val flag = byte()
                val numberOfNodes = uByte()
                require(numberOfNodes > 0) { "At least one node code must be specified" }
                require(numberOfNodes <= MAX_NODES) {
                    "Maximum $MAX_NODES nodes can be authenticated at once"
                }
                require(remaining() >= (numberOfNodes * 2) + 16) {
                    "Data size insufficient for $numberOfNodes node codes and challenge1A"
                }

                val nodeCodes = Array(numberOfNodes) { bytes(2) }
                val challenge1A = bytes(16)

                Authentication1AesCommand(idm, nodeCodes, challenge1A, flag)
            }
    }
}
