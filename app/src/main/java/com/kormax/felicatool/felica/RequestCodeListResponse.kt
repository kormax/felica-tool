package com.kormax.felicatool.felica

/**
 * Request Code List response received from FeliCa cards
 *
 * Contains the card's IDM and a comprehensive list of all nodes (systems, areas, services)
 * available on the card, providing a complete overview of the card's structure.
 */
class RequestCodeListResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag 1 from the response */
    override val statusFlag1: Byte,

    /** Status Flag 2 from the response */
    override val statusFlag2: Byte,

    /** Continue flag indicating if there are more packets to follow */
    val continueFlag: Boolean,

    /** List of area codes found (parsed Area objects) */
    val areas: List<Area>,

    /** List of service codes found (parsed Service objects) */
    val services: List<Service>,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        // Calculate total data size
        val totalAreaBytes = areas.size * 4
        val totalServiceBytes = services.size * 2

        val length =
            FelicaResponseWithIdm.BASE_LENGTH +
                1 +
                1 +
                1 +
                1 +
                totalAreaBytes +
                1 +
                totalServiceBytes
        val data = ByteArray(length)
        var offset = 0

        // Length (1 byte)
        data[offset++] = length.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Status Flag 1 (1 byte)
        data[offset++] = statusFlag1

        // Status Flag 2 (1 byte)
        data[offset++] = statusFlag2

        // Continue flag (1 byte)
        data[offset++] = if (continueFlag) 0x01 else 0x00

        // Area count (1 byte)
        data[offset++] = areas.size.toByte()

        // Areas (each 4 bytes)
        areas.forEach { area ->
            area.fullCode.copyInto(data, offset)
            offset += 4
        }

        // Service count (1 byte)
        data[offset++] = services.size.toByte()

        // Services (each 2 bytes)
        services.forEach { service ->
            service.code.copyInto(data, offset)
            offset += 2
        }

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x1B
        const val MIN_LENGTH =
            FelicaResponseWithIdm.BASE_LENGTH +
                1 +
                1 +
                1 +
                1 +
                1 // + status1(1) + status2(1) + continue_flag(1) + area_count(1) + service_count(1)

        /** Parse a Request Code List response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestCodeListResponse {
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

            // Status Flag 1 (1 byte)
            val statusFlag1 = data[offset]
            offset++

            // Status Flag 2 (1 byte)
            val statusFlag2 = data[offset]
            offset++

            // Continue flag (1 byte)
            val continueFlag = data[offset] != 0x00.toByte()
            offset++

            // Area count (1 byte)
            val areaCount = data[offset].toInt() and 0xFF
            offset++

            // Areas (each 4 bytes)
            val areas = mutableListOf<Area>()
            for (i in 0 until areaCount) {
                require(offset + 4 <= data.size) { "Insufficient data for area ${i}" }
                val areaData = data.sliceArray(offset until offset + 4)
                val area = Area.fromByteArray(areaData)
                areas.add(area)
                offset += 4
            }

            // Service count (1 byte)
            val serviceCount = data[offset].toInt() and 0xFF
            offset++

            // Services (each 2 bytes)
            val services = mutableListOf<Service>()
            for (i in 0 until serviceCount) {
                require(offset + 2 <= data.size) { "Insufficient data for service ${i}" }
                val serviceData = data.sliceArray(offset until offset + 2)
                val service = Service.fromByteArray(serviceData)
                services.add(service)
                offset += 2
            }

            // Verify we consumed all expected data
            require(offset == data.size) {
                "Data parsing incomplete: consumed $offset bytes out of ${data.size}"
            }

            return RequestCodeListResponse(
                idm,
                statusFlag1,
                statusFlag2,
                continueFlag,
                areas,
                services,
            )
        }
    }
}
