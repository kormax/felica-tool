package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for GetPlatformInformationResponse */
class GetPlatformInformationResponseTest {

    companion object {
        private val TEST_IDM = "01020304050607ff".hexToByteArray()
        private val ANOTHER_IDM = "1122334455667788".hexToByteArray()

        // Sample platform info from user example: 00000f1801020308475001d7007720250110
        // Status: 00 00, Length: 0f, Data: 1801020308475001d7007720250110
        private val SAMPLE_PLATFORM_DATA = "1801020308475001d7007720250110".hexToByteArray()
    }

    @Test
    fun testGetPlatformInformationResponse_successCreation() {
        val response =
            GetPlatformInformationResponse(
                idm = TEST_IDM,
                statusFlag1 = 0x00,
                statusFlag2 = 0x00,
                platformInformationData = SAMPLE_PLATFORM_DATA,
            )

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertArrayEquals(SAMPLE_PLATFORM_DATA, response.platformInformationData)
        assertTrue(response.success)
        assertEquals("01020304050607FF", response.idm.toHexString().uppercase())
    }

    @Test
    fun testGetPlatformInformationResponse_errorCreation() {
        val response =
            GetPlatformInformationResponse(
                idm = TEST_IDM,
                statusFlag1 = 0x01, // Error status
                statusFlag2 = 0x02,
                platformInformationData = ByteArray(0), // Empty for error
            )

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(0x01.toByte(), response.statusFlag1)
        assertEquals(0x02.toByte(), response.statusFlag2)
        assertArrayEquals(ByteArray(0), response.platformInformationData)
        assertFalse(response.success)
    }

