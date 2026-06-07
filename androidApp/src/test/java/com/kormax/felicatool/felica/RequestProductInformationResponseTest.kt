package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestProductInformationResponse */
class RequestProductInformationResponseTest {

    companion object {
        private val TEST_IDM = "01020304050607ff".hexToByteArray()
        private val ANOTHER_IDM = "1122334455667788".hexToByteArray()

        // Sample product info from user example: 00000f1801020308475001d7007720250110
        // Status: 00 00, Length: 0f, Data: 1801020308475001d7007720250110
        private val SAMPLE_PRODUCT_DATA = "1801020308475001d7007720250110".hexToByteArray()
    }

    @Test
    fun testRequestProductInformationResponse_successCreation() {
        val response =
            RequestProductInformationResponse(
                idm = TEST_IDM,
                statusFlag1 = 0x00,
                statusFlag2 = 0x00,
                productInformationData = SAMPLE_PRODUCT_DATA,
            )

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertArrayEquals(SAMPLE_PRODUCT_DATA, response.productInformationData)
        assertTrue(response.success)
        assertEquals("01020304050607FF", response.idm.toHexString().uppercase())
    }

    @Test
    fun testRequestProductInformationResponse_errorCreation() {
        val response =
            RequestProductInformationResponse(
                idm = TEST_IDM,
                statusFlag1 = 0x01, // Error status
                statusFlag2 = 0x02,
                productInformationData = ByteArray(0), // Empty for error
            )

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(0x01.toByte(), response.statusFlag1)
        assertEquals(0x02.toByte(), response.statusFlag2)
        assertArrayEquals(ByteArray(0), response.productInformationData)
        assertFalse(response.success)
    }

