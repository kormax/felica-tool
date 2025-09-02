package com.kormax.felicatool.felica

/**
 * Request Block Information command used to acquire information about blocks in a service
 *
 * This command requests detailed information about blocks within specified services, including
 * block count, block attributes, and other block-related properties.
 */
class RequestBlockInformationCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /**
     * Array of Service Codes to request block information for. Each service code is 2 bytes in
     * Little Endian format.
     */
    val nodeCodes: Array<ByteArray>,
) : FelicaCommandWithIdm<RequestBlockInformationResponse>(idm) {

    init {
        require(nodeCodes.isNotEmpty()) { "At least one service code must be specified" }
        require(nodeCodes.size <= MAX_SERVICE_CODES) {
            "Maximum 16 service codes can be requested at once"
        }
        require(nodeCodes.all { it.size == 2 }) { "Each service code must be exactly 2 bytes" }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int = nodeCodes.size

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

    override fun responseFromByteArray(data: ByteArray) =
        RequestBlockInformationResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated
        data.add(0x00) // Placeholder

        // Command code
        data.add(COMMAND_CODE.toByte())

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Number of service codes (1 byte)
        data.add(nodeCodes.size.toByte())

        // Service codes (2 bytes each, Little Endian)
        nodeCodes.forEach { serviceCode -> data.addAll(serviceCode.toList()) }

        // Set the correct length
        data[0] = data.size.toByte()

        return data.toByteArray()
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x0E
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val MIN_LENGTH: Int =
            FelicaCommandWithIdm.BASE_LENGTH + 1 + 2 // + num_services(1) + min 1 service(2)
        const val MAX_SERVICE_CODES = 32

        /** Parse a Request Block Information command from raw bytes */
        fun fromByteArray(data: ByteArray): RequestBlockInformationCommand {
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
            require(data.size >= FelicaCommandWithIdm.BASE_LENGTH + 1 + numberOfServices * 2) {
                "Data size insufficient for $numberOfServices service codes"
            }
            offset++

            // Service codes (2 bytes each, Little Endian)
            val nodeCodes = mutableListOf<ByteArray>()
            repeat(numberOfServices) {
                val serviceCode = data.sliceArray(offset until offset + 2)
                nodeCodes.add(serviceCode)
                offset += 2
            }

            return RequestBlockInformationCommand(idm, nodeCodes.toTypedArray())
        }
    }
}
