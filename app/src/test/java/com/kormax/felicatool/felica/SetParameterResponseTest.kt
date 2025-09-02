package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for SetParameterResponse */
class SetParameterResponseTest {

    companion object {
        private val TEST_IDM = "01020304050607ff".hexToByteArray()
        private val ANOTHER_IDM = "1122334455667788".hexToByteArray()
        private const val SUCCESS_STATUS1: Byte = 0x00
        private const val SUCCESS_STATUS2: Byte = 0x00
        private const val ERROR_STATUS1: Byte = 0xFF.toByte()
        private const val ERROR_STATUS2: Byte = 0x01
    }

    @Test
    fun testSetParameterResponse_creation() {
        val response = SetParameterResponse(TEST_IDM, SUCCESS_STATUS1, SUCCESS_STATUS2)

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(SUCCESS_STATUS1, response.statusFlag1)
        assertEquals(SUCCESS_STATUS2, response.statusFlag2)
        assertEquals("01020304050607FF", response.idm.toHexString().uppercase())
    }

    @Test
    fun testSetParameterResponse_toByteArray() {
        val response = SetParameterResponse(TEST_IDM, SUCCESS_STATUS1, SUCCESS_STATUS2)
        val bytes = response.toByteArray()

        // Check length
        assertEquals(SetParameterResponse.RESPONSE_LENGTH, bytes.size)
        assertEquals(SetParameterResponse.RESPONSE_LENGTH.toByte(), bytes[0])

        // Check response code
        assertEquals(SetParameterResponse.RESPONSE_CODE, bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(SUCCESS_STATUS1, bytes[10])
        assertEquals(SUCCESS_STATUS2, bytes[11])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSetParameterResponse_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        SetParameterResponse(invalidIdm, SUCCESS_STATUS1, SUCCESS_STATUS2)
    }

    @Test
    fun testFromByteArray_basic() {
        val originalResponse = SetParameterResponse(TEST_IDM, SUCCESS_STATUS1, SUCCESS_STATUS2)
        val data = originalResponse.toByteArray()

        val parsedResponse = SetParameterResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedResponse.idm)
        assertEquals(SUCCESS_STATUS1, parsedResponse.statusFlag1)
        assertEquals(SUCCESS_STATUS2, parsedResponse.statusFlag2)
    }

    @Test
    fun testFromByteArray_specificBytes() {
        // 0c2101020304050607ff0000 (length=12, response_code=0x21, idm=01020304050607ff,
        // status1=0x00,
        // status2=0x00)
        val data = "0c2101020304050607ff0000".hexToByteArray()

        val response = SetParameterResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(SUCCESS_STATUS1, response.statusFlag1)
        assertEquals(SUCCESS_STATUS2, response.statusFlag2)
    }

    @Test
    fun testFromByteArray_anotherIdm() {
        // 0c2111223344556677880000 (length=12, response_code=0x21, idm=1122334455667788,
        // status1=0x00,
        // status2=0x00)
        val data = "0c2111223344556677880000".hexToByteArray()

        val response = SetParameterResponse.fromByteArray(data)

        assertArrayEquals(ANOTHER_IDM, response.idm)
        assertEquals(SUCCESS_STATUS1, response.statusFlag1)
        assertEquals(SUCCESS_STATUS2, response.statusFlag2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val data = "0c21".hexToByteArray() // Too short
        SetParameterResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = "0d2101020304050607ff0000".hexToByteArray() // Length says 13, but actual is 12
        SetParameterResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongResponseCode() {
        val data =
            "0c2001020304050607ff0000"
                .hexToByteArray() // Wrong response code (0x20 instead of 0x21)
        SetParameterResponse.fromByteArray(data)
    }

    @Test
    fun testConstants() {
        assertEquals(0x21.toByte(), SetParameterResponse.RESPONSE_CODE)
        assertEquals(
            12,
            SetParameterResponse.RESPONSE_LENGTH,
        ) // 1 (length) + 1 (response code) + 8 (IDM) + 1 (status1) + 1 (status2)
    }

    @Test
    fun testRoundTrip() {
        val originalResponse = SetParameterResponse(ANOTHER_IDM, SUCCESS_STATUS1, SUCCESS_STATUS2)

        val bytes = originalResponse.toByteArray()
        val parsedResponse = SetParameterResponse.fromByteArray(bytes)

        assertArrayEquals(originalResponse.idm, parsedResponse.idm)
        assertEquals(originalResponse.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(originalResponse.statusFlag2, parsedResponse.statusFlag2)
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
            val response = SetParameterResponse(idm, SUCCESS_STATUS1, SUCCESS_STATUS2)
            val bytes = response.toByteArray()
            val parsed = SetParameterResponse.fromByteArray(bytes)

            assertArrayEquals("Failed for IDM: ${idm.toHexString()}", idm, parsed.idm)
            assertEquals(
                "Failed for status1 with IDM: ${idm.toHexString()}",
                SUCCESS_STATUS1,
                parsed.statusFlag1,
            )
            assertEquals(
                "Failed for status2 with IDM: ${idm.toHexString()}",
                SUCCESS_STATUS2,
                parsed.statusFlag2,
            )
        }
    }
}