    @Test
    fun testRequestProductInformationResponse_toByteArray_success() {
        val response = RequestProductInformationResponse(TEST_IDM, 0x00, 0x00, SAMPLE_PRODUCT_DATA)
        val bytes = response.toByteArray()

        // Check length: 1 (length) + 1 (response code) + 8 (IDM) + 2 (status) + 1 (data length) +
        // 15
        // (data) = 28
        val expectedLength = 1 + 1 + 8 + 2 + 1 + SAMPLE_PRODUCT_DATA.size
        assertEquals(expectedLength, bytes.size)
        assertEquals(expectedLength.toByte(), bytes[0])

        // Check response code
        assertEquals(RequestProductInformationResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(0x00.toByte(), bytes[10]) // Status 1
        assertEquals(0x00.toByte(), bytes[11]) // Status 2

        // Check data length
        assertEquals(SAMPLE_PRODUCT_DATA.size.toByte(), bytes[12])

        for (i in SAMPLE_PRODUCT_DATA.indices) {
            assertEquals("Data byte $i", SAMPLE_PRODUCT_DATA[i], bytes[13 + i])
        }
    }

    @Test
    fun testRequestProductInformationResponse_toByteArray_error() {
        val response =
            RequestProductInformationResponse(
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
        assertEquals(RequestProductInformationResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(0x01.toByte(), bytes[10]) // Status 1
        assertEquals(0x02.toByte(), bytes[11]) // Status 2
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestProductInformationResponse_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        RequestProductInformationResponse(invalidIdm, 0x00, 0x00, SAMPLE_PRODUCT_DATA)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestProductInformationResponse_successWithEmptyData() {
        // Success response should have data
        RequestProductInformationResponse(TEST_IDM, 0x00, 0x00, ByteArray(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestProductInformationResponse_errorWithData() {
        // Error response should not have data
        RequestProductInformationResponse(TEST_IDM, 0x01, 0x00, SAMPLE_PRODUCT_DATA)
    }

    @Test
    fun testFromByteArray_successResponse() {
        // Create a success response with the user's example data
        val response = RequestProductInformationResponse(TEST_IDM, 0x00, 0x00, SAMPLE_PRODUCT_DATA)
        val data = response.toByteArray()

        val parsedResponse = RequestProductInformationResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedResponse.idm)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag1)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag2)
        assertArrayEquals(SAMPLE_PRODUCT_DATA, parsedResponse.productInformationData)
        assertTrue(parsedResponse.success)
    }

    @Test
    fun testFromByteArray_errorResponse() {
        // Create an error response
        val response = RequestProductInformationResponse(TEST_IDM, 0x01, 0x02, ByteArray(0))
        val data = response.toByteArray()

        val parsedResponse = RequestProductInformationResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedResponse.idm)
        assertEquals(0x01.toByte(), parsedResponse.statusFlag1)
        assertEquals(0x02.toByte(), parsedResponse.statusFlag2)
        assertArrayEquals(ByteArray(0), parsedResponse.productInformationData)
        assertFalse(parsedResponse.success)
    }

    @Test
    fun testFromByteArray_userExampleData() {
        // Test with the user's example: 00000f1801020308475001d7007720250110
        // This represents: status1=00, status2=00, length=0f, data=1801020308475001d7007720250110
        // Full response: length + response_code + idm + status1 + status2 + length + data

        val fullResponseData =
            "1c3b01020304050607ff00000f1801020308475001d7007720250110".hexToByteArray()

        val parsedResponse = RequestProductInformationResponse.fromByteArray(fullResponseData)

        assertArrayEquals(TEST_IDM, parsedResponse.idm)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag1)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag2)
        assertArrayEquals(SAMPLE_PRODUCT_DATA, parsedResponse.productInformationData)
        assertTrue(parsedResponse.success)
    }

    @Test
    fun testFromByteArray_anotherIdm() {
        val response =
            RequestProductInformationResponse(ANOTHER_IDM, 0x00, 0x00, SAMPLE_PRODUCT_DATA)
        val data = response.toByteArray()

        val parsedResponse = RequestProductInformationResponse.fromByteArray(data)

        assertArrayEquals(ANOTHER_IDM, parsedResponse.idm)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag1)
        assertEquals(0x00.toByte(), parsedResponse.statusFlag2)
        assertArrayEquals(SAMPLE_PRODUCT_DATA, parsedResponse.productInformationData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val data = "0c3b".hexToByteArray() // Too short for minimum response
        RequestProductInformationResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = "0d3b01020304050607ff0000".hexToByteArray() // Length says 13, but actual is 12
        RequestProductInformationResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongResponseCode() {
        val data =
            "0c3a01020304050607ff0000"
                .hexToByteArray() // Wrong response code (0x3a instead of 0x3b)
        RequestProductInformationResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_successWithWrongDataLength() {
        // Create data where the length byte doesn't match actual data length
        val wrongData = "1c3b01020304050607ff0000101801020308475001d7007720250110".hexToByteArray()
        // Length byte says 16 (0x10) but we only have 15 bytes of data
        RequestProductInformationResponse.fromByteArray(wrongData)
    }

    @Test
    fun testConstants() {
        assertEquals(0x3b.toByte(), RequestProductInformationResponse.RESPONSE_CODE.toByte())
        assertEquals(
            12,
            RequestProductInformationResponse.MIN_LENGTH,
        ) // 1 (length) + 1 (response code) + 8 (IDM) + 2 (status)
        assertEquals(
            13,
            RequestProductInformationResponse.MIN_SUCCESS_LENGTH,
        ) // MIN_LENGTH + 1 (data length)
    }

    @Test
    fun testRoundTrip_success() {
        val originalResponse =
            RequestProductInformationResponse(ANOTHER_IDM, 0x00, 0x00, SAMPLE_PRODUCT_DATA)

        val bytes = originalResponse.toByteArray()
        val parsedResponse = RequestProductInformationResponse.fromByteArray(bytes)

        assertArrayEquals(originalResponse.idm, parsedResponse.idm)
        assertEquals(originalResponse.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(originalResponse.statusFlag2, parsedResponse.statusFlag2)
        assertArrayEquals(
            originalResponse.productInformationData,
            parsedResponse.productInformationData,
        )
        assertArrayEquals(bytes, parsedResponse.toByteArray())
    }

    @Test
    fun testRoundTrip_error() {
        val originalResponse =
            RequestProductInformationResponse(ANOTHER_IDM, 0xFF.toByte(), 0x01, ByteArray(0))

        val bytes = originalResponse.toByteArray()
        val parsedResponse = RequestProductInformationResponse.fromByteArray(bytes)

        assertArrayEquals(originalResponse.idm, parsedResponse.idm)
        assertEquals(originalResponse.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(originalResponse.statusFlag2, parsedResponse.statusFlag2)
        assertArrayEquals(
            originalResponse.productInformationData,
            parsedResponse.productInformationData,
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
            val response = RequestProductInformationResponse(idm, 0x00, 0x00, SAMPLE_PRODUCT_DATA)
            val bytes = response.toByteArray()
            val parsed = RequestProductInformationResponse.fromByteArray(bytes)

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
                SAMPLE_PRODUCT_DATA,
                parsed.productInformationData,
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
                SAMPLE_PRODUCT_DATA,
            )

        for (testData in testDataSizes) {
            if (testData.isEmpty()) continue // Skip empty data for success response

            val response = RequestProductInformationResponse(TEST_IDM, 0x00, 0x00, testData)
            val bytes = response.toByteArray()
            val parsed = RequestProductInformationResponse.fromByteArray(bytes)

            assertArrayEquals(
                "Failed for data size ${testData.size}",
                testData,
                parsed.productInformationData,
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
            val testData = if (hasData) SAMPLE_PRODUCT_DATA else ByteArray(0)

            val response = RequestProductInformationResponse(TEST_IDM, status1, status2, testData)
            val bytes = response.toByteArray()
            val parsed = RequestProductInformationResponse.fromByteArray(bytes)

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
