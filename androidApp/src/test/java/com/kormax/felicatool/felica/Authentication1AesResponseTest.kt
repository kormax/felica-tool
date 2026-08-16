package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class Authentication1AesResponseTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
        private val CHALLENGE_1B = "AABBCCDDEEFF00112233445566778899".hexToByteArray()
        private val CHALLENGE_2A = "00112233445566778899001122334455".hexToByteArray()
        private val NONCE = "66778899".hexToByteArray()
    }

    @Test
    fun testAuthentication1AesResponseCreation() {
        val idm = IDM
        val response = Authentication1AesResponse(idm, CHALLENGE_1B, CHALLENGE_2A, NONCE)

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(CHALLENGE_1B.toList(), response.challenge1B.toList())
        assertEquals(CHALLENGE_2A.toList(), response.challenge2A.toList())
        assertEquals(NONCE.toList(), response.nonce.toList())
    }

    @Test
    fun testAuthentication1AesResponseToByteArray() {
        val idm = IDM
        val response = Authentication1AesResponse(idm, CHALLENGE_1B, CHALLENGE_2A, NONCE)
        val responseData = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + challenge1B(16) +
        // challenge2A(16) + nonce(4) = 46 bytes
        assertEquals(46, responseData.size)
        assertEquals(46.toByte(), responseData[0]) // Length
        assertEquals(0x41.toByte(), responseData[1]) // Response code
        assertEquals(idm.toList(), responseData.sliceArray(2..9).toList()) // IDM
        assertEquals(CHALLENGE_1B.toList(), responseData.sliceArray(10..25).toList())
        assertEquals(CHALLENGE_2A.toList(), responseData.sliceArray(26..41).toList())
        assertEquals(NONCE.toList(), responseData.sliceArray(42..45).toList())
    }

    @Test
    fun testAuthentication1AesResponseFromByteArray() {
        // Response with IDM and data
        val responseData =
            "2E410102030405060708AABBCCDDEEFF001122334455667788990011223344556677889900112233445566778899"
                .hexToByteArray()

        val response = Authentication1AesResponse.fromByteArray(responseData)

        assertEquals(IDM.toList(), response.idm.toList())
        assertEquals(CHALLENGE_1B.toList(), response.challenge1B.toList())
        assertEquals(CHALLENGE_2A.toList(), response.challenge2A.toList())
        assertEquals(NONCE.toList(), response.nonce.toList())
    }

    @Test
    fun testAuthentication1AesResponseRoundTrip() {
        val idm = IDM
        val originalResponse = Authentication1AesResponse(idm, CHALLENGE_1B, CHALLENGE_2A, NONCE)
        val responseData = originalResponse.toByteArray()
        val parsedResponse = Authentication1AesResponse.fromByteArray(responseData)

        assertEquals(originalResponse.idm.toList(), parsedResponse.idm.toList())
        assertEquals(originalResponse.challenge1B.toList(), parsedResponse.challenge1B.toList())
        assertEquals(originalResponse.challenge2A.toList(), parsedResponse.challenge2A.toList())
        assertEquals(originalResponse.nonce.toList(), parsedResponse.nonce.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesResponseInvalidChallenge1BSize() {
        Authentication1AesResponse(IDM, ByteArray(15), CHALLENGE_2A, NONCE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesResponseInvalidChallenge2ASize() {
        Authentication1AesResponse(IDM, CHALLENGE_1B, ByteArray(15), NONCE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesResponseInvalidNonceSize() {
        Authentication1AesResponse(IDM, CHALLENGE_1B, CHALLENGE_2A, ByteArray(3))
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
