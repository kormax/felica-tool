package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class RequestBlockInformationResponseTest {

    @Test
    fun `test fromByteArray with valid data`() {
        // Sample data: length(1), response_code(1), idm(8), number_of_blocks(1), block_info(2)
        val data =
            byteArrayOf(
                0x0F.toByte(), // length
                0x0F.toByte(), // response code
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(), // IDM
                0x02.toByte(), // number of blocks
                0x10.toByte(),
                0x00.toByte(), // block 1
                0x20.toByte(),
                0x00.toByte(), // block 2
            )

        val response = RequestBlockInformationResponse.fromByteArray(data)

        assertArrayEquals(
            byteArrayOf(
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(),
            ),
            response.idm,
        )
        assertEquals(2, response.blockCountInformation.size)
        assertEquals(0x0010, response.blockCountInformation[0].toInt())
        assertEquals(0x0020, response.blockCountInformation[1].toInt())
    }

    @Test
    fun `test toByteArray round trip`() {
        val idm =
            byteArrayOf(
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(),
            )
        val blockInfos = arrayOf(CountInformation.fromInt(0x0010), CountInformation.fromInt(0x0020))
        val original = RequestBlockInformationResponse(idm, blockInfos)

        val data = original.toByteArray()
        val parsed = RequestBlockInformationResponse.fromByteArray(data)

        assertArrayEquals(original.idm, parsed.idm)
        assertEquals(
            original.blockCountInformation.size,
            parsed.blockCountInformation.size,
        )
        for (i in 0 until original.blockCountInformation.size) {
            assertEquals(
                original.blockCountInformation[i].toInt(),
                parsed.blockCountInformation[i].toInt(),
            )
        }
    }

    @Test
    fun `test fromByteArray with invalid length`() {
        val data = byteArrayOf(0x10.toByte(), 0x0F.toByte()) // length mismatch

        try {
            RequestBlockInformationResponse.fromByteArray(data)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `test fromByteArray with invalid response code`() {
        val data =
            byteArrayOf(
                0x0A.toByte(), // length
                0x00.toByte(), // invalid response code
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(), // IDM
            )

        try {
            RequestBlockInformationResponse.fromByteArray(data)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
