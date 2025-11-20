package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for GetSystemStatusResponse */
class GetSystemStatusResponseTest {

    companion object {
        private const val IDM = "0123456789abcdef"
    }

    @Test
    fun testGetSystemStatusResponse_creation() {
        val idm = IDM.hexToByteArray()
        val response =
            GetSystemStatusResponse(idm, 0x00, 0x00, 0x01, byteArrayOf(0x01, 0x02, 0x03, 0x04))

        assertArrayEquals(idm, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertEquals(0x01.toByte(), response.flag)
        assertEquals(0x04.toByte(), response.data.size.toByte())
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), response.data)
    }

    @Test
    fun testGetSystemStatusResponse_creation_emptyData() {
        val idm = IDM.hexToByteArray()
        val response = GetSystemStatusResponse(idm, 0x00, 0x00, 0x01, byteArrayOf())

        assertArrayEquals(idm, response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertEquals(0x01.toByte(), response.flag)
        assertEquals(0x00.toByte(), response.data.size.toByte())
        assertArrayEquals(byteArrayOf(), response.data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusResponse_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        GetSystemStatusResponse(invalidIdm, 0x00, 0x00, 0x01, byteArrayOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusResponse_dataSizeTooLarge() {
        val idm = IDM.hexToByteArray()
        val largeData = ByteArray(256) // Too large for 1-byte length field
        GetSystemStatusResponse(idm, 0x00, 0x00, 0x01, largeData)
    }

    @Test
    fun testGetSystemStatusResponse_toByteArray() {
        val idm = IDM.hexToByteArray()
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val response = GetSystemStatusResponse(idm, 0x00, 0x00, 0x01, data)
        val bytes = response.toByteArray()

        // Check length (1 + 1 + 8 + 1 + 1 + 1 + 1 + 4 = 18 bytes)
        assertEquals(18, bytes.size)
        assertEquals(18.toByte(), bytes[0]) // Length

        // Check response code
        assertEquals(GetSystemStatusResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(0x00.toByte(), bytes[10]) // Status 1
        assertEquals(0x00.toByte(), bytes[11]) // Status 2

        // Check flag
        assertEquals(0x01.toByte(), bytes[12])

        // Check data length
        assertEquals(0x04.toByte(), bytes[13])

        // Check data
        assertArrayEquals(data, bytes.sliceArray(14..17))
    }

    @Test
    fun testGetSystemStatusResponse_toByteArray_emptyData() {
        val idm = IDM.hexToByteArray()
        val response = GetSystemStatusResponse(idm, 0x00, 0x00, 0x01, byteArrayOf())
        val bytes = response.toByteArray()

        // Check length (1 + 1 + 8 + 1 + 1 + 1 + 1 = 14 bytes)
        assertEquals(14, bytes.size)
        assertEquals(14.toByte(), bytes[0]) // Length

        // Check response code
        assertEquals(GetSystemStatusResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check data length
        assertEquals(0x00.toByte(), bytes[13])
    }

    @Test
    fun testGetSystemStatusResponse_fromByteArray() {
        // Length(1) + ResponseCode(1) + IDM(8) + Status1(1) + Status2(1) + Flag(1) + Data.size(1)
        // +
        // Data(4) = 18 bytes
        val data = "1239${IDM}0000010401020304".hexToByteArray()

        val response = GetSystemStatusResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertEquals(0x01.toByte(), response.flag)
        assertEquals(0x04.toByte(), response.data.size.toByte())
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03, 0x04), response.data)
    }

    @Test
    fun testGetSystemStatusResponse_fromByteArray_emptyData() {
        // Length(1) + ResponseCode(1) + IDM(8) + Status1(1) + Status2(1) + Flag(1) + Data.size(1)
        // = 14
        // bytes
        val data = "0e39${IDM}00000100".hexToByteArray()

        val response = GetSystemStatusResponse.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), response.idm)
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertEquals(0x01.toByte(), response.flag)
        assertEquals(0x00.toByte(), response.data.size.toByte())
        assertArrayEquals(byteArrayOf(), response.data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusResponse_fromByteArray_tooShort() {
        val shortData = "0d39${IDM}00000100".hexToByteArray() // Length says 13 but data is 14 bytes
        GetSystemStatusResponse.fromByteArray(shortData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusResponse_fromByteArray_wrongResponseCode() {
        val wrongResponseData =
            "0e38${IDM}00000100".hexToByteArray() // Response code 0x38 instead of 0x39
        GetSystemStatusResponse.fromByteArray(wrongResponseData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusResponse_fromByteArray_lengthMismatch() {
        val wrongLengthData =
            "0f39${IDM}00000100".hexToByteArray() // Length says 15 but data is 14 bytes
        GetSystemStatusResponse.fromByteArray(wrongLengthData)
    }

    @Test
    fun testGetSystemStatusResponse_roundTrip() {
        val idm = IDM.hexToByteArray()
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val response = GetSystemStatusResponse(idm, 0x00, 0x00, 0x01, data)
        val bytes = response.toByteArray()
        val parsedResponse = GetSystemStatusResponse.fromByteArray(bytes)

        assertArrayEquals(response.idm, parsedResponse.idm)
        assertEquals(response.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(response.statusFlag2, parsedResponse.statusFlag2)
        assertEquals(response.flag, parsedResponse.flag)
        assertEquals(response.data.size, parsedResponse.data.size)
        assertArrayEquals(response.data, parsedResponse.data)
    }

    @Test
    fun testGetSystemStatusResponse_roundTrip_emptyData() {
        val idm = IDM.hexToByteArray()
        val response = GetSystemStatusResponse(idm, 0x00, 0x00, 0x01, byteArrayOf())
        val bytes = response.toByteArray()
        val parsedResponse = GetSystemStatusResponse.fromByteArray(bytes)

        assertArrayEquals(response.idm, parsedResponse.idm)
        assertEquals(response.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(response.statusFlag2, parsedResponse.statusFlag2)
        assertEquals(response.flag, parsedResponse.flag)
        assertEquals(response.data.size, parsedResponse.data.size)
        assertArrayEquals(response.data, parsedResponse.data)
    }
}
