package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class RequestServiceV2ResponseTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testRequestServiceV2ResponseSuccessAesOnly() {
        val idm = IDM
        val aesKeyVersions =
            arrayOf(
                KeyVersion("0100".hexToByteArray()), // Key version 0x0001
                KeyVersion.MISSING, // Node doesn't exist
            )

        val response =
            RequestServiceV2Response(
                idm,
                0x00, // Success status
                0x00, // Success status
                EncryptionIdentifier.AES128,
                aesKeyVersions,
                emptyArray(), // No DES keys
            )

        assertEquals(idm.toList(), response.idm.toList())
        assertTrue(response.success)
        assertEquals(EncryptionIdentifier.AES128, response.encryptionIdentifier)
        assertEquals(2, response.aesKeyVersions.size)
        assertEquals(0, response.desKeyVersions.size)
        assertTrue(!response.aesKeyVersions[0].isMissing)
        assertFalse(!response.aesKeyVersions[1].isMissing)
        assertEquals(0x0001, response.aesKeyVersions[0].toInt())
        assertEquals(0xFFFF, response.aesKeyVersions[1].toInt())
        assertTrue(response.desKeyVersions.isEmpty())
    }

    @Test
    fun testRequestServiceV2ResponseSuccessAesAndDes() {
        val idm = IDM
        val aesKeyVersions = arrayOf(KeyVersion("0100".hexToByteArray()))
        val desKeyVersions = arrayOf(KeyVersion("0200".hexToByteArray()))

        val response =
            RequestServiceV2Response(
                idm,
                0x00, // Success status
                0x00, // Success status
                EncryptionIdentifier.AES128_DES112,
                aesKeyVersions,
                desKeyVersions,
            )

        assertEquals(idm.toList(), response.idm.toList())
        assertTrue(response.success)
        assertEquals(EncryptionIdentifier.AES128_DES112, response.encryptionIdentifier)
        assertEquals(1, response.aesKeyVersions.size)
        assertEquals(1, response.desKeyVersions.size)
        assertTrue(!response.aesKeyVersions[0].isMissing)
        assertTrue(!response.desKeyVersions[0].isMissing)
        assertEquals(0x0001, response.aesKeyVersions[0].toInt())
        assertEquals(0x0002, response.desKeyVersions[0].toInt())
    }

    @Test
    fun testRequestServiceV2ResponseError() {
        val idm = IDM

        val response =
            RequestServiceV2Response(
                idm,
                0x01, // Error status
                0x00,
                null, // No encryption identifier on error
                // No AES keys on error
                // No DES keys on error
            )

        assertEquals(idm.toList(), response.idm.toList())
        assertFalse(response.success)
        assertNull(response.encryptionIdentifier)
        assertTrue(response.aesKeyVersions.isEmpty())
        assertTrue(response.desKeyVersions.isEmpty())
        assertEquals(0, response.aesKeyVersions.size)
    }

    @Test
    fun testRequestServiceV2ResponseToByteArraySuccess() {
        val idm = IDM
        val aesKeyVersions = arrayOf(KeyVersion("0100".hexToByteArray()))

        val response =
            RequestServiceV2Response(idm, 0x00, 0x00, EncryptionIdentifier.AES128, aesKeyVersions)

        val data = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + status_flags(2) + enc_id(1) +
        // num_nodes(1)
        // + aes_key(2) = 16 bytes
        assertEquals(16, data.size)
        assertEquals(16.toByte(), data[0]) // Length
        assertEquals(0x33.toByte(), data[1]) // Response code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(0x00.toByte(), data[10]) // Status flag 1
        assertEquals(0x00.toByte(), data[11]) // Status flag 2
        assertEquals(EncryptionIdentifier.AES128.value.toByte(), data[12]) // Encryption identifier
        assertEquals(1.toByte(), data[13]) // Number of nodes
        assertEquals(
            "0100".hexToByteArray().toList(),
            data.sliceArray(14..15).toList(),
        ) // AES key version
    }

    @Test
    fun testRequestServiceV2ResponseToByteArrayError() {
        val idm = IDM

        val response =
            RequestServiceV2Response(
                idm,
                0x01, // Error
                0x00,
                null,
            )

        val data = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + status_flags(2) = 12 bytes
        assertEquals(12, data.size)
        assertEquals(12.toByte(), data[0]) // Length
        assertEquals(0x33.toByte(), data[1]) // Response code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(0x01.toByte(), data[10]) // Status flag 1
        assertEquals(0x00.toByte(), data[11]) // Status flag 2
    }

    @Test
    fun testRequestServiceV2ResponseFromByteArraySuccess() {
        // Test data: success response with AES only
        val testData = "1033" + "0102030405060708" + "0000" + "4f" + "01" + "0100"
        val data = testData.hexToByteArray()

        val response = RequestServiceV2Response.fromByteArray(data)

        assertTrue(response.success)
        assertEquals(EncryptionIdentifier.AES128, response.encryptionIdentifier)
        assertEquals(1, response.aesKeyVersions.size)
        assertEquals(0x0001, response.aesKeyVersions[0].toInt())
        assertTrue(response.desKeyVersions.isEmpty())
    }

    @Test
    fun testRequestServiceV2ResponseFromByteArrayError() {
        // Test data: error response
        val testData = "0c33" + "0102030405060708" + "0100"
        val data = testData.hexToByteArray()

        val response = RequestServiceV2Response.fromByteArray(data)

        assertFalse(response.success)
        assertNull(response.encryptionIdentifier)
        assertEquals(0, response.aesKeyVersions.size)
    }

    @Test
    fun testRequestServiceV2ResponseRoundTrip() {
        val originalResponse =
            RequestServiceV2Response(
                IDM,
                0x00,
                0x00,
                EncryptionIdentifier.AES128_DES56,
                arrayOf(KeyVersion("0100".hexToByteArray()), KeyVersion.MISSING),
                arrayOf(KeyVersion("0200".hexToByteArray()), KeyVersion.MISSING),
            )

        val data = originalResponse.toByteArray()
        val parsedResponse = RequestServiceV2Response.fromByteArray(data)

        assertEquals(originalResponse.success, parsedResponse.success)
        assertEquals(originalResponse.encryptionIdentifier, parsedResponse.encryptionIdentifier)
        assertEquals(originalResponse.aesKeyVersions.size, parsedResponse.aesKeyVersions.size)

        for (i in 0 until originalResponse.aesKeyVersions.size) {
            assertEquals(
                originalResponse.aesKeyVersions[i].toInt(),
                parsedResponse.aesKeyVersions[i].toInt(),
            )
            assertEquals(
                originalResponse.desKeyVersions[i].toInt(),
                parsedResponse.desKeyVersions[i].toInt(),
            )
        }
    }

    @Test
    fun testRequestServiceV2ResponseInvalidMissingEncryptionId() {
        assertThrows(IllegalArgumentException::class.java) {
            RequestServiceV2Response(
                IDM,
                0x00, // Success status
                0x00,
                null, // Missing encryption identifier
                arrayOf(KeyVersion("0100".hexToByteArray())),
            )
        }
    }

    @Test
    fun testRequestServiceV2ResponseInvalidMissingAesKeys() {
        assertThrows(IllegalArgumentException::class.java) {
            RequestServiceV2Response(
                IDM,
                0x00, // Success status
                0x00,
                EncryptionIdentifier.AES128,
                // Missing AES keys
            )
        }
    }

    @Test
    fun testRequestServiceV2ResponseInvalidMismatchedKeyArraySizes() {
        assertThrows(IllegalArgumentException::class.java) {
            RequestServiceV2Response(
                IDM,
                0x00,
                0x00,
                EncryptionIdentifier.AES128_DES112,
                arrayOf(KeyVersion("0100".hexToByteArray())),
                arrayOf(
                    KeyVersion("0200".hexToByteArray()),
                    KeyVersion("0300".hexToByteArray()),
                ), // Different size
            )
        }
    }
}
