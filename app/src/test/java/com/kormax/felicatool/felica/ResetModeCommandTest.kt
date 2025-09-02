package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for ResetModeCommand */
class ResetModeCommandTest {

    companion object {
        private val TEST_IDM = "01020304050607ff".hexToByteArray()
        private val ANOTHER_IDM = "1122334455667788".hexToByteArray()
    }

    @Test
    fun testResetModeCommand_defaultCreation() {
        val command = ResetModeCommand(TEST_IDM)

        assertArrayEquals(TEST_IDM, command.idm)
        assertArrayEquals(ByteArray(2), command.reserved) // Should be all zeros
        assertEquals("01020304050607FF", command.idm.toHexString().uppercase())
    }

    @Test
    fun testResetModeCommand_withCustomReserved() {
        val customReserved = byteArrayOf(0x00, 0x00)
        val command = ResetModeCommand(TEST_IDM, customReserved)

        assertArrayEquals(TEST_IDM, command.idm)
        assertArrayEquals(customReserved, command.reserved)
    }

    @Test
    fun testResetModeCommand_toByteArray() {
        val command = ResetModeCommand(TEST_IDM)
        val bytes = command.toByteArray()

        // Check length
        assertEquals(ResetModeCommand.COMMAND_LENGTH, bytes.size)
        assertEquals(ResetModeCommand.COMMAND_LENGTH.toByte(), bytes[0])

        // Check command code
        assertEquals(ResetModeCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }

        // Check reserved bytes (should be 0x00 0x00)
        assertEquals(0x00.toByte(), bytes[10])
        assertEquals(0x00.toByte(), bytes[11])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testResetModeCommand_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        ResetModeCommand(invalidIdm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testResetModeCommand_invalidReservedSize() {
        val invalidReserved = byteArrayOf(0x00) // Wrong size
        ResetModeCommand(TEST_IDM, invalidReserved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testResetModeCommand_nonZeroReserved() {
        val invalidReserved = byteArrayOf(0x01, 0x00) // Non-zero reserved byte
        ResetModeCommand(TEST_IDM, invalidReserved)
    }

    @Test
    fun testFromByteArray_defaults() {
        // Create command with default parameters
        val originalCommand = ResetModeCommand(TEST_IDM)
        val data = originalCommand.toByteArray()

        val parsedCommand = ResetModeCommand.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedCommand.idm)
        assertArrayEquals(ByteArray(2), parsedCommand.reserved)
    }

    @Test
    fun testFromByteArray_specificBytes() {
        // 0c3e01020304050607ff0000 (length=12, command_code=0x3e, idm=01020304050607ff,
        // reserved=0000)
        val data = "0c3e01020304050607ff0000".hexToByteArray()

        val command = ResetModeCommand.fromByteArray(data)

        assertArrayEquals(TEST_IDM, command.idm)
        assertArrayEquals(byteArrayOf(0x00, 0x00), command.reserved)
    }

    @Test
    fun testFromByteArray_anotherIdm() {
        // 0c3e11223344556677880000 (length=12, command_code=0x3e, idm=1122334455667788,
        // reserved=0000)
        val data = "0c3e11223344556677880000".hexToByteArray()

        val command = ResetModeCommand.fromByteArray(data)

        assertArrayEquals(ANOTHER_IDM, command.idm)
        assertArrayEquals(byteArrayOf(0x00, 0x00), command.reserved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val data = "0c3e".hexToByteArray() // Too short
        ResetModeCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = "0d3e01020304050607ff0000".hexToByteArray() // Length says 13, but actual is 12
        ResetModeCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongCommandCode() {
        val data =
            "0c3d01020304050607ff0000".hexToByteArray() // Wrong command code (0x3d instead of 0x3e)
        ResetModeCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_nonZeroReserved() {
        // 0c3e01020304050607ff0100 - has 0x01 in first reserved byte
        val data = "0c3e01020304050607ff0100".hexToByteArray()
        ResetModeCommand.fromByteArray(data)
    }

    @Test
    fun testConstants() {
        assertEquals(0x3E.toShort(), ResetModeCommand.COMMAND_CODE)
        assertEquals(
            12,
            ResetModeCommand.COMMAND_LENGTH,
        ) // 1 (length) + 1 (command code) + 8 (IDM) + 2 (reserved)
    }

    @Test
    fun testRoundTrip() {
        val originalCommand = ResetModeCommand(ANOTHER_IDM)

        val bytes = originalCommand.toByteArray()
        val parsedCommand = ResetModeCommand.fromByteArray(bytes)

        assertArrayEquals(originalCommand.idm, parsedCommand.idm)
        assertArrayEquals(originalCommand.reserved, parsedCommand.reserved)
        assertArrayEquals(bytes, parsedCommand.toByteArray())
    }

    @Test
    fun testMultipleIdms() {
        val testIdms =
            listOf(
                "01020304050607ff".hexToByteArray(),
                "1122334455667788".hexToByteArray(),
                "0000000000000000".hexToByteArray(),
                "ffffffffffffffff".hexToByteArray(),
            )

        for (idm in testIdms) {
            val command = ResetModeCommand(idm)
            val bytes = command.toByteArray()
            val parsed = ResetModeCommand.fromByteArray(bytes)

            assertArrayEquals("Failed for IDM: ${idm.toHexString()}", idm, parsed.idm)
            assertArrayEquals(
                "Failed for reserved with IDM: ${idm.toHexString()}",
                ByteArray(2),
                parsed.reserved,
            )
        }
    }
}
