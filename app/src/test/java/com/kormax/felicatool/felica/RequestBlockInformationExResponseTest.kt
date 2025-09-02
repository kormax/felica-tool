package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class RequestBlockInformationExResponseTest {

    @Test
    fun `test fromByteArray with valid data`() {
        // Sample data: length(1), response_code(1), idm(8), status_flag1(1), status_flag2(1), number_of_blocks(1), assigned(2),
        // free(2), assigned(2), free(2)
        val data =
            byteArrayOf(
                0x15.toByte(), // length = 1 + 1 + 8 + 1 + 1 + 1 + 4 + 4 = 21 = 0x15
                0x1F.toByte(), // response code (corrected)
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(), // IDM
                0x00.toByte(), // status flag 1 (success)
                0x00.toByte(), // status flag 2 (success)
                0x02.toByte(), // number of blocks
                0x10.toByte(),
                0x00.toByte(), // assigned block 1
                0x20.toByte(),
                0x00.toByte(), // free block 1
                0x30.toByte(),
                0x00.toByte(), // assigned block 2
                0x40.toByte(),
                0x00.toByte(), // free block 2
            )

        val response = RequestBlockInformationExResponse.fromByteArray(data)

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
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertTrue(response.isStatusSuccessful)
        assertEquals(2, response.assignedBlockCount.size)
        assertEquals(2, response.freeBlockCount.size)
        assertEquals(0x0010, response.assignedBlockCount[0].toInt())
        assertEquals(0x0020, response.freeBlockCount[0].toInt())
        assertEquals(0x0030, response.assignedBlockCount[1].toInt())
        assertEquals(0x0040, response.freeBlockCount[1].toInt())
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
        val assignedBlockCount =
            arrayOf(CountInformation.fromInt(0x0010), CountInformation.fromInt(0x0030))
        val freeBlockCount =
            arrayOf(CountInformation.fromInt(0x0020), CountInformation.fromInt(0x0040))
        val original = RequestBlockInformationExResponse(idm, 0x00, 0x00, assignedBlockCount, freeBlockCount)

        val data = original.toByteArray()
        val parsed = RequestBlockInformationExResponse.fromByteArray(data)

        assertArrayEquals(original.idm, parsed.idm)
        assertEquals(original.statusFlag1, parsed.statusFlag1)
        assertEquals(original.statusFlag2, parsed.statusFlag2)
        assertEquals(original.assignedBlockCount.size, parsed.assignedBlockCount.size)
        assertEquals(original.freeBlockCount.size, parsed.freeBlockCount.size)
        for (i in 0 until original.assignedBlockCount.size) {
            assertEquals(
                original.assignedBlockCount[i].toInt(),
                parsed.assignedBlockCount[i].toInt(),
            )
            assertEquals(original.freeBlockCount[i].toInt(), parsed.freeBlockCount[i].toInt())
        }
    }

    @Test
    fun `test fromByteArray with invalid length`() {
        val data = byteArrayOf(0x10.toByte(), 0x0F.toByte()) // length mismatch

        try {
            RequestBlockInformationExResponse.fromByteArray(data)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `test fromByteArray with invalid response code`() {
        val data =
            byteArrayOf(
                0x0C.toByte(), // length
                0x00.toByte(), // invalid response code
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(), // IDM
                0x00.toByte(), // status flag 1
                0x00.toByte(), // status flag 2
            )

        try {
            RequestBlockInformationExResponse.fromByteArray(data)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `test constructor with mismatched array sizes`() {
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
        val assignedBlockCount = arrayOf(CountInformation.fromInt(0x0010))
        val freeBlockCount =
            arrayOf(CountInformation.fromInt(0x0020), CountInformation.fromInt(0x0040))

        try {
            RequestBlockInformationExResponse(idm, 0x00, 0x00, assignedBlockCount, freeBlockCount)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
