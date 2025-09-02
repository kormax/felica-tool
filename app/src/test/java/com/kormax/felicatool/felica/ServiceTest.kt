package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for Service */
class ServiceTest {

    companion object {
        // Test data from user specifications
        private val CJRC_SERVICES =
            listOf(
                    "4800",
                    "4a00",
                    "8800",
                    "8b00",
                    "c800",
                    "ca00",
                    "cc00",
                    "ce00",
                    "d000",
                    "d200",
                    "d400",
                    "d600",
                    "1008",
                    "1208",
                    "1608",
                    "5008",
                    "5208",
                    "5608",
                    "9008",
                    "9208",
                    "9608",
                    "c808",
                    "ca08",
                    "0a09",
                    "0c09",
                    "0f09",
                    "0810",
                    "0a10",
                    "4810",
                    "4a10",
                    "8c10",
                    "8f10",
                    "c810",
                    "cb10",
                    "0811",
                    "0a11",
                    "4811",
                    "4a11",
                    "0818",
                    "0a18",
                    "4818",
                    "4b18",
                    "8818",
                    "8b18",
                    "0819",
                    "0a19",
                    "4819",
                    "4b19",
                    "8819",
                    "8b19",
                    "081f",
                    "0a1f",
                    "0b1f",
                    "0823",
                    "0a23",
                    "4823",
                    "4b23",
                    "8823",
                    "8b23",
                    "c823",
                    "cb23",
                )
                .map { it.hexToByteArray() }

        private val COMMON_AREA_SERVICES =
            listOf(
                    "0810",
                    "0811",
                    "0a11",
                    "0b11",
                    "0812",
                    "1013",
                    "1213",
                    "1713",
                    "0814",
                    "0a14",
                    "0815",
                    "0a15",
                    "0816",
                    "0a16",
                    "0c17",
                    "0f17",
                    "8855",
                    "8b55",
                    "9055",
                    "9455",
                    "9755",
                    "c855",
                    "cb55",
                    "0856",
                    "0b56",
                    "4856",
                    "4b56",
                    "4c56",
                    "4f56",
                    "8856",
                    "8b56",
                    "c867",
                    "cb67",
                    "cc67",
                    "cf67",
                    "0868",
                    "0b68",
                    "0c68",
                    "0f68",
                    "1068",
                    "1468",
                    "1768",
                    "4868",
                    "4b68",
                    "4c68",
                    "4f68",
                    "8868",
                    "8b68",
                    "8c68",
                    "8f68",
                    "c868",
                    "cb68",
                    "cc68",
                    "cf68",
                )
                .map { it.hexToByteArray() }

        private val FELICA_CE_SERVICES =
            listOf("0810", "0820", "0880", "0890", "08a0", "08b0", "48b0").map {
                it.hexToByteArray()
            }
    }

    @Test
    fun testService_fromByteArray_basic() {
        // Test basic Service creation from bytes
        val service = Service.fromByteArray("0800".hexToByteArray())

        assertEquals(0, service.number)
        assertEquals(ServiceAttribute.RANDOM_RW_WITH_KEY, service.attribute)
        assertEquals(8.toShort(), service.getServiceCode())
    }

    @Test
    fun testService_toByteArray_basic() {
        // Test basic Service to bytes conversion
        val service = Service(0, ServiceAttribute.RANDOM_RW_WITH_KEY)
        val bytes = service.toByteArray()

        assertArrayEquals("0800".hexToByteArray(), bytes)
    }

    @Test
    fun testService_roundTrip() {
        // Test that fromByteArray and toByteArray are inverses
        val originalBytes = "0800".hexToByteArray()
        val service = Service.fromByteArray(originalBytes)
        val roundTripData = service.toByteArray()
        assertArrayEquals(originalBytes, roundTripData)
    }

    @Test
    fun testService_fromByteArray_allTestServices() {
        // Test all service collections for round-trip parsing
        val allTestServiceCollections =
            listOf(
                "CJRC_SERVICES" to CJRC_SERVICES,
                "COMMON_AREA_SERVICES" to COMMON_AREA_SERVICES,
                "FELICA_CE_SERVICES" to FELICA_CE_SERVICES,
            )

        allTestServiceCollections.forEach { (collectionName, services) ->
            services.forEach { data ->
                val service = Service.fromByteArray(data)
                val roundTripData = service.toByteArray()
                assertArrayEquals(
                    "Round trip failed for $collectionName data ${data.toHexString()}, parsed as $service ${service.toByteArray().toHexString()}",
                    data,
                    roundTripData,
                )
            }
        }
    }

    @Test
    fun testService_belongsTo_area() {
        // Test that services belong to appropriate areas
        val area = Area(0, AreaAttribute.CAN_CREATE_SUB_AREA, 1000, AreaAttribute.END_ROOT_AREA)

        // Services within the area range should belong to it
        val servicesInRange =
            listOf(
                Service(100, ServiceAttribute.RANDOM_RW_WITH_KEY),
                Service(500, ServiceAttribute.RANDOM_RW_WITH_KEY),
                Service(999, ServiceAttribute.RANDOM_RW_WITH_KEY),
            )

        servicesInRange.forEach { service ->
            assertTrue("Service ${service} should belong to area ${area}", service.belongsTo(area))
        }

        // Services outside the area range should not belong to it
        val servicesOutOfRange =
            listOf(
                Service(1001, ServiceAttribute.RANDOM_RW_WITH_KEY),
                Service(1022, ServiceAttribute.RANDOM_RW_WITH_KEY),
            )

        servicesOutOfRange.forEach { service ->
            assertFalse(
                "Service ${service} should not belong to area ${area}",
                service.belongsTo(area),
            )
        }
    }

    @Test
    fun testService_belongsTo_service() {
        // Test that services do not belong to other services
        val service1 = Service(100, ServiceAttribute.RANDOM_RW_WITH_KEY)
        val service2 = Service(200, ServiceAttribute.RANDOM_RW_WITH_KEY)

        assertFalse("Service should not belong to another service", service1.belongsTo(service2))
    }

    @Test
    fun testService_belongsTo_edgeCases() {
        // Test edge cases for service belongsTo
        val area = Area(100, AreaAttribute.CAN_CREATE_SUB_AREA, 200, AreaAttribute.END_SUB_AREA)

        // Service at the boundary should belong
        val boundaryService = Service(100, ServiceAttribute.RANDOM_RW_WITH_KEY)
        assertTrue(
            "Service at lower boundary should belong to area",
            boundaryService.belongsTo(area),
        )

        val upperBoundaryService = Service(200, ServiceAttribute.RANDOM_RW_WITH_KEY)
        assertTrue(
            "Service at upper boundary should belong to area",
            upperBoundaryService.belongsTo(area),
        )

        // Service just outside boundary should not belong
        val outsideService = Service(201, ServiceAttribute.RANDOM_RW_WITH_KEY)
        assertFalse(
            "Service just outside boundary should not belong to area",
            outsideService.belongsTo(area),
        )
    }
}
