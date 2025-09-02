package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestResponseResponse */
class RequestResponseResponseTest {

    companion object {
        private const val IDM = "0123456789abcdef"
    }

    @Test
    fun testRequestResponseResponse_creation() {
        val idm = IDM.hexToByteArray()
        val response = RequestResponseResponse(idm, CardMode.INITIAL)

        assertArrayEquals(idm, response.idm)
        assertEquals(CardMode.INITIAL, response.mode)
    }

    @Test
    fun testRequestResponseResponse_toByteArray_mode0() {
        val idm = IDM.hexToByteArray()
        val response = RequestResponseResponse(idm, CardMode.INITIAL)
        val bytes = response.toByteArray()

        // Check length (1 + 1 + 8 + 1 = 11 bytes)
        assertEquals(11, bytes.size)
        assertEquals(11.toByte(), bytes[0]) // Length

        // Check response code
        assertEquals(RequestResponseResponse.RESPONSE_CODE, bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }

        // Check mode
        assertEquals(0x00.toByte(), bytes[10]) // Mode0
    }

    @Test
    fun testRequestResponseResponse_toByteArray_mode1() {
        val idm = IDM.hexToByteArray()
        val response = RequestResponseResponse(idm, CardMode.AUTHENTICATION_PENDING)
        val bytes = response.toByteArray()

        assertEquals(11, bytes.size)
        assertEquals(0x01.toByte(), bytes[10]) // Mode1
    }

    @Test
    fun testRequestResponseResponse_toByteArray_mode2() {
        val idm = IDM.hexToByteArray()
        val response = RequestResponseResponse(idm, CardMode.AUTHENTICATED)
        val bytes = response.toByteArray()

        assertEquals(11, bytes.size)
        assertEquals(0x02.toByte(), bytes[10]) // Mode2
    }

    @Test
    fun testRequestResponseResponse_toByteArray_mode3() {
        val idm = IDM.hexToByteArray()
        val response = RequestResponseResponse(idm, CardMode.ISSUANCE)
        val bytes = response.toByteArray()

        assertEquals(11, bytes.size)
        assertEquals(0x03.toByte(), bytes[10]) // Mode3
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestResponseResponse_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        RequestResponseResponse(invalidIdm, CardMode.INITIAL)
    }

    @Test
    fun testFromByteArray_mode0() {
        // 0b05 + IDM (8 bytes) + mode (1 byte)
        val data = "0b05${IDM}00".hexToByteArray()

        val response = RequestResponseResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(CardMode.INITIAL, response.mode)
    }

    @Test
    fun testFromByteArray_mode1() {
        val data = "0b05${IDM}01".hexToByteArray()

        val response = RequestResponseResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(CardMode.AUTHENTICATION_PENDING, response.mode)
    }

    @Test
    fun testFromByteArray_mode2() {
        val data = "0b05${IDM}02".hexToByteArray()

        val response = RequestResponseResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(CardMode.AUTHENTICATED, response.mode)
    }

    @Test
    fun testFromByteArray_mode3() {
        val data = "0b05${IDM}03".hexToByteArray()

        val response = RequestResponseResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(CardMode.ISSUANCE, response.mode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val shortData = "0a05${IDM}".hexToByteArray() // 10 bytes instead of 11
        RequestResponseResponse.fromByteArray(shortData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = "0c05${IDM}00".hexToByteArray() // Length says 12, but actual length is 11
        RequestResponseResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongResponseCode() {
        val wrongResponseData =
            "0b04${IDM}00".hexToByteArray() // Response code 0x04 instead of 0x05
        RequestResponseResponse.fromByteArray(wrongResponseData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_invalidMode() {
        val invalidModeData = "0b05${IDM}04".hexToByteArray() // Invalid mode value 0x04
        RequestResponseResponse.fromByteArray(invalidModeData)
    }

    @Test
    fun testRequestResponseResponse_roundTrip() {
        val idm = IDM.hexToByteArray()
        val response = RequestResponseResponse(idm, CardMode.AUTHENTICATED)
        val bytes = response.toByteArray()
        val parsedResponse = RequestResponseResponse.fromByteArray(bytes)

        assertArrayEquals(response.idm, parsedResponse.idm)
        assertEquals(response.mode, parsedResponse.mode)
    }
}