    @Test
    fun testGetPlatformInformationResponse_toByteArray_success() {
        val response = GetPlatformInformationResponse(TEST_IDM, 0x00, 0x00, SAMPLE_PLATFORM_DATA)
        val bytes = response.toByteArray()

        // Check length: 1 (length) + 1 (response code) + 8 (IDM) + 2 (status) + 1 (data length) +
        // 15
        // (data) = 28
        val expectedLength = 1 + 1 + 8 + 2 + 1 + SAMPLE_PLATFORM_DATA.size
        assertEquals(expectedLength, bytes.size)
        assertEquals(expectedLength.toByte(), bytes[0])

        // Check response code
        assertEquals(GetPlatformInformationResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(0x00.toByte(), bytes[10]) // Status 1
        assertEquals(0x00.toByte(), bytes[11]) // Status 2

        // Check data length
        assertEquals(SAMPLE_PLATFORM_DATA.size.toByte(), bytes[12])

        for (i in SAMPLE_PLATFORM_DATA.indices) {
            assertEquals("Data byte $i", SAMPLE_PLATFORM_DATA[i], bytes[13 + i])
        }
    }

    @Test
    fun testGetPlatformInformationResponse_toByteArray_error() {
        val response =
            GetPlatformInformationResponse(
                TEST_IDM,
                0x01, // Error
                0x02,
                ByteArray(0),
            )
        val bytes = response.toByteArray()

        // Check length: 1 (length) + 1 (response code) + 8 (IDM) + 2 (status) = 12 (no data for
        // error)
        val expectedLength = 12
        assertEquals(expectedLength, bytes.size)
        assertEquals(expectedLength.toByte(), bytes[0])

        // Check response code
        assertEquals(GetPlatformInformationResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(0x01.toByte(), bytes[10]) // Status 1
        assertEquals(0x02.toByte(), bytes[11]) // Status 2
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetPlatformInformationResponse_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        GetPlatformInformationResponse(invalidIdm, 0x00, 0x00, SAMPLE_PLATFORM_DATA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetPlatformInformationResponse_successWithEmptyData() {
        // Success response should have data
        GetPlatformInformationResponse(TEST_IDM, 0x00, 0x00, ByteArray(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetPlatformInformationResponse_errorWithData() {
        // Error response should not have data
        GetPlatformInformationResponse(TEST_IDM, 0x01, 0x00, SAMPLE_PLATFORM_DATA)
    }

    @Test
    fun testFromByteArray_successResponse() {
        // Create a success response with the user's example data
        val response = GetPlatformInformationResponse(TEST_IDM, 0x00, 0x00, SAMPLE_PLATFORM_DATA)
        val data = response.toByteArray()

        val parsedResponse = GetPlatformInformationResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedResponse.idm)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag1)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag2)
        assertArrayEquals(SAMPLE_PLATFORM_DATA, parsedResponse.platformInformationData)
        assertTrue(parsedResponse.success)
    }

    @Test
    fun testFromByteArray_errorResponse() {
        // Create an error response
        val response = GetPlatformInformationResponse(TEST_IDM, 0x01, 0x02, ByteArray(0))
        val data = response.toByteArray()

        val parsedResponse = GetPlatformInformationResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedResponse.idm)
        assertEquals(0x01.toByte(), parsedResponse.statusFlag1)
        assertEquals(0x02.toByte(), parsedResponse.statusFlag2)
        assertArrayEquals(ByteArray(0), parsedResponse.platformInformationData)
        assertFalse(parsedResponse.success)
    }

    @Test
    fun testFromByteArray_userExampleData() {
        // Test with the user's example: 00000f1801020308475001d7007720250110
        // This represents: status1=00, status2=00, length=0f, data=1801020308475001d7007720250110
        // Full response: length + response_code + idm + status1 + status2 + length + data

        val fullResponseData =
            "1c3b01020304050607ff00000f1801020308475001d7007720250110".hexToByteArray()

        val parsedResponse = GetPlatformInformationResponse.fromByteArray(fullResponseData)

        assertArrayEquals(TEST_IDM, parsedResponse.idm)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag1)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag2)
        assertArrayEquals(SAMPLE_PLATFORM_DATA, parsedResponse.platformInformationData)
        assertTrue(parsedResponse.success)
    }

    @Test
    fun testFromByteArray_anotherIdm() {
        val response = GetPlatformInformationResponse(ANOTHER_IDM, 0x00, 0x00, SAMPLE_PLATFORM_DATA)
        val data = response.toByteArray()

        val parsedResponse = GetPlatformInformationResponse.fromByteArray(data)

        assertArrayEquals(ANOTHER_IDM, parsedResponse.idm)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag1)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag2)
        assertArrayEquals(SAMPLE_PLATFORM_DATA, parsedResponse.platformInformationData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val data = "0c3b".hexToByteArray() // Too short for minimum response
        GetPlatformInformationResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = "0d3b01020304050607ff0000".hexToByteArray() // Length says 13, but actual is 12
        GetPlatformInformationResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongResponseCode() {
        val data =
            "0c3a01020304050607ff0000"
                .hexToByteArray() // Wrong response code (0x3a instead of 0x3b)
        GetPlatformInformationResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_successWithWrongDataLength() {
        // Create data where the length byte doesn't match actual data length
        val wrongData = "1c3b01020304050607ff0000101801020308475001d7007720250110".hexToByteArray()
        // Length byte says 16 (0x10) but we only have 15 bytes of data
        GetPlatformInformationResponse.fromByteArray(wrongData)
    }

    @Test
    fun testConstants() {
        assertEquals(0x3b.toByte(), GetPlatformInformationResponse.RESPONSE_CODE.toByte())
        assertEquals(
            12,
            GetPlatformInformationResponse.MIN_LENGTH,
        ) // 1 (length) + 1 (response code) + 8 (IDM) + 2 (status)
        assertEquals(
            13,
            GetPlatformInformationResponse.MIN_SUCCESS_LENGTH,
        ) // MIN_LENGTH + 1 (data length)
    }

    @Test
    fun testRoundTrip_success() {
        val originalResponse =
            GetPlatformInformationResponse(ANOTHER_IDM, 0x00, 0x00, SAMPLE_PLATFORM_DATA)

        val bytes = originalResponse.toByteArray()
        val parsedResponse = GetPlatformInformationResponse.fromByteArray(bytes)

        assertArrayEquals(originalResponse.idm, parsedResponse.idm)
        assertEquals(originalResponse.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(originalResponse.statusFlag2, parsedResponse.statusFlag2)
        assertArrayEquals(
            originalResponse.platformInformationData,
            parsedResponse.platformInformationData,
        )
        assertArrayEquals(bytes, parsedResponse.toByteArray())
    }

    @Test
    fun testRoundTrip_error() {
        val originalResponse =
            GetPlatformInformationResponse(ANOTHER_IDM, 0xFF.toByte(), 0x01, ByteArray(0))

        val bytes = originalResponse.toByteArray()
        val parsedResponse = GetPlatformInformationResponse.fromByteArray(bytes)

        assertArrayEquals(originalResponse.idm, parsedResponse.idm)
        assertEquals(originalResponse.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(originalResponse.statusFlag2, parsedResponse.statusFlag2)
        assertArrayEquals(
            originalResponse.platformInformationData,
            parsedResponse.platformInformationData,
        )
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
            val response = GetPlatformInformationResponse(idm, 0x00, 0x00, SAMPLE_PLATFORM_DATA)
            val bytes = response.toByteArray()
            val parsed = GetPlatformInformationResponse.fromByteArray(bytes)

            assertArrayEquals("Failed for IDM: ${idm.toHexString()}", idm, parsed.idm)
            assertEquals(
                "Failed for status1 with IDM: ${idm.toHexString()}",
                0x00.toByte(),
                parsed.statusFlag1,
            )
            assertEquals(
                "Failed for status2 with IDM: ${idm.toHexString()}",
                0x00.toByte(),
                parsed.statusFlag2,
            )
            assertArrayEquals(
                "Failed for data with IDM: ${idm.toHexString()}",
                SAMPLE_PLATFORM_DATA,
                parsed.platformInformationData,
            )
        }
    }

    @Test
    fun testDifferentDataSizes() {
        val testDataSizes =
            listOf(
                ByteArray(0), // Edge case - but this should fail for success response
                byteArrayOf(0x01),
                byteArrayOf(0x01, 0x02),
                byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05),
                SAMPLE_PLATFORM_DATA,
            )

        for (testData in testDataSizes) {
            if (testData.isEmpty()) continue // Skip empty data for success response

            val response = GetPlatformInformationResponse(TEST_IDM, 0x00, 0x00, testData)
            val bytes = response.toByteArray()
            val parsed = GetPlatformInformationResponse.fromByteArray(bytes)

            assertArrayEquals(
                "Failed for data size ${testData.size}",
                testData,
                parsed.platformInformationData,
            )
        }
    }

    @Test
    fun testVariousStatusCodes() {
        val testStatuses =
            listOf(
                Pair(0x00.toByte(), 0x00.toByte()), // Success
                Pair(0x01.toByte(), 0x00.toByte()), // Error 1
                Pair(0x00.toByte(), 0x01.toByte()), // Error 2
                Pair(0xFF.toByte(), 0xFF.toByte()), // Error 3
            )

        for ((status1, status2) in testStatuses) {
            val hasData = (status1 == 0x00.toByte())
            val testData = if (hasData) SAMPLE_PLATFORM_DATA else ByteArray(0)

            val response = GetPlatformInformationResponse(TEST_IDM, status1, status2, testData)
            val bytes = response.toByteArray()
            val parsed = GetPlatformInformationResponse.fromByteArray(bytes)

            assertEquals(
                "Failed for status1 ${status1.toUByte().toString(16)}",
                status1,
                parsed.statusFlag1,
            )
            assertEquals(
                "Failed for status2 ${status2.toUByte().toString(16)}",
                status2,
                parsed.statusFlag2,
            )
            assertEquals(
                "Failed for success with status1 ${status1.toUByte().toString(16)}",
                hasData,
                parsed.success,
            )
        }
    }
}
