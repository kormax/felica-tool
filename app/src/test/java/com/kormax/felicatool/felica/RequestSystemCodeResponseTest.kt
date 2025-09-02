package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestSystemCodeResponse */
class RequestSystemCodeResponseTest {

    companion object {
        private const val IDM = "0123456789abcdef"
        private val SYSTEM_CODE_FE00 = "fe00".hexToByteArray()
        private val SYSTEM_CODE_FE0F = "fe0f".hexToByteArray()
        private val SYSTEM_CODE_8000 = "8000".hexToByteArray()
    }

    @Test
    fun testRequestSystemCodeResponse_creation_singleSystemCode() {
        val idm = IDM.hexToByteArray()
        val systemCodes = listOf(SYSTEM_CODE_FE00)
        val response = RequestSystemCodeResponse(idm, systemCodes)

        assertArrayEquals(idm, response.idm)
        assertEquals(1, response.systemCodes.size)
        assertArrayEquals(SYSTEM_CODE_FE00, response.systemCodes[0])
    }

    @Test
    fun testRequestSystemCodeResponse_creation_multipleSystemCodes() {
        val idm = IDM.hexToByteArray()
        val systemCodes = listOf(SYSTEM_CODE_FE00, SYSTEM_CODE_FE0F, SYSTEM_CODE_8000)
        val response = RequestSystemCodeResponse(idm, systemCodes)

        assertArrayEquals(idm, response.idm)
        assertEquals(3, response.systemCodes.size)
        assertArrayEquals(SYSTEM_CODE_FE00, response.systemCodes[0])
        assertArrayEquals(SYSTEM_CODE_FE0F, response.systemCodes[1])
        assertArrayEquals(SYSTEM_CODE_8000, response.systemCodes[2])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestSystemCodeResponse_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        val systemCodes = listOf(SYSTEM_CODE_FE00)
        RequestSystemCodeResponse(invalidIdm, systemCodes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestSystemCodeResponse_emptySystemCodes() {
        val idm = IDM.hexToByteArray()
        val systemCodes = emptyList<ByteArray>()
        RequestSystemCodeResponse(idm, systemCodes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestSystemCodeResponse_invalidSystemCodeSize() {
        val idm = IDM.hexToByteArray()
        val invalidSystemCode = byteArrayOf(0xFE.toByte()) // Too short
        val systemCodes = listOf(invalidSystemCode)
        RequestSystemCodeResponse(idm, systemCodes)
    }

    @Test
    fun testRequestSystemCodeResponse_toByteArray_singleSystemCode() {
        val idm = IDM.hexToByteArray()
        val systemCodes = listOf(SYSTEM_CODE_FE00)
        val response = RequestSystemCodeResponse(idm, systemCodes)
        val bytes = response.toByteArray()

        // Check length (1 + 1 + 8 + 1 + 2 = 13 bytes)
        assertEquals(13, bytes.size)
        assertEquals(13.toByte(), bytes[0]) // Length

        // Check response code
        assertEquals(RequestSystemCodeResponse.RESPONSE_CODE, bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }

        // Check number of system codes
        assertEquals(1.toByte(), bytes[10])

        // Check system code
        assertArrayEquals(SYSTEM_CODE_FE00, bytes.sliceArray(11..12))
    }

    @Test
    fun testRequestSystemCodeResponse_toByteArray_multipleSystemCodes() {
        val idm = IDM.hexToByteArray()
        val systemCodes = listOf(SYSTEM_CODE_FE00, SYSTEM_CODE_FE0F)
        val response = RequestSystemCodeResponse(idm, systemCodes)
        val bytes = response.toByteArray()

        // Check length (1 + 1 + 8 + 1 + 4 = 15 bytes)
        assertEquals(15, bytes.size)
        assertEquals(15.toByte(), bytes[0]) // Length

        // Check response code
        assertEquals(RequestSystemCodeResponse.RESPONSE_CODE, bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }

        // Check number of system codes
        assertEquals(2.toByte(), bytes[10])

        // Check system codes
        assertArrayEquals(SYSTEM_CODE_FE00, bytes.sliceArray(11..12))
        assertArrayEquals(SYSTEM_CODE_FE0F, bytes.sliceArray(13..14))
    }

    @Test
    fun testFromByteArray_singleSystemCode() {
        // Length(1) + ResponseCode(1) + IDM(8) + NumSystemCodes(1) + SystemCode(2) = 13 bytes
        val data = "0d0d${IDM}01fe00".hexToByteArray()

        val response = RequestSystemCodeResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(1, response.systemCodes.size)
        assertArrayEquals(SYSTEM_CODE_FE00, response.systemCodes[0])
    }

    @Test
    fun testFromByteArray_multipleSystemCodes() {
        // Length(1) + ResponseCode(1) + IDM(8) + NumSystemCodes(1) + SystemCodes(6) = 17 bytes
        val data = "110d${IDM}03fe00fe0f8000".hexToByteArray()

        val response = RequestSystemCodeResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(3, response.systemCodes.size)
        assertArrayEquals(SYSTEM_CODE_FE00, response.systemCodes[0])
        assertArrayEquals(SYSTEM_CODE_FE0F, response.systemCodes[1])
        assertArrayEquals(SYSTEM_CODE_8000, response.systemCodes[2])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val shortData = "0c0d${IDM}01fe".hexToByteArray() // Missing 1 byte from system code
        RequestSystemCodeResponse.fromByteArray(shortData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongResponseCode() {
        val wrongResponseData =
            "0d0c${IDM}01fe00".hexToByteArray() // Response code 0x0c instead of 0x0d
        RequestSystemCodeResponse.fromByteArray(wrongResponseData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val wrongLengthData =
            "0e0d${IDM}01fe00".hexToByteArray() // Length says 14 but data is 13 bytes
        RequestSystemCodeResponse.fromByteArray(wrongLengthData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_dataSizeMismatch() {
        // Says 2 system codes but only provides data for 1
        val mismatchData = "0d0d${IDM}02fe00".hexToByteArray()
        RequestSystemCodeResponse.fromByteArray(mismatchData)
    }

    @Test
    fun testRequestSystemCodeResponse_roundTrip_single() {
        val idm = IDM.hexToByteArray()
        val systemCodes = listOf(SYSTEM_CODE_FE00)
        val response = RequestSystemCodeResponse(idm, systemCodes)
        val bytes = response.toByteArray()
        val parsedResponse = RequestSystemCodeResponse.fromByteArray(bytes)

        assertArrayEquals(response.idm, parsedResponse.idm)
        assertEquals(response.systemCodes.size, parsedResponse.systemCodes.size)
        for (i in response.systemCodes.indices) {
            assertArrayEquals(response.systemCodes[i], parsedResponse.systemCodes[i])
        }
    }

    @Test
    fun testRequestSystemCodeResponse_roundTrip_multiple() {
        val idm = IDM.hexToByteArray()
        val systemCodes = listOf(SYSTEM_CODE_FE00, SYSTEM_CODE_FE0F, SYSTEM_CODE_8000)
        val response = RequestSystemCodeResponse(idm, systemCodes)
        val bytes = response.toByteArray()
        val parsedResponse = RequestSystemCodeResponse.fromByteArray(bytes)

        assertArrayEquals(response.idm, parsedResponse.idm)
        assertEquals(response.systemCodes.size, parsedResponse.systemCodes.size)
        for (i in response.systemCodes.indices) {
            assertArrayEquals(response.systemCodes[i], parsedResponse.systemCodes[i])
        }
    }
}
