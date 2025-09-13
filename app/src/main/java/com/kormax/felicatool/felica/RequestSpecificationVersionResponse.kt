package com.kormax.felicatool.felica

/**
 * Request Specification Version response received from FeliCa cards
 *
 * Contains the card's OS version information including basic version and option versions.
 */
class RequestSpecificationVersionResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag1 - indicates success or error location */
    override val statusFlag1: Byte,

    /** Status Flag2 - indicates detailed error information */
    override val statusFlag2: Byte,

    /** Specification version information (only present if statusFlag1 == 0x00) */
    val specificationVersion: SpecificationVersion? = null,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated at the end
        data.add(0x00) // placeholder

        // Response code (1 byte)
        data.add(RESPONSE_CODE)

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Status Flag1 (1 byte)
        data.add(statusFlag1)

        // Status Flag2 (1 byte)
        data.add(statusFlag2)

        // Specification Version fields (if successful)
        if (statusFlag1 == 0x00.toByte() && specificationVersion != null) {
            data.addAll(specificationVersion.toByteArray().toList())
        }

        // Update length
        val length = data.size
        data[0] = length.toByte()

        return data.toByteArray()
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x3D
        const val MIN_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 2 // + status_flags(2)

        /** Parse a Request Specification Version response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestSpecificationVersionResponse {
            require(data.size >= MIN_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_LENGTH required"
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

            // Status Flag1 (1 byte)
            val statusFlag1 = data[offset++]

            // Status Flag2 (1 byte)
            val statusFlag2 = data[offset++]

            // Parse specification version fields if successful and data remains
            var specificationVersion: SpecificationVersion? = null

            if (statusFlag1 == 0x00.toByte() && offset < data.size) {
                specificationVersion = SpecificationVersion.fromByteArray(data, offset)
            }

            return RequestSpecificationVersionResponse(
                idm,
                statusFlag1,
                statusFlag2,
                specificationVersion,
            )
        }
    }
}
