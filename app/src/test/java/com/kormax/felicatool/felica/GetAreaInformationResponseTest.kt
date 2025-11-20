package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class GetAreaInformationResponseTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testGetAreaInformationResponseErrorResponse() {
        val idm = IDM
        val statusFlag1 = 0xFF.toByte() // Error status
        val statusFlag2 = 0xE0.toByte() // Area 0 error

        val response = GetAreaInformationResponse(idm, statusFlag1, statusFlag2)

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(statusFlag1, response.statusFlag1)
        assertEquals(statusFlag2, response.statusFlag2)
        assertEquals(0, response.nodeCode.size)
        assertEquals(0, response.data.size)
        assertFalse(response.isStatusSuccessful)
    }

    @Test
    fun testGetAreaInformationResponseSuccessResponse() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte() // Success status
        val statusFlag2 = 0x00.toByte()
        val nodeCode = "1234".hexToByteArray()
        val data = "DEAD".hexToByteArray() // 2 bytes

        val response = GetAreaInformationResponse(idm, statusFlag1, statusFlag2, nodeCode, data)

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(statusFlag1, response.statusFlag1)
        assertEquals(statusFlag2, response.statusFlag2)
        assertEquals(nodeCode.toList(), response.nodeCode.toList())
        assertEquals(data.toList(), response.data.toList())
        assertTrue(response.isStatusSuccessful)
    }

    @Test
    fun testGetAreaInformationResponseToByteArrayError() {
        val idm = IDM
        val statusFlag1 = 0xFF.toByte()
        val statusFlag2 = 0xE2.toByte() // Not an area error

        val response = GetAreaInformationResponse(idm, statusFlag1, statusFlag2)
        val bytes = response.toByteArray()

        // Check length (1 + 1 + 8 + 2 = 12 bytes for error response)
        assertEquals(12, bytes.size)
        assertEquals(12.toByte(), bytes[0]) // Length

        // Check response code
        assertEquals(GetAreaInformationResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in idm.indices) {
            assertEquals("IDM byte $i", idm[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(statusFlag1, bytes[10])
        assertEquals(statusFlag2, bytes[11])
    }

    @Test
    fun testGetAreaInformationResponseToByteArraySuccess() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte()
        val statusFlag2 = 0x00.toByte()
        val nodeCode = "ABCD".hexToByteArray()
        val data = "1234".hexToByteArray() // 2 bytes

        val response = GetAreaInformationResponse(idm, statusFlag1, statusFlag2, nodeCode, data)
        val bytes = response.toByteArray()

        // Check length (1 + 1 + 8 + 2 + 2 + 2 = 16 bytes for success response)
        assertEquals(16, bytes.size)
        assertEquals(16.toByte(), bytes[0]) // Length

        // Check response code
        assertEquals(GetAreaInformationResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in idm.indices) {
            assertEquals("IDM byte $i", idm[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(statusFlag1, bytes[10])
        assertEquals(statusFlag2, bytes[11])

        // Check node code
        assertEquals(nodeCode[0], bytes[12])
        assertEquals(nodeCode[1], bytes[13])

        // Check data
        for (i in data.indices) {
            assertEquals("Data byte $i", data[i], bytes[14 + i])
        }
    }

    @Test
    fun testGetAreaInformationResponseFromByteArrayError() {
        val errorBytes = "0C250102030405060708FFE2".hexToByteArray()

        val response = GetAreaInformationResponse.fromByteArray(errorBytes)

        assertEquals("0102030405060708".hexToByteArray().toList(), response.idm.toList())
        assertEquals(0xFF.toByte(), response.statusFlag1)
        assertEquals(0xE2.toByte(), response.statusFlag2)
        assertEquals(0, response.nodeCode.size)
        assertEquals(0, response.data.size)
        assertFalse(response.isStatusSuccessful)
    }

    @Test
    fun testGetAreaInformationResponseFromByteArraySuccess() {
        val successBytes = "1025010203040506070800001234BEEF".hexToByteArray()

        val response = GetAreaInformationResponse.fromByteArray(successBytes)

        assertEquals("0102030405060708".hexToByteArray().toList(), response.idm.toList())
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertEquals("1234".hexToByteArray().toList(), response.nodeCode.toList())
        assertEquals("BEEF".hexToByteArray().toList(), response.data.toList())
        assertTrue(response.isStatusSuccessful)
    }

    @Test
    fun testGetAreaInformationResponseValidation() {
        val idm = IDM

        // Test success response with invalid node code
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationResponse(idm, 0x00, 0x00, byteArrayOf(0x12)) // Only 1 byte
        }

        // Test error response with non-empty node code
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationResponse(idm, 0xFF.toByte(), 0xE0.toByte(), "1234".hexToByteArray())
        }

        // Test error response with non-empty data
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationResponse(
                idm,
                0xFF.toByte(),
                0xE0.toByte(),
                ByteArray(0),
                "1234".hexToByteArray(),
            )
        }

        // Test success response with invalid data length
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationResponse(
                idm,
                0x00,
                0x00,
                "1234".hexToByteArray(),
                "12".hexToByteArray(),
            ) // Only 1 byte
        }

        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationResponse(
                idm,
                0x00,
                0x00,
                "1234".hexToByteArray(),
                "123456".hexToByteArray(),
            ) // 3 bytes
        }
    }

    @Test
    fun testGetAreaInformationResponseFromByteArrayValidation() {
        // Test too short data
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationResponse.fromByteArray(byteArrayOf(0x0B, 0x25)) // Too short
        }

        // Test invalid response code
        val invalidResponseCode = "0C26010203040506070800FFE0".hexToByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationResponse.fromByteArray(invalidResponseCode)
        }

        // Test length mismatch
        val lengthMismatch =
            "0B25010203040506070800FFE0".hexToByteArray() // Length says 11 but data is 12
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationResponse.fromByteArray(lengthMismatch)
        }

        // Test success response too short
        val tooShortSuccess =
            "0D2501020304050607080000AB".hexToByteArray() // Missing second byte of node code
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationResponse.fromByteArray(tooShortSuccess)
        }
    }

    @Test
    fun testGetAreaInformationResponseConstants() {
        assertEquals(0x25.toByte(), GetAreaInformationResponse.RESPONSE_CODE.toByte())
        assertEquals(12, GetAreaInformationResponse.MIN_ERROR_LENGTH)
        assertEquals(16, GetAreaInformationResponse.MIN_SUCCESS_LENGTH)
    }
}
