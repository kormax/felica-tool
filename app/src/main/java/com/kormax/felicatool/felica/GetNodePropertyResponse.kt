package com.kormax.felicatool.felica

/**
 * Get Node Property response received from FeliCa cards
 *
 * Contains the Node Property information for the requested Node Codes. The response structure
 * depends on the type of property requested.
 */
class GetNodePropertyResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag1 (see section 4.5 "Status Flag") */
    override val statusFlag1: Byte,

    /** Status Flag2 (see section 4.5 "Status Flag") */
    override val statusFlag2: Byte,

    /** Array of Node Properties for the requested nodes Empty array if Status Flag1 != 0x00 */
    val nodeProperties: Array<NodeProperty> = emptyArray(),
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        // If status is success, validate node properties
        if (statusFlag1 == 0x00.toByte()) {
            require(nodeProperties.isNotEmpty()) {
                "Node properties must not be empty for successful response"
            }
            require(nodeProperties.size <= 16) { "Maximum 16 node properties can be returned" }
        } else {
            // If status is error, node properties should be empty
            require(nodeProperties.isEmpty()) {
                "Node properties should be empty for error response"
            }
        }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity =
                if (statusFlag1 == 0x00.toByte()) {
                    MIN_SUCCESS_LENGTH + nodeProperties.sumOf { it.sizeBytes }
                } else {
                    MIN_ERROR_LENGTH
                },
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            if (statusFlag1 == 0x00.toByte()) {
                addByte(nodeProperties.size)
                nodeProperties.forEach { addBytes(it.toByteArray()) }
            }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x29
        const val MIN_ERROR_LENGTH = BASE_LENGTH + 2 // + status_flags(2)
        const val MIN_SUCCESS_LENGTH =
            BASE_LENGTH + 2 + 1 // + status_flags(2) + num_nodes(1) + min 1 property(1 byte)

        /** Parse a Get Node Property response from raw bytes */
        fun fromByteArray(data: ByteArray): GetNodePropertyResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_ERROR_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()

                if (statusFlag1 != 0x00.toByte()) {
                    return GetNodePropertyResponse(idm, statusFlag1, statusFlag2, emptyArray())
                }

                val numberOfNodes = uByte()
                require(numberOfNodes in 1..16) {
                    "Number of nodes must be between 1 and 16, got $numberOfNodes"
                }

                val remainingBytes = remaining()
                val nodeProperties =
                    when (remainingBytes) {
                        numberOfNodes * ValueLimitedPurseServiceProperty.SIZE_BYTES ->
                            List(numberOfNodes) {
                                ValueLimitedPurseServiceProperty.fromByteArray(
                                    bytes(ValueLimitedPurseServiceProperty.SIZE_BYTES)
                                )
                            }
                        numberOfNodes * MacCommunicationProperty.SIZE_BYTES ->
                            List(numberOfNodes) {
                                MacCommunicationProperty.fromByteArray(
                                    bytes(MacCommunicationProperty.SIZE_BYTES)
                                )
                            }
                        else ->
                            throw IllegalArgumentException(
                                "Cannot determine node property type from response data size. Expected ${numberOfNodes * ValueLimitedPurseServiceProperty.SIZE_BYTES} or ${numberOfNodes * MacCommunicationProperty.SIZE_BYTES} bytes, got $remainingBytes"
                            )
                    }

                GetNodePropertyResponse(
                    idm,
                    statusFlag1,
                    statusFlag2,
                    nodeProperties.toTypedArray(),
                )
            }
    }
}
