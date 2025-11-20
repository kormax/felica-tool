package com.kormax.felicatool.felica

/**
 * Get Area Information command (preliminary name - no sources available)
 *
 * This command retrieves information about a specific area on the FeliCa card. The exact purpose
 * and functionality are not well documented, hence the preliminary naming.
 */
class GetAreaInformationCommand(
    /** The 8-byte IDM of the target card (obtained from polling) */
    idm: ByteArray,

    /** The 2-byte node code (area code) to request information for */
    val nodeCode: ByteArray,
) : FelicaCommandWithIdm<GetAreaInformationResponse>(idm) {

    init {
        require(nodeCode.size == 2) { "Node code must be exactly 2 bytes" }
    }

    /**
     * Alternative constructor that accepts a Node object and extracts its code
     *
     * @param idm The 8-byte IDM of the target card
     * @param node Node object to request area information for
     */
    constructor(idm: ByteArray, node: Node) : this(idm, node.code)

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        GetAreaInformationResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = COMMAND_LENGTH) { addBytes(nodeCode) }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x24
        override val COMMAND_CLASS: CommandClass = CommandClass.FIXED_RESPONSE_TIME

        const val COMMAND_LENGTH: Int = BASE_LENGTH + 2 // + node_code(2)

        /** Parse a Get Area Information command from raw bytes */
        fun fromByteArray(data: ByteArray): GetAreaInformationCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = COMMAND_LENGTH) { idm ->
                GetAreaInformationCommand(idm, bytes(2))
            }
    }
}
