package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestCodeListResponse */
class RequestCodeListResponseTest {

    companion object {
        private const val IDM = "0123456789abcdef"
        private val AREA_1 =
            Area(0, AreaAttribute.CanCreateSubArea, 1023, AreaAttribute.EndRootArea)
        private val AREA_2 =
            Area(256, AreaAttribute.CanCreateSubArea, 511, AreaAttribute.EndRootArea)
        private val SERVICE_1 = Service(3, ServiceAttribute.RandomRwWithoutKey)
        private val SERVICE_2 = Service(252, ServiceAttribute.CyclicRwWithoutKey)
    }

    @Test
    fun testRequestCodeListResponse_creation_singleAreaAndService() {
        val idm = IDM.hexToByteArray()
        val areas = listOf(AREA_1)
        val services = listOf(SERVICE_1)
        val response = RequestCodeListResponse(idm, 0x00, 0x00, false, areas, services)

        assertArrayEquals(idm, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertFalse(response.continueFlag)
        assertEquals(1, response.areas.size)
        assertEquals(AREA_1, response.areas[0])
        assertEquals(1, response.services.size)
        assertEquals(SERVICE_1, response.services[0])
    }

    @Test
    fun testRequestCodeListResponse_creation_multipleAreasAndServices() {
        val idm = IDM.hexToByteArray()
        val areas = listOf(AREA_1, AREA_2)
        val services = listOf(SERVICE_1, SERVICE_2)
        val response = RequestCodeListResponse(idm, 0x00, 0x00, true, areas, services)

        assertArrayEquals(idm, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertTrue(response.continueFlag)
        assertEquals(2, response.areas.size)
        assertEquals(AREA_1, response.areas[0])
        assertEquals(AREA_2, response.areas[1])
        assertEquals(2, response.services.size)
        assertEquals(SERVICE_1, response.services[0])
        assertEquals(SERVICE_2, response.services[1])
    }

    @Test
    fun testRequestCodeListResponse_creation_emptyLists() {
        val idm = IDM.hexToByteArray()
        val areas = emptyList<Area>()
        val services = emptyList<Service>()
        val response = RequestCodeListResponse(idm, 0x00, 0x00, false, areas, services)

        assertArrayEquals(idm, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertFalse(response.continueFlag)
        assertTrue(response.areas.isEmpty())
        assertTrue(response.services.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestCodeListResponse_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        val areas = listOf(AREA_1)
        val services = listOf(SERVICE_1)
        RequestCodeListResponse(invalidIdm, 0x00, 0x00, false, areas, services)
    }

    @Test
    fun testRequestCodeListResponse_toByteArray_singleAreaAndService() {
        val idm = IDM.hexToByteArray()
        val areas = listOf(AREA_1)
        val services = listOf(SERVICE_1)
        val response = RequestCodeListResponse(idm, 0x00, 0x00, false, areas, services)
        val bytes = response.toByteArray()

        // Check length (1 + 1 + 8 + 1 + 1 + 1 + 1 + 4 + 1 + 2 = 21 bytes)
        assertEquals(21, bytes.size)
        assertEquals(21.toByte(), bytes[0]) // Length

        // Check response code
        assertEquals(RequestCodeListResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(0x00.toByte(), bytes[10]) // Status 1
        assertEquals(0x00.toByte(), bytes[11]) // Status 2

        // Check continue flag
        assertEquals(0x00.toByte(), bytes[12]) // No more packets

        // Check area count
        assertEquals(1.toByte(), bytes[13])

        // Check area data
        assertArrayEquals(AREA_1.fullCode, bytes.sliceArray(14..17))

        // Check service count
        assertEquals(1.toByte(), bytes[18])

        // Check service data
        assertArrayEquals(SERVICE_1.code, bytes.sliceArray(19..20))
    }

    @Test
    fun testRequestCodeListResponse_toByteArray_multipleAreasAndServices() {
        val idm = IDM.hexToByteArray()
        val areas = listOf(AREA_1, AREA_2)
        val services = listOf(SERVICE_1, SERVICE_2)
        val response = RequestCodeListResponse(idm, 0x00, 0x00, true, areas, services)
        val bytes = response.toByteArray()

        // Check length (1 + 1 + 8 + 1 + 1 + 1 + 1 + 8 + 1 + 4 = 27 bytes)
        assertEquals(27, bytes.size)
        assertEquals(27.toByte(), bytes[0]) // Length

        // Check response code
        assertEquals(RequestCodeListResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check continue flag
        assertEquals(0x01.toByte(), bytes[12]) // Has more packets

        // Check area count
        assertEquals(2.toByte(), bytes[13])

        // Check area data
        assertArrayEquals(AREA_1.fullCode, bytes.sliceArray(14..17))
        assertArrayEquals(AREA_2.fullCode, bytes.sliceArray(18..21))

        // Check service count
        assertEquals(2.toByte(), bytes[22])

        // Check service data
        assertArrayEquals(SERVICE_1.code, bytes.sliceArray(23..24))
        assertArrayEquals(SERVICE_2.code, bytes.sliceArray(25..26))
    }

    @Test
    fun testFromByteArray_singleAreaAndService() {
        // Length(1) + ResponseCode(1) + IDM(8) + Status1(1) + Status2(1) + Continue(1) +
        // AreaCount(1) +
        // Area(4) + ServiceCount(1) + Service(2) = 21 bytes
        val data =
            "151b${IDM}00000101${AREA_1.fullCode.toHexString()}01${SERVICE_1.code.toHexString()}"
                .hexToByteArray()

        val response = RequestCodeListResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertTrue(response.continueFlag)
        assertEquals(1, response.areas.size)
        assertEquals(AREA_1, response.areas[0])
        assertEquals(1, response.services.size)
        assertEquals(SERVICE_1, response.services[0])
    }

    @Test
    fun testFromByteArray_multipleAreasAndServices() {
        // Length(1) + ResponseCode(1) + IDM(8) + Status1(1) + Status2(1) + Continue(1) +
        // AreaCount(1) +
        // Areas(8) + ServiceCount(1) + Services(4) = 27 bytes
        val data =
            "1b1b${IDM}00000002${AREA_1.fullCode.toHexString()}${AREA_2.fullCode.toHexString()}02${SERVICE_1.code.toHexString()}${SERVICE_2.code.toHexString()}"
                .hexToByteArray()

        val response = RequestCodeListResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertFalse(response.continueFlag)
        assertEquals(2, response.areas.size)
        assertEquals(AREA_1, response.areas[0])
        assertEquals(AREA_2, response.areas[1])
        assertEquals(2, response.services.size)
        assertEquals(SERVICE_1, response.services[0])
        assertEquals(SERVICE_2, response.services[1])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val shortData =
            "141b${IDM}00000101${AREA_1.fullCode.toHexString()}01${SERVICE_1.code.toHexString().substring(0, 2)}"
                .hexToByteArray() // Missing 1 byte
        RequestCodeListResponse.fromByteArray(shortData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongResponseCode() {
        val wrongResponseData =
            "150e${IDM}00000101${AREA_1.fullCode.toHexString()}01${SERVICE_1.code.toHexString()}"
                .hexToByteArray() // Response code 0x0e instead of 0x0f
        RequestCodeListResponse.fromByteArray(wrongResponseData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val wrongLengthData =
            "161b${IDM}00000101${AREA_1.fullCode.toHexString()}01${SERVICE_1.code.toHexString()}"
                .hexToByteArray() // Length says 22 but data is 21 bytes
        RequestCodeListResponse.fromByteArray(wrongLengthData)
    }

    @Test
    fun testRequestCodeListResponse_roundTrip_single() {
        val idm = IDM.hexToByteArray()
        val areas = listOf(AREA_1)
        val services = listOf(SERVICE_1)
        val response = RequestCodeListResponse(idm, 0x00, 0x00, false, areas, services)
        val bytes = response.toByteArray()
        val parsedResponse = RequestCodeListResponse.fromByteArray(bytes)

        assertArrayEquals(response.idm, parsedResponse.idm)
        assertEquals(response.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(response.statusFlag2, parsedResponse.statusFlag2)
        assertEquals(response.continueFlag, parsedResponse.continueFlag)
        assertEquals(response.areas.size, parsedResponse.areas.size)
        for (i in response.areas.indices) {
            assertEquals(response.areas[i], parsedResponse.areas[i])
        }
        assertEquals(response.services.size, parsedResponse.services.size)
        for (i in response.services.indices) {
            assertEquals(response.services[i], parsedResponse.services[i])
        }
    }

    @Test
    fun testRequestCodeListResponse_roundTrip_multiple() {
        val idm = IDM.hexToByteArray()
        val areas = listOf(AREA_1, AREA_2)
        val services = listOf(SERVICE_1, SERVICE_2)
        val response = RequestCodeListResponse(idm, 0x00, 0x00, true, areas, services)
        val bytes = response.toByteArray()
        val parsedResponse = RequestCodeListResponse.fromByteArray(bytes)

        assertArrayEquals(response.idm, parsedResponse.idm)
        assertEquals(response.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(response.statusFlag2, parsedResponse.statusFlag2)
        assertEquals(response.continueFlag, parsedResponse.continueFlag)
        assertEquals(response.areas.size, parsedResponse.areas.size)
        for (i in response.areas.indices) {
            assertEquals(response.areas[i], parsedResponse.areas[i])
        }
        assertEquals(response.services.size, parsedResponse.services.size)
        for (i in response.services.indices) {
            assertEquals(response.services[i], parsedResponse.services[i])
        }
    }
}
