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

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Number of area codes (1 byte)
        data.add(areaCodes.size.toByte())

        // Area codes (2 bytes each)
        areaCodes.forEach { areaCode -> data.addAll(areaCode.toList()) }

        // Number of service codes (1 byte)
        data.add(serviceCodes.size.toByte())

        // Service codes (2 bytes each)
        serviceCodes.forEach { serviceCode -> data.addAll(serviceCode.toList()) }

        // Challenge1A (8 bytes)
        data.addAll(challenge1A.toList())

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x10
        override val COMMAND_CLASS: CommandClass = CommandClass.MUTUAL_AUTH

        const val MIN_LENGTH: Int =
            FelicaCommandWithIdm.BASE_LENGTH +
                1 +
                1 +
                8 // + area_count(1) + service_count(1) + challenge1A(8)
        const val MAX_NODES = 64

        /** Parse an Authentication 1 DES command from raw bytes */
        fun fromByteArray(data: ByteArray): Authentication1DesCommand {
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

            // Number of area codes (1 byte)
            val numberOfAreaCodes = data[offset].toInt() and 0xFF
            require(numberOfAreaCodes >= 0) {
                "Number of area codes must be non-negative, got $numberOfAreaCodes"
            }
            offset++

            // Area codes (2 bytes each)
            val areaCodes = mutableListOf<ByteArray>()
            repeat(numberOfAreaCodes) {
                require(offset + 2 <= data.size) {
                    "Data size insufficient for area code at offset $offset"
                }
                val areaCode = data.sliceArray(offset until offset + 2)
                areaCodes.add(areaCode)
                offset += 2
            }

            // Number of service codes (1 byte)
            val numberOfServiceCodes = data[offset].toInt() and 0xFF
            require(numberOfServiceCodes >= 0) {
                "Number of service codes must be non-negative, got $numberOfServiceCodes"
            }
            require(numberOfAreaCodes + numberOfServiceCodes > 0) {
                "At least one area or service code must be specified"
            }
            require(numberOfAreaCodes + numberOfServiceCodes <= MAX_NODES) {
                "Maximum $MAX_NODES nodes can be authenticated at once"
            }
            offset++

            // Service codes (2 bytes each)
            val serviceCodes = mutableListOf<ByteArray>()
            repeat(numberOfServiceCodes) {
                require(offset + 2 <= data.size) {
                    "Data size insufficient for service code at offset $offset"
                }
                val serviceCode = data.sliceArray(offset until offset + 2)
                serviceCodes.add(serviceCode)
                offset += 2
            }

            // Challenge1A (8 bytes)
            require(offset + 8 <= data.size) {
                "Data size insufficient for challenge1A at offset $offset"
            }
            val challenge1A = data.sliceArray(offset until offset + 8)

            return Authentication1DesCommand(
                idm,
                areaCodes.toTypedArray(),
                serviceCodes.toTypedArray(),
                challenge1A,
            )
        }
    }
}
