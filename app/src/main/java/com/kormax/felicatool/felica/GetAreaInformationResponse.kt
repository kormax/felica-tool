package com.kormax.felicatool.felica

/**
 * Get Area Information response received from FeliCa cards
 *
 * Contains the card's IDM and area information including status flags, node code, and unknown data.
 * Status flags indicate different conditions:
 * - FF E0 for area 0
 * - FF E7 if two higher bits of first byte of the code are high
 * - FF E2 for codes that don't represent areas
 */
class GetAreaInformationResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag1 (see section 4.5 "Status Flag") */
    override val statusFlag1: Byte,

    /** Status Flag2 (see section 4.5 "Status Flag") */
    override val statusFlag2: Byte,

    /** The 2-byte node code (only present if status is success) */
    val nodeCode: ByteArray = ByteArray(0),

    /** Data field with unknown meaning (2 bytes, only present if status is success) */
    val data: ByteArray = ByteArray(0),
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        // If status is success, nodeCode and data should be present
        if (statusFlag1 == 0x00.toByte()) {
            require(nodeCode.size == 2) {
                "Node code must be exactly 2 bytes for successful response"
            }
            require(data.size == 2) { "Data must be exactly 2 bytes for successful response" }
        } else {
            // If status is error, nodeCode and data should be empty
            require(nodeCode.isEmpty()) { "Node code should be empty for error response" }
            require(data.isEmpty()) { "Data should be empty for error response" }
        }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(RESPONSE_CODE, idm, capacity = MIN_SUCCESS_LENGTH) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            if (statusFlag1 == 0x00.toByte()) {
                addBytes(nodeCode)
                addBytes(data)
            }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x25
        const val MIN_ERROR_LENGTH = BASE_LENGTH + 2 // + status_flags(2)
        const val MIN_SUCCESS_LENGTH =
            BASE_LENGTH + 2 + 2 + 2 // + status_flags(2) + node_code(2) + data(2)

        /** Parse a Get Area Information response from raw bytes */
        fun fromByteArray(data: ByteArray): GetAreaInformationResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_ERROR_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()

                if (statusFlag1 != 0x00.toByte()) {
                    return GetAreaInformationResponse(idm, statusFlag1, statusFlag2)
                }

                require(remaining() == 4) {
                    "Success response data must contain node code and data (4 bytes), got ${remaining()}"
                }

                val nodeCode = bytes(2)
                val responseData = bytes(2)

                GetAreaInformationResponse(idm, statusFlag1, statusFlag2, nodeCode, responseData)
            }
    }
}
