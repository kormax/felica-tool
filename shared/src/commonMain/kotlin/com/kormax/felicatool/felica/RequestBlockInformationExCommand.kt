package com.kormax.felicatool.felica

/**
 * Request Block Information Ex command used to acquire assigned and free block counts for nodes.
 *
 * This is the extended variant of Request Block Information and returns both assigned and free
 * block counts.
 */
class RequestBlockInformationExCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /**
     * Array of node codes to request block information for. Each node code is 2 bytes in Little
     * Endian format.
     */
    val nodeCodes: Array<ByteArray>,
) : FelicaCommandWithIdm<RequestBlockInformationExResponse>(idm) {

    init {
        require(nodeCodes.isNotEmpty()) { "At least one node code must be specified" }
        require(nodeCodes.size <= MAX_NODE_CODES) {
            "Maximum $MAX_NODE_CODES node codes can be requested at once"
        }
        require(nodeCodes.all { it.size == 2 }) { "Each node code must be exactly 2 bytes" }
    }

    /**
     * Alternative constructor that takes an array of Node objects.
     *
     * @param idm The 8-byte IDM of the target card
     * @param nodes Array of Node objects to request block information for
     */
    constructor(
        idm: ByteArray,
        nodes: Array<Node>,
    ) : this(idm, nodes.map { it.code }.toTypedArray())

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = nodeCodes.size

    override fun responseFromByteArray(data: ByteArray) =
        RequestBlockInformationExResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = BASE_LENGTH + 1 + (nodeCodes.size * 2)) {
            addByte(nodeCodes.size)
            nodeCodes.forEach { addBytes(it) }
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x1E
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int = BASE_LENGTH + 1 + 2 // + num_nodes(1) + min 1 node(2)
        const val MAX_NODE_CODES = 32

        /** Parse a Request Block Information Ex command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestBlockInformationExCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val numberOfNodes = uByte()
                require(numberOfNodes in 1..MAX_NODE_CODES) {
                    "Number of node codes must be between 1 and $MAX_NODE_CODES, got $numberOfNodes"
                }
                require(remaining() >= numberOfNodes * 2) {
                    "Data size insufficient for $numberOfNodes node codes"
                }

                val nodeCodes = Array(numberOfNodes) { bytes(2) }
                RequestBlockInformationExCommand(idm, nodeCodes)
            }
    }
}
