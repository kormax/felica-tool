package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class WriteWithoutEncryptionResponseTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testWriteWithoutEncryptionResponseToByteArray() {
        val response = WriteWithoutEncryptionResponse(IDM, 0x00, 0x00)
        val data = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + status_flags(2) = 12 bytes
        assertEquals(12, data.size)
        assertEquals(12.toByte(), data[0]) // Length
        assertEquals(0x09.toByte(), data[1]) // Response code
        assertEquals(IDM.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(0x00.toByte(), data[10]) // Status flag 1
        assertEquals(0x00.toByte(), data[11]) // Status flag 2
    }

    @Test
    fun testWriteWithoutEncryptionResponseFromByteArray() {
        val rawData =
            ("0C" + // Length: 12
                    "09" + // Response code
                    "0102030405060708" + // IDM
                    "00" + // Status flag 1 (success)
                    "00" // Status flag 2
                )
                .hexToByteArray()

        val response = WriteWithoutEncryptionResponse.fromByteArray(rawData)

        assertEquals(IDM.toList(), response.idm.toList())
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
    }

    @Test
    fun testWriteWithoutEncryptionResponseWithErrorFlags() {
        val rawData =
            ("0C" + // Length: 12
                    "09" + // Response code
                    "0102030405060708" + // IDM
                    "01" + // Status flag 1 (error)
                    "A1" // Status flag 2 (specific error code)
                )
                .hexToByteArray()

        val response = WriteWithoutEncryptionResponse.fromByteArray(rawData)

        assertEquals(0x01.toByte(), response.statusFlag1)
        assertEquals(0xA1.toByte(), response.statusFlag2)
    }

    @Test
    fun testWriteWithoutEncryptionResponseRoundTrip() {
        val original = WriteWithoutEncryptionResponse(IDM, 0x00, 0x00)
        val serialized = original.toByteArray()
        val parsed = WriteWithoutEncryptionResponse.fromByteArray(serialized)

        assertEquals(original.idm.toList(), parsed.idm.toList())
        assertEquals(original.statusFlag1, parsed.statusFlag1)
        assertEquals(original.statusFlag2, parsed.statusFlag2)
    }
}
