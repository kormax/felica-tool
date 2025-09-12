package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class Authentication1AesResponseTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
        private val DATA =
            "AABBCCDDEEFF001122334455667788990011223344556677889900112233445566778899"
                .hexToByteArray() // 36 bytes
    }

    @Test
    fun testAuthentication1AesResponseCreation() {
        val idm = IDM
        val data = DATA

        val response = Authentication1AesResponse(idm, data)

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(data.toList(), response.data.toList())
    }

    @Test
    fun testAuthentication1AesResponseToByteArray() {
        val idm = IDM
        val data = DATA

        val response = Authentication1AesResponse(idm, data)
        val responseData = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + data(36) = 46 bytes
        assertEquals(46, responseData.size)
        assertEquals(46.toByte(), responseData[0]) // Length
        assertEquals(0x41.toByte(), responseData[1]) // Response code
        assertEquals(idm.toList(), responseData.sliceArray(2..9).toList()) // IDM
        assertEquals(data.toList(), responseData.sliceArray(10..45).toList()) // Data
    }

    @Test
    fun testAuthentication1AesResponseFromByteArray() {
        // Response with IDM and data
        val responseData =
            "2E410102030405060708AABBCCDDEEFF001122334455667788990011223344556677889900112233445566778899"
                .hexToByteArray()

        val response = Authentication1AesResponse.fromByteArray(responseData)

        assertEquals(IDM.toList(), response.idm.toList())
        assertEquals(DATA.toList(), response.data.toList())
    }

    @Test
    fun testAuthentication1AesResponseRoundTrip() {
        val idm = IDM
        val data = DATA

        val originalResponse = Authentication1AesResponse(idm, data)
        val responseData = originalResponse.toByteArray()
        val parsedResponse = Authentication1AesResponse.fromByteArray(responseData)

        assertEquals(originalResponse.idm.toList(), parsedResponse.idm.toList())
        assertEquals(originalResponse.data.toList(), parsedResponse.data.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesResponseInvalidDataSize() {
        val idm = IDM
        val invalidData = "AABBCCDDEE".hexToByteArray() // Too short (5 bytes instead of 36)
        Authentication1AesResponse(idm, invalidData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesResponseFromByteArrayTooShort() {
        val data = "2E410102030405060708AABBCCDDEE".hexToByteArray() // Too short
        Authentication1AesResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesResponseFromByteArrayWrongResponseCode() {
        // Wrong response code (0x11 instead of 0x41)
        val data =
            "2E110102030405060708AABBCCDDEEFF00112233445566778899001122334455667788990011223344556677889900"
                .hexToByteArray()
        Authentication1AesResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesResponseFromByteArrayWrongLength() {
        // Wrong length (0x20 instead of 0x2E)
        val data =
            "20410102030405060708AABBCCDDEEFF00112233445566778899001122334455667788990011223344556677889900"
                .hexToByteArray()
        Authentication1AesResponse.fromByteArray(data)
    }
}
