package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for PollingResponse */
class PollingResponseTest {

    companion object {
        private val IDM_F14011111111B660 = "f14011111111b660".hexToByteArray()
        private val IDM_014011111111B660 = "014011111111b660".hexToByteArray()
        private val IDM_014022222222B660 = "014022222222b660".hexToByteArray()
        private val PMM_01188B868FBECBFF = "01188b868fbecbff".hexToByteArray()
        private val REQUEST_DATA_FE00 = "fe00".hexToByteArray()
        private val REQUEST_DATA_FE0F = "fe0f".hexToByteArray()
        private val REQUEST_DATA_0083 = "0083".hexToByteArray()
        private val REQUEST_DATA_0003 = "0003".hexToByteArray()

        // Generic test values
        private val TEST_IDM = "0102030405060708".hexToByteArray()
        private val TEST_PMM = "1112131415161718".hexToByteArray()
        private val TEST_SYSTEM_CODE_FE0F = "fe0f".hexToByteArray()
        private val TEST_SYSTEM_CODE_8008 = "8008".hexToByteArray()
    }

    @Test
    fun testPollingResponse_creation() {
        val response =
            PollingResponse(IDM_F14011111111B660, PMM_01188B868FBECBFF, REQUEST_DATA_FE00)

        assertArrayEquals(IDM_F14011111111B660, response.idm)
        assertArrayEquals(PMM_01188B868FBECBFF, response.pmm)
        assertArrayEquals(REQUEST_DATA_FE00, response.requestData)
        assertTrue(response.hasRequestData)
    }

    @Test
    fun testPollingResponse_noRequestData() {
        val response = PollingResponse(IDM_F14011111111B660, PMM_01188B868FBECBFF, null)

        assertArrayEquals(IDM_F14011111111B660, response.idm)
        assertArrayEquals(PMM_01188B868FBECBFF, response.pmm)
        assertNull(response.requestData)
        assertFalse(response.hasRequestData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPollingResponse_invalidIdmSize() {
        val invalidIdm = "010203".hexToByteArray() // Too short
        val pmm = TEST_PMM
        PollingResponse(invalidIdm, pmm, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPollingResponse_invalidPmmSize() {
        val idm = TEST_IDM
        val invalidPmm = "111213".hexToByteArray() // Too short
        PollingResponse(idm, invalidPmm, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPollingResponse_invalidRequestDataSize() {
        val idm = TEST_IDM
        val pmm = TEST_PMM
        val invalidRequestData = "01".hexToByteArray() // Too short
        PollingResponse(idm, pmm, invalidRequestData)
    }

    @Test
    fun testFromByteArray_noRequestData() {
        // 1201f14011111111b66001188b868fbecbff
        val data = "1201f14011111111b66001188b868fbecbff".hexToByteArray()

        val response = PollingResponse.fromByteArray(data)

        assertArrayEquals(IDM_F14011111111B660, response.idm)
        assertArrayEquals(PMM_01188B868FBECBFF, response.pmm)
        assertNull(response.requestData)
        assertFalse(response.hasRequestData)
    }

    @Test
    fun testFromByteArray_variousCombinations() {
        val testCases =
            listOf(
                // (expectedResponse, hexData)
                PollingResponse(IDM_014011111111B660, PMM_01188B868FBECBFF, REQUEST_DATA_FE00) to
                    "1401014011111111b66001188b868fbecbfffe00",
                PollingResponse(IDM_F14011111111B660, PMM_01188B868FBECBFF, REQUEST_DATA_0083) to
                    "1401f14011111111b66001188b868fbecbff0083",
                PollingResponse(IDM_014011111111B660, PMM_01188B868FBECBFF, REQUEST_DATA_0083) to
                    "1401014011111111b66001188b868fbecbff0083",
                PollingResponse(IDM_F14011111111B660, PMM_01188B868FBECBFF, REQUEST_DATA_FE0F) to
                    "1401f14011111111b66001188b868fbecbfffe0f",
                PollingResponse(IDM_014022222222B660, PMM_01188B868FBECBFF, REQUEST_DATA_0003) to
                    "1401014022222222b66001188b868fbecbff0003",
            )

        testCases.forEach { (expectedResponse, hexData) ->
            val data = hexData.hexToByteArray()
            val actualResponse = PollingResponse.fromByteArray(data)

            assertArrayEquals("IDM mismatch for $hexData", expectedResponse.idm, actualResponse.idm)
            assertArrayEquals("PMM mismatch for $hexData", expectedResponse.pmm, actualResponse.pmm)
            assertArrayEquals(
                "Request data mismatch for $hexData",
                expectedResponse.requestData,
                actualResponse.requestData,
            )
            assertEquals(
                "hasRequestData mismatch for $hexData",
                expectedResponse.hasRequestData,
                actualResponse.hasRequestData,
            )

            if (expectedResponse.hasRequestData) {
                assertEquals(
                    "System code mismatch for $hexData",
                    expectedResponse.systemCode.toHexString().uppercase(),
                    actualResponse.systemCode.toHexString().uppercase(),
                )
            }
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_dataTooShort() {
        val data = "10".hexToByteArray() // Too short
        PollingResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_invalidResponseCode() {
        val data = "11020102030405060708".hexToByteArray() // Wrong response code
        PollingResponse.fromByteArray(data)
    }

    @Test
    fun testGetSystemCode() {
        val response = PollingResponse(TEST_IDM, TEST_PMM, TEST_SYSTEM_CODE_FE0F)

        assertArrayEquals(TEST_SYSTEM_CODE_FE0F, response.systemCode)
        assertEquals("FE0F", response.systemCode.toHexString().uppercase())
    }

    @Test(expected = IllegalStateException::class)
    fun testGetSystemCode_noRequestData() {
        val response = PollingResponse(TEST_IDM, TEST_PMM, null)

        response.systemCode // Should throw
    }
}
