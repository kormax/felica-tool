package com.kormax.felicatool.felica

/**
 * Set Parameter command for FeliCa cards This command may only be issuable to 'container' system
 */
class SetParameterCommand(
    /** Card IDM (8 bytes) */
    idm: ByteArray,

    /** Encryption type (SRM Type) */
    val encryptionType: EncryptionType = EncryptionType.SRM_TYPE1,

    /** Packet type (node code size) */
    val packetType: PacketType = PacketType.NODECODESIZE_2,

    /** Reserved bytes D0-D3 (4 bytes, must be all 0x00) */
    val reservedD0D1D2D3: ByteArray = ByteArray(4),

    /** Reserved bytes D6-D7 (2 bytes, must be all 0x00) */
    val reservedD6D7D: ByteArray = ByteArray(2),
) : FelicaCommandWithIdm<SetParameterResponse>(idm) {

    init {
        require(reservedD0D1D2D3.size == 4) { "Reserved D0-D3 must be exactly 4 bytes" }
        require(reservedD0D1D2D3.all { it == 0x00.toByte() }) {
            "Reserved D0-D3 must be all 0x00, got: ${reservedD0D1D2D3.toHexString()}"
        }
        require(reservedD6D7D.size == 2) { "Reserved D6-D7 must be exactly 2 bytes" }
        require(reservedD6D7D.all { it == 0x00.toByte() }) {
            "Reserved D6-D7 must be all 0x00, got: ${reservedD6D7D.toHexString()}"
        }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) = SetParameterResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(COMMAND_CODE, idm, capacity = COMMAND_LENGTH) {
            addBytes(reservedD0D1D2D3)
            addByte(encryptionType.value)
            addByte(packetType.value)
            addBytes(reservedD6D7D)
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x20
        override val COMMAND_CLASS: CommandClass = CommandClass.OTHER

        private const val RESERVED: Byte = 0x0
        const val COMMAND_LENGTH: Int =
            BASE_LENGTH + 8 // + reserved(4) + encryption_type(1) + packet_type(1) + reserved(2)

        /** Parse a SetParameter command from raw bytes */
        fun fromByteArray(data: ByteArray): SetParameterCommand =
            parseFelicaCommandWithIdm(data, COMMAND_CODE, minLength = COMMAND_LENGTH) { idm ->
                val reservedD0D1D2D3 = bytes(4)
                require(reservedD0D1D2D3.all { it == RESERVED }) {
                    "Reserved bytes D0-D3 must be 0x00, got: ${reservedD0D1D2D3.toHexString()}"
                }

                val encryptionTypeByte = uByte()
                val encryptionType = EncryptionType.fromValue(encryptionTypeByte)

                val packetTypeByte = uByte()
                val packetType = PacketType.fromValue(packetTypeByte)

                val reservedD6D7 = bytes(2)
                require(reservedD6D7.all { it == RESERVED }) {
                    "Reserved bytes D6-D7 must be 0x00, got: ${reservedD6D7.toHexString()}"
                }

                SetParameterCommand(idm, encryptionType, packetType, reservedD0D1D2D3, reservedD6D7)
            }
    }

    enum class EncryptionType(val value: Int) {
        SRM_TYPE1(0x00),
        SRM_TYPE2(0x01);

        // There could be something interesting here, but I did not bother

        companion object {
            fun fromValue(value: Int): EncryptionType =
                entries.find { it.value == value }
                    ?: throw IllegalArgumentException("Invalid encryption type: $value")
        }
    }

    enum class PacketType(val value: Int) {
        NODECODESIZE_2(0x0),
        NODECODESIZE_4(0x1);

        companion object {
            fun fromValue(value: Int): PacketType =
                entries.find { it.value == value }
                    ?: throw IllegalArgumentException("Invalid packet type: $value")
        }
    }
}
