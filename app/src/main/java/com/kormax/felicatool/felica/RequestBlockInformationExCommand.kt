package com.kormax.felicatool.felica

/**
 * Request Block Information Ex command used to acquire information about blocks in a service
 *
 * This command requests detailed information about blocks within specified services, including
 * block count, block attributes, and other block-related properties. This is an extended version
 * that provides additional information including free blocks.
 */
class RequestBlockInformationExCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /**
     * Array of Service Codes to request block information for. Each service code is 2 bytes in
     * Little Endian format.
     */
    val nodeCodes: Array<ByteArray>,
) : FelicaCommandWithIdm<RequestBlockInformationExResponse>(idm) {

    init {
        require(nodeCodes.isNotEmpty()) { "At least one service code must be specified" }
        require(nodeCodes.size <= MAX_SERVICE_CODES) {
            "Maximum 16 service codes can be requested at once"
        }
        require(nodeCodes.all { it.size == 2 }) { "Each service code must be exactly 2 bytes" }
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

        const val MIN_LENGTH: Int = BASE_LENGTH + 1 + 2 // + num_services(1) + min 1 service(2)
        const val MAX_SERVICE_CODES = 32

        /** Parse a Request Block Information Ex command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestBlockInformationExCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val numberOfServices = uByte()
                require(numberOfServices in 1..MAX_SERVICE_CODES) {
                    "Number of service codes must be between 1 and $MAX_SERVICE_CODES, got $numberOfServices"
                }
                require(remaining() >= numberOfServices * 2) {
                    "Data size insufficient for $numberOfServices service codes"
                }

                val nodeCodes = Array(numberOfServices) { bytes(2) }
                RequestBlockInformationExCommand(idm, nodeCodes)
            }
    }
}
