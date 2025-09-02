package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class RequestServiceResponseTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testRequestServiceResponseCreation() {
        val idm = IDM
        val keyVersions =
            arrayOf(
                KeyVersion.fromByteArray("0100".hexToByteArray()), // Key version 0x0001
                KeyVersion.MISSING, // Node doesn't exist
            )

        val response = RequestServiceResponse(idm, keyVersions)

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(2, response.keyVersions.size)
        assertTrue(!response.keyVersions[0].isMissing)
        assertFalse(!response.keyVersions[1].isMissing)
        assertEquals(0x0001, response.keyVersions[0].toInt())
        assertEquals(0xFFFF, response.keyVersions[1].toInt())
    }

    @Test
    fun testRequestServiceResponseToByteArray() {
        val idm = IDM
        val keyVersions = arrayOf(KeyVersion.fromByteArray("0100".hexToByteArray()))

        val response = RequestServiceResponse(idm, keyVersions)
        val data = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + number_of_nodes(1) + key_version(2) =
        // 13
        // bytes
        assertEquals(13, data.size)
        assertEquals(13.toByte(), data[0]) // Length
        assertEquals(0x03.toByte(), data[1]) // Response code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(1.toByte(), data[10]) // Number of nodes
        assertEquals(
            "0100".hexToByteArray().toList(),
            data.sliceArray(11..12).toList(),
        ) // Key version
    }

    @Test
    fun testRequestServiceResponseFromByteArray() {
        val data = "0D03010203040506070801FFFF".hexToByteArray()

        val response = RequestServiceResponse.fromByteArray(data)

        assertEquals(IDM.toList(), response.idm.toList())
        assertEquals(1, response.keyVersions.size)
        assertTrue(KeyVersion.MISSING == response.keyVersions[0])
        assertTrue(response.keyVersions[0].isMissing) // This should be true
        assertTrue(response.keyVersions[0].isMissing)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestServiceResponseEmptyKeyVersions() {
        val idm = IDM
        RequestServiceResponse(idm, emptyArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestServiceResponseInvalidKeyVersionSize() {
        val idm = IDM
        val keyVersions = arrayOf(KeyVersion("00".hexToByteArray())) // Too short
        RequestServiceResponse(idm, keyVersions)
    }
}
