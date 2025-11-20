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

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity = BASE_LENGTH + 4 + (areas.size * 4) + 1 + (services.size * 2),
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)
            addByte(if (continueFlag) 0x01 else 0x00)
            addByte(areas.size)
            areas.forEach { addBytes(it.fullCode) }
            addByte(services.size)
            services.forEach { addBytes(it.code) }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x1B
        const val MIN_LENGTH =
            BASE_LENGTH +
                1 +
                1 +
                1 +
                1 +
                1 // + status1(1) + status2(1) + continue_flag(1) + area_count(1) + service_count(1)

        /** Parse a Request Code List response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestCodeListResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()
                val continueFlag = byte() != 0x00.toByte()

                val areaCount = uByte()
                require(remaining() >= (areaCount * 4) + 1) {
                    "Insufficient data for $areaCount areas"
                }

                val areas = List(areaCount) { Area.fromByteArray(bytes(4)) }

                val serviceCount = uByte()
                require(remaining() >= serviceCount * 2) {
                    "Insufficient data for $serviceCount services"
                }

                val services = List(serviceCount) { Service.fromByteArray(bytes(2)) }

                RequestCodeListResponse(
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
