package com.kormax.felicatool.felica

/**
 * Polling response received from FeliCa cards Contains the card's IDM and other identification
 * information
 */
class PollingResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /**
     * PMm (Parameter of card) - 8 bytes containing card parameters Contains information about the
     * card's capabilities and performance
     */
    val pmm: ByteArray,

    /**
     * Request data (2 bytes, optional) - interpretation depends on the request code used
     * - For System Code request (0x01): Contains the system code
     * - For Communication Performance request (0x02): Contains communication capabilities
     * - For No request (0x00): This field is null
     */
    val requestData: ByteArray? = null,
) : FelicaResponseWithIdm(idm) {

    init {
        require(pmm.size == 8) { "PMm must be exactly 8 bytes" }
        requestData?.let { require(it.size == 2) { "Request data must be exactly 2 bytes" } }
    }

    /**
     * Get system code from request data Only valid if the original request was for system code
     * (RequestCode.SYSTEM_CODE_REQUEST)
     *
     * @throws IllegalStateException if request data is not present
     */
    val systemCode: ByteArray
        get() =
            requestData
                ?: throw IllegalStateException(
                    "No request data present - system code not available"
                )

    /**
     * Get communication performance from request data Only valid if the original request was for
     * communication performance (RequestCode.COMMUNICATION_PERFORMANCE_REQUEST)
     *
     * @throws IllegalStateException if request data is not present
     */
    val communicationPerformance: CommunicationPerformance
        get() {
            val data =
                requestData
                    ?: throw IllegalStateException(
                        "No request data present - communication performance not available"
                    )
            return CommunicationPerformance.fromByteArray(data)
        }

    /** Check if request data is present */
    val hasRequestData: Boolean
        get() = requestData != null

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val length = if (requestData != null) LENGTH_WITH_REQUEST_DATA else MIN_LENGTH
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // PMm (8 bytes)
        pmm.copyInto(data, offset)
        offset += 8

        // Request data (2 bytes, optional)
        requestData?.copyInto(data, offset)

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x01
        const val MIN_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 8 // + PMm(8)
        const val LENGTH_WITH_REQUEST_DATA = MIN_LENGTH + 2 // + Request data(2)

        /** Parse a polling response from raw bytes */
        fun fromByteArray(data: ByteArray): PollingResponse {
            require(data.size >= MIN_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_LENGTH required"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
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

            // PMm (8 bytes)
            val pmm = data.sliceArray(offset until offset + 8)
            offset += 8

            // Request data (2 bytes, optional)
            val requestData =
                if (offset + 2 <= data.size) {
                    data.sliceArray(offset until offset + 2)
                } else null

            return PollingResponse(idm, pmm, requestData)
        }
    }
}
