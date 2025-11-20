package com.kormax.felicatool.felica

/**
 * Request Service v2 command used to verify the existence of Area and Service, and to acquire Key
 * Version
 *
 * This command is used to check if specified Areas or Services exist on the card and to retrieve
 * their Key Versions for each supported encryption type. When the specified Area or Service exists
 * and the Key is assigned, the card returns its Key Version. When it doesn't exist or the Key is
 * not assigned, the card returns 0xFFFF as Key Version.
 *
 * The main difference from RequestService is that this command returns key versions for both AES
 * and DES encryption types when supported by the card.
 */
class RequestServiceV2Command(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /**
     * Array of Node Codes (Area Code or Service Code) to query. Each code is 2 bytes in Little
     * Endian format. For System Key Version, use 0xFFFF.
     */
    val nodeCodes: Array<ByteArray>,
) : FelicaCommandWithIdm<RequestServiceV2Response>(idm) {

    init {
        require(nodeCodes.isNotEmpty()) { "At least one node code must be specified" }
        require(nodeCodes.size <= MAX_NODES) { "Maximum 32 node codes can be requested at once" }
        require(nodeCodes.all { it.size == 2 }) { "Each node code must be exactly 2 bytes" }
    }

    /**
     * Alternative constructor that accepts a list of Node objects and extracts their codes
     *
     * @param idm The 8-byte IDM of the target card
     * @param nodes List of Node objects (Area, Service, or System) to query
     */
    constructor(idm: ByteArray, nodes: List<Node>) : this(idm, nodes.map { it.code }.toTypedArray())

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = nodeCodes.size

    override fun responseFromByteArray(data: ByteArray) =
        RequestServiceV2Response.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = BASE_LENGTH + 1 + (nodeCodes.size * 2)) {
            addByte(nodeCodes.size)
            nodeCodes.forEach { addBytes(it) }
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x32
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int = BASE_LENGTH + 1 + 2 // + number_of_nodes(1) + min 1 node(2)
        const val MAX_NODES = 32

        /** Parse a Request Service v2 command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestServiceV2Command =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val numberOfNodes = uByte()
                require(numberOfNodes in 1..MAX_NODES) {
                    "Number of nodes must be between 1 and 32, got $numberOfNodes"
                }
                require(remaining() >= numberOfNodes * 2) {
                    "Data size insufficient for $numberOfNodes nodes"
                }

                val nodeCodes = Array(numberOfNodes) { bytes(2) }
                RequestServiceV2Command(idm, nodeCodes)
            }
    }
}
