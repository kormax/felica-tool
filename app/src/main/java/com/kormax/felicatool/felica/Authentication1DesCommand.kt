package com.kormax.felicatool.felica

/**
 * Authentication 1 DES command used for DES authentication with FeliCa cards.
 *
 * This command performs the first phase of DES authentication by sending area codes, node codes,
 * and challenge1A to the card. The card responds with challenge1B and challenge2A.
 */
class Authentication1DesCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** Array of Area codes to authenticate (2 bytes each) */
    val areaCodes: Array<ByteArray>,

    /** Array of Node codes (system/area/service) to authenticate (2 bytes each) */
    val nodeCodes: Array<ByteArray>,

    /** Challenge1A (8 bytes) sent to the card for authentication */
    val challenge1A: ByteArray,
) : FelicaCommandWithIdm<Authentication1DesResponse>(idm) {

    init {
        require(areaCodes.isNotEmpty() || nodeCodes.isNotEmpty()) {
            "At least one area or node code must be specified"
        }
        require(areaCodes.size + nodeCodes.size <= MAX_NODES) {
            "Maximum $MAX_NODES nodes can be authenticated at once"
        }
        require(areaCodes.all { it.size == 2 }) { "Each area code must be exactly 2 bytes" }
        require(nodeCodes.all { it.size == 2 }) { "Each node code must be exactly 2 bytes" }
        require(challenge1A.size == 8) {
            "Challenge1A must be exactly 8 bytes, got ${challenge1A.size}"
        }
    }

    /**
     * Alternative constructor that accepts lists of Area objects and Node objects and extracts
     * their codes
     *
     * @param idm The 8-byte IDM of the target card
     * @param areaNodes List of Area objects to authenticate
     * @param nodes List of Node objects (Service/Area) to authenticate
     * @param challenge1A Challenge1A (8 bytes) sent to the card for authentication
     */
    constructor(
        idm: ByteArray,
        areaNodes: List<Area>,
        nodes: List<Node>,
        challenge1A: ByteArray,
    ) : this(
        idm,
        areaNodes.map { it.code }.toTypedArray(),
        nodes.map { it.code }.toTypedArray(),
        challenge1A,
    )

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = areaCodes.size + nodeCodes.size

    override fun responseFromByteArray(data: ByteArray) =
        Authentication1DesResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            COMMAND_CODE,
            idm,
            capacity =
                BASE_LENGTH + 2 + (areaCodes.size * 2) + (nodeCodes.size * 2) + challenge1A.size,
        ) {
            addByte(areaCodes.size)
            areaCodes.forEach { addBytes(it) }
            addByte(nodeCodes.size)
            nodeCodes.forEach { addBytes(it) }
            addBytes(challenge1A)
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x10
        override val COMMAND_CLASS: CommandClass = CommandClass.MUTUAL_AUTH

        const val MIN_LENGTH: Int =
            BASE_LENGTH + 1 + 1 + 8 // + area_count(1) + service_count(1) + challenge1A(8)
        const val MAX_NODES = 64

        /** Parse an Authentication 1 DES command from raw bytes */
        fun fromByteArray(data: ByteArray): Authentication1DesCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = MIN_LENGTH) { idm ->
                val numberOfAreaCodes = uByte()
                val areaCodes = Array(numberOfAreaCodes) { bytes(2) }

                val numberOfNodeCodes = uByte()
                require(numberOfAreaCodes + numberOfNodeCodes > 0) {
                    "At least one area or node code must be specified"
                }
                require(numberOfAreaCodes + numberOfNodeCodes <= MAX_NODES) {
                    "Maximum $MAX_NODES nodes can be authenticated at once"
                }

                val nodeCodes = Array(numberOfNodeCodes) { bytes(2) }
                val challenge1A = bytes(8)

                Authentication1DesCommand(idm, areaCodes, nodeCodes, challenge1A)
            }
    }
}
