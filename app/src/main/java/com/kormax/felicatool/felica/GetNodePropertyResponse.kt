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

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val baseLength = FelicaResponseWithIdm.BASE_LENGTH + 2 // + status flags

        // For error responses, only include base data and status flags
        if (statusFlag1 != 0x00.toByte()) {
            val data = ByteArray(baseLength)
            var offset = 0

            data[offset++] = baseLength.toByte()
            data[offset++] = RESPONSE_CODE
            idm.copyInto(data, offset)
            offset += 8
            data[offset++] = statusFlag1
            data[offset++] = statusFlag2

            return data
        }

        // Success response
        val propertiesDataSize = nodeProperties.sumOf { it.sizeBytes }
        val totalLength =
            baseLength + 1 + propertiesDataSize // + number_of_nodes(1) + properties_data

        val data = ByteArray(totalLength)
        var offset = 0

        // Length (1 byte)
        data[offset++] = totalLength.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Status flags (2 bytes)
        data[offset++] = statusFlag1
        data[offset++] = statusFlag2

        // Number of nodes (1 byte)
        data[offset++] = nodeProperties.size.toByte()

        // Node properties
        nodeProperties.forEach { property ->
            val propertyBytes = property.toByteArray()
            propertyBytes.copyInto(data, offset)
            offset += propertyBytes.size
        }

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x29
        const val MIN_ERROR_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 2 // + status_flags(2)
        const val MIN_SUCCESS_LENGTH =
            FelicaResponseWithIdm.BASE_LENGTH +
                2 +
                1 +
                1 // + status_flags(2) + num_nodes(1) + min 1 property(1 byte)

        /** Parse a Get Node Property response from raw bytes */
        fun fromByteArray(data: ByteArray): GetNodePropertyResponse {
            require(data.size >= MIN_ERROR_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_ERROR_LENGTH required"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Response code (1 byte)
            val responseCode = data[offset]
            require(responseCode == RESPONSE_CODE) {
                "Invalid response code: expected $RESPONSE_CODE, got $responseCode"
            }
            offset++

            // IDM (8 bytes)
            val idm = data.sliceArray(offset until offset + 8)
            offset += 8

            // Status flags (2 bytes)
            val statusFlag1 = data[offset++]
            val statusFlag2 = data[offset++]

            // If status indicates error, return early
            if (statusFlag1 != 0x00.toByte()) {
                return GetNodePropertyResponse(idm, statusFlag1, statusFlag2, emptyArray())
            }

            // Success case - parse remaining fields
            require(data.size >= MIN_SUCCESS_LENGTH) {
                "Success response data too short: ${data.size} bytes, minimum $MIN_SUCCESS_LENGTH required"
            }

            // Number of nodes (1 byte)
            val numberOfNodes = data[offset++].toInt() and 0xFF
            require(numberOfNodes in 1..16) {
                "Number of nodes must be between 1 and 16, got $numberOfNodes"
            }

            // Parse node properties - size depends on property type
            // We need to determine the property type from the command that was sent,
            // but since we only have the response, we'll try to infer it from the data size
            val remainingBytes = data.size - offset
            val nodeProperties = mutableListOf<NodeProperty>()

            // Try to parse as Value-Limited Purse Service first (10 bytes per property)
            if (remainingBytes == numberOfNodes * ValueLimitedPurseServiceProperty.SIZE_BYTES) {
                repeat(numberOfNodes) {
                    val propertyData =
                        data.sliceArray(
                            offset until offset + ValueLimitedPurseServiceProperty.SIZE_BYTES
                        )
                    val property = ValueLimitedPurseServiceProperty.fromByteArray(propertyData)
                    nodeProperties.add(property)
                    offset += ValueLimitedPurseServiceProperty.SIZE_BYTES
                }
            }
            // Try to parse as MAC Communication (1 byte per property)
            else if (remainingBytes == numberOfNodes * MacCommunicationProperty.SIZE_BYTES) {
                repeat(numberOfNodes) {
                    val propertyData =
                        data.sliceArray(offset until offset + MacCommunicationProperty.SIZE_BYTES)
                    val property = MacCommunicationProperty.fromByteArray(propertyData)
                    nodeProperties.add(property)
                    offset += MacCommunicationProperty.SIZE_BYTES
                }
            } else {
                throw IllegalArgumentException(
                    "Cannot determine node property type from response data size. Expected ${numberOfNodes * ValueLimitedPurseServiceProperty.SIZE_BYTES} or ${numberOfNodes * MacCommunicationProperty.SIZE_BYTES} bytes, got $remainingBytes"
                )
            }

            return GetNodePropertyResponse(
                idm,
                statusFlag1,
                statusFlag2,
                nodeProperties.toTypedArray(),
            )
        }
    }
}
