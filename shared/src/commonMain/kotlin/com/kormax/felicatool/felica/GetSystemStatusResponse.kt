package com.kormax.felicatool.felica

/**
 * Get System Status response received from FeliCa cards
 *
 * Contains the card's IDM, response status flags, and system status information.
 */
class GetSystemStatusResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag 1 from the response */
    override val statusFlag1: Byte,

    /** Status Flag 2 from the response */
    override val statusFlag2: Byte,
    val systemStatus: SystemStatus,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        require(systemStatus.toByteArray().size <= 256) {
            "System Status data must be at most 255 bytes for 1-byte length field"
        }
    }

    override fun toByteArray(): ByteArray {
        val systemStatusBytes = systemStatus.toByteArray()
        val responseData = systemStatusBytes.copyOfRange(1, systemStatusBytes.size)

        return buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity = BASE_LENGTH + 4 + responseData.size,
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            addByte(systemStatus.formatVersion)
            addByte(responseData.size)
            addBytes(responseData)
        }
    }

    companion object {
        const val RESPONSE_CODE: Short = 0x39
        const val MIN_LENGTH =
            BASE_LENGTH + 1 + 1 + 1 + 1 // + status1(1) + status2(1) + flag(1) + dataLength(1)

        /** Parse a Get System Status response from raw bytes */
        fun fromByteArray(data: ByteArray): GetSystemStatusResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()
                val formatVersion = byte()
                val dataLength = uByte()

                require(remaining() >= dataLength) {
                    "Insufficient data for response data: expected $dataLength bytes, but only ${remaining()} available"
                }

                val responseData = if (dataLength > 0) bytes(dataLength) else byteArrayOf()
                val systemStatus =
                    SystemStatus.fromByteArray(byteArrayOf(formatVersion) + responseData)

                GetSystemStatusResponse(idm, statusFlag1, statusFlag2, systemStatus)
            }
    }
}
