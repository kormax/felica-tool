package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for ResetModeResponse */
class ResetModeResponseTest {

    companion object {
        private val TEST_IDM = "01020304050607ff".hexToByteArray()
        private val ANOTHER_IDM = "1122334455667788".hexToByteArray()
    }

    @Test
    fun testResetModeResponse_creation() {
        val response = ResetModeResponse(TEST_IDM, 0x00.toByte(), 0x00.toByte())

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertTrue(response.isStatusSuccessful)
        assertEquals("01020304050607FF", response.idm.toHexString().uppercase())
    }

    @Test
    fun testResetModeResponse_creationWithError() {
        val response = ResetModeResponse(TEST_IDM, 0x01.toByte(), 0x02.toByte())

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(0x01.toByte(), response.statusFlag1)
        assertEquals(0x02.toByte(), response.statusFlag2)
        assertFalse(response.isStatusSuccessful)
    }

    @Test
    fun testResetModeResponse_toByteArray() {
        val response = ResetModeResponse(TEST_IDM, 0x00.toByte(), 0x00.toByte())
        val bytes = response.toByteArray()

        // Check length
        assertEquals(ResetModeResponse.RESPONSE_LENGTH, bytes.size)
        assertEquals(ResetModeResponse.RESPONSE_LENGTH.toByte(), bytes[0])

        // Check response code
        assertEquals(ResetModeResponse.RESPONSE_CODE, bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(0x00.toByte(), bytes[10])
        assertEquals(0x00.toByte(), bytes[11])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testResetModeResponse_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        ResetModeResponse(invalidIdm, 0x00.toByte(), 0x00.toByte())
    }

    @Test
    fun testFromByteArray_basic() {
        val originalResponse = ResetModeResponse(TEST_IDM, 0x00.toByte(), 0x00.toByte())
        val data = originalResponse.toByteArray()

        val parsedResponse = ResetModeResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedResponse.idm)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag1)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag2)
        assertTrue(parsedResponse.isStatusSuccessful)
    }

    @Test
    fun testFromByteArray_successResponse() {
        // 0c3f01020304050607ff0000 (length=12, response_code=0x3f, idm=01020304050607ff,
        // status1=00,
        // status2=00)
        val data = "0c3f01020304050607ff0000".hexToByteArray()

        val response = ResetModeResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertTrue(response.isStatusSuccessful)
    }

    @Test
    fun testFromByteArray_errorResponse() {
        // 0c3f01020304050607ff0102 (length=12, response_code=0x3f, idm=01020304050607ff,
        // status1=01,
        // status2=02)
        val data = "0c3f01020304050607ff0102".hexToByteArray()

        val response = ResetModeResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(0x01.toByte(), response.statusFlag1)
        assertEquals(0x02.toByte(), response.statusFlag2)
        assertFalse(response.isStatusSuccessful)
    }

    @Test
    fun testFromByteArray_anotherIdm() {
        // 0c3f11223344556677880000 (length=12, response_code=0x3f, idm=1122334455667788,
        // status1=00,
        // status2=00)
        val data = "0c3f11223344556677880000".hexToByteArray()

        val response = ResetModeResponse.fromByteArray(data)

        assertArrayEquals(ANOTHER_IDM, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertTrue(response.isStatusSuccessful)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val data = "0c3f".hexToByteArray() // Too short
        ResetModeResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = "0d3f01020304050607ff0000".hexToByteArray() // Length says 13, but actual is 12
        ResetModeResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongResponseCode() {
        val data =
            "0c3e01020304050607ff0000"
                .hexToByteArray() // Wrong response code (0x3e instead of 0x3f)
        ResetModeResponse.fromByteArray(data)
    }

    @Test
    fun testConstants() {
        assertEquals(0x3F.toByte(), ResetModeResponse.RESPONSE_CODE)
        assertEquals(
            12,
            ResetModeResponse.RESPONSE_LENGTH,
        ) // 1 (length) + 1 (response code) + 8 (IDM) + 1 (status1) + 1 (status2)
    }

    @Test
    fun testRoundTrip() {
        val originalResponse = ResetModeResponse(ANOTHER_IDM, 0x01.toByte(), 0x02.toByte())

        val bytes = originalResponse.toByteArray()
        val parsedResponse = ResetModeResponse.fromByteArray(bytes)

        assertArrayEquals(originalResponse.idm, parsedResponse.idm)
        assertEquals(originalResponse.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(originalResponse.statusFlag2, parsedResponse.statusFlag2)
        assertEquals(originalResponse.isStatusSuccessful, parsedResponse.isStatusSuccessful)
        assertArrayEquals(bytes, parsedResponse.toByteArray())
    }

    @Test
    fun testMultipleIdms() {
        val testIdms =
            listOf(
                "01020304050607ff".hexToByteArray(),
                "1122334455667788".hexToByteArray(),
                "0000000000000000".hexToByteArray(),
                "ffffffffffffffff".hexToByteArray(),
            )

        for (idm in testIdms) {
            val response = ResetModeResponse(idm, 0x00.toByte(), 0x00.toByte())
            val bytes = response.toByteArray()
            val parsed = ResetModeResponse.fromByteArray(bytes)

            assertArrayEquals("Failed for IDM: ${idm.toHexString()}", idm, parsed.idm)
            assertEquals(
                "Failed for statusFlag1 with IDM: ${idm.toHexString()}",
                0x00.toByte(),
                parsed.statusFlag1,
            )
            assertEquals(
                "Failed for statusFlag2 with IDM: ${idm.toHexString()}",
                0x00.toByte(),
                parsed.statusFlag2,
            )
            assertTrue(
                "Failed for isStatusSuccessful with IDM: ${idm.toHexString()}",
                parsed.isStatusSuccessful,
            )
        }
    }

    @Test
    fun testisStatusSuccessfulProperty() {
        // Test successful response
        val successResponse = ResetModeResponse(TEST_IDM, 0x00.toByte(), 0x00.toByte())
        assertTrue(successResponse.isStatusSuccessful)

        // Test various error responses
        val errorResponses =
            listOf(
                ResetModeResponse(TEST_IDM, 0x01.toByte(), 0x00.toByte()),
                ResetModeResponse(TEST_IDM, 0xFF.toByte(), 0x00.toByte()),
                ResetModeResponse(
                    TEST_IDM,
                    0x00.toByte(),
                    0x01.toByte(),
                ), // Note: statusFlag2 doesn't affect isStatusSuccessful
                ResetModeResponse(
                    TEST_IDM,
                    0x00.toByte(),
                    0xFF.toByte(),
                ), // Note: statusFlag2 doesn't affect isStatusSuccessful
            )

        for (response in errorResponses) {
            if (response.statusFlag1 == 0x00.toByte()) {
                assertTrue("Expected successful for statusFlag1=0x00", response.isStatusSuccessful)
            } else {
                assertFalse(
                    "Expected unsuccessful for statusFlag1=${response.statusFlag1}",
                    response.isStatusSuccessful,
                )
            }
        }
    }
}
