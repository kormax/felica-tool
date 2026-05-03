package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class Authentication1DesResponseTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
        private val CHALLENGE_1B = "AABBCCDDEEFF0011".hexToByteArray()
        private val CHALLENGE_2A = "1133557799BBDDFF".hexToByteArray()
    }

    @Test
    fun testAuthentication1DesResponseCreation() {
        val idm = IDM
        val challenge1B = CHALLENGE_1B
        val challenge2A = CHALLENGE_2A

        val response = Authentication1DesResponse(idm, challenge1B, challenge2A)

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(challenge1B.toList(), response.challenge1B.toList())
        assertEquals(challenge2A.toList(), response.challenge2A.toList())
    }

    @Test
    fun testAuthentication1DesResponseToByteArray() {
        val idm = IDM
        val challenge1B = CHALLENGE_1B
        val challenge2A = CHALLENGE_2A

        val response = Authentication1DesResponse(idm, challenge1B, challenge2A)
        val data = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + challenge1B(8) + challenge2A(8) = 26
        // bytes
        assertEquals(26, data.size)
        assertEquals(26.toByte(), data[0]) // Length
        assertEquals(0x11.toByte(), data[1]) // Response code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(challenge1B.toList(), data.sliceArray(10..17).toList()) // Challenge1B
        assertEquals(challenge2A.toList(), data.sliceArray(18..25).toList()) // Challenge2A
    }

    @Test
    fun testAuthentication1DesResponseFromByteArray() {
        // Response with IDM, challenge1B, and challenge2A
        val data = "1A110102030405060708AABBCCDDEEFF00111133557799BBDDFF".hexToByteArray()

        val response = Authentication1DesResponse.fromByteArray(data)

        assertEquals(IDM.toList(), response.idm.toList())
        assertEquals(CHALLENGE_1B.toList(), response.challenge1B.toList())
        assertEquals(CHALLENGE_2A.toList(), response.challenge2A.toList())
    }

    @Test
    fun testAuthentication1DesResponseRoundTrip() {
        val idm = IDM
        val challenge1B = CHALLENGE_1B
        val challenge2A = CHALLENGE_2A

        val originalResponse = Authentication1DesResponse(idm, challenge1B, challenge2A)
        val data = originalResponse.toByteArray()
        val parsedResponse = Authentication1DesResponse.fromByteArray(data)

        assertEquals(originalResponse.idm.toList(), parsedResponse.idm.toList())
        assertEquals(originalResponse.challenge1B.toList(), parsedResponse.challenge1B.toList())
        assertEquals(originalResponse.challenge2A.toList(), parsedResponse.challenge2A.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1DesResponseInvalidChallenge1BSize() {
        val idm = IDM
        val invalidChallenge1B = "AABBCC".hexToByteArray() // Too short
        val challenge2A = CHALLENGE_2A
        Authentication1DesResponse(idm, invalidChallenge1B, challenge2A)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1DesResponseInvalidChallenge2ASize() {
        val idm = IDM
        val challenge1B = CHALLENGE_1B
        val invalidChallenge2A = "1133557799".hexToByteArray() // Too short
        Authentication1DesResponse(idm, challenge1B, invalidChallenge2A)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1DesResponseFromByteArrayTooShort() {
        val data = "10".hexToByteArray() // Too short
        Authentication1DesResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1DesResponseFromByteArrayWrongResponseCode() {
        val data =
            "1A120102030405060708AABBCCDDEEFF00111133557799BBDDFF"
                .hexToByteArray() // Wrong response code (0x12 instead of 0x11)
        Authentication1DesResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1DesResponseFromByteArrayWrongLength() {
        val data =
            "1B110102030405060708AABBCCDDEEFF00111133557799BBDDFF"
                .hexToByteArray() // Wrong length (0x1B instead of 0x1A)
        Authentication1DesResponse.fromByteArray(data)
    }
}
