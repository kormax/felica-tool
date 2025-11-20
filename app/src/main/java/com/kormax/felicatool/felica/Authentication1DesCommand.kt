package com.kormax.felicatool.felica

/**
 * Authentication 1 DES command used for DES authentication with FeliCa cards.
 *
 * This command performs the first phase of DES authentication by sending area codes, service codes,
 * and challenge1A to the card. The card responds with challenge1B and challenge2A.
 */
class Authentication1DesCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** Array of Area codes to authenticate (2 bytes each) */
    val areaCodes: Array<ByteArray>,

    /** Array of Service codes to authenticate (2 bytes each) */
    val serviceCodes: Array<ByteArray>,

    /** Challenge1A (8 bytes) sent to the card for authentication */
    val challenge1A: ByteArray,
) : FelicaCommandWithIdm<Authentication1DesResponse>(idm) {

    init {
        require(areaCodes.isNotEmpty() || serviceCodes.isNotEmpty()) {
            "At least one area or service code must be specified"
        }
        require(areaCodes.size + serviceCodes.size <= MAX_NODES) {
            "Maximum $MAX_NODES nodes can be authenticated at once"
        }
        require(areaCodes.all { it.size == 2 }) { "Each area code must be exactly 2 bytes" }
        require(serviceCodes.all { it.size == 2 }) { "Each service code must be exactly 2 bytes" }
        require(challenge1A.size == 8) {
            "Challenge1A must be exactly 8 bytes, got ${challenge1A.size}"
        }
    }

    /**
     * Alternative constructor that accepts lists of Area and Service objects and extracts their
     * codes
     *
     * @param idm The 8-byte IDM of the target card
     * @param areaNodes List of Area objects to authenticate
     * @param serviceNodes List of Service objects to authenticate
     * @param challenge1A Challenge1A (8 bytes) sent to the card for authentication
     */
    constructor(
        idm: ByteArray,
        areaNodes: List<Area>,
        serviceNodes: List<Service>,
        challenge1A: ByteArray,
    ) : this(
        idm,
        areaNodes.map { it.code }.toTypedArray(),
        serviceNodes.map { it.code }.toTypedArray(),
        challenge1A,
    )

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = areaCodes.size + serviceCodes.size

    override fun responseFromByteArray(data: ByteArray) =
        Authentication1DesResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            COMMAND_CODE,
            idm,
            capacity =
                BASE_LENGTH + 2 + (areaCodes.size * 2) + (serviceCodes.size * 2) + challenge1A.size,
        ) {
            addByte(areaCodes.size)
            areaCodes.forEach { addBytes(it) }
            addByte(serviceCodes.size)
            serviceCodes.forEach { addBytes(it) }
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

                val numberOfServiceCodes = uByte()
                require(numberOfAreaCodes + numberOfServiceCodes > 0) {
                    "At least one area or service code must be specified"
                }
                require(numberOfAreaCodes + numberOfServiceCodes <= MAX_NODES) {
                    "Maximum $MAX_NODES nodes can be authenticated at once"
                }

                val serviceCodes = Array(numberOfServiceCodes) { bytes(2) }
                val challenge1A = bytes(8)

                Authentication1DesCommand(idm, areaCodes, serviceCodes, challenge1A)
            }
    }
}
