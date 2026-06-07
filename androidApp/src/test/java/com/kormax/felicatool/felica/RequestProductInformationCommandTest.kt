package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestProductInformationCommand */
class RequestProductInformationCommandTest {

    companion object {
        private val TEST_IDM = "01020304050607ff".hexToByteArray()
        private val ANOTHER_IDM = "1122334455667788".hexToByteArray()
    }

    @Test
    fun testProductInformationCommand_creation() {
        val command = RequestProductInformationCommand(TEST_IDM)

        assertArrayEquals(TEST_IDM, command.idm)
        assertEquals("01020304050607FF", command.idm.toHexString().uppercase())
    }

    @Test
    fun testProductInformationCommand_toByteArray() {
        val command = RequestProductInformationCommand(TEST_IDM)
        val bytes = command.toByteArray()

        // Check length
        assertEquals(RequestProductInformationCommand.COMMAND_LENGTH, bytes.size)
        assertEquals(RequestProductInformationCommand.COMMAND_LENGTH.toByte(), bytes[0])

        // Check command code
        assertEquals(RequestProductInformationCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testProductInformationCommand_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        RequestProductInformationCommand(invalidIdm)
    }

    @Test
    fun testFromByteArray_basic() {
        // Create command
        val originalCommand = RequestProductInformationCommand(TEST_IDM)
        val data = originalCommand.toByteArray()

        val parsedCommand = RequestProductInformationCommand.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedCommand.idm)
    }

    @Test
    fun testFromByteArray_specificBytes() {
        // 0a3a01020304050607ff (length=10, command_code=0x3a, idm=01020304050607ff)
        val data = "0a3a01020304050607ff".hexToByteArray()

        val command = RequestProductInformationCommand.fromByteArray(data)

        assertArrayEquals(TEST_IDM, command.idm)
    }

    @Test
    fun testFromByteArray_anotherIdm() {
        // 0a3a1122334455667788 (length=10, command_code=0x3a, idm=1122334455667788)
        val data = "0a3a1122334455667788".hexToByteArray()

        val command = RequestProductInformationCommand.fromByteArray(data)

        assertArrayEquals(ANOTHER_IDM, command.idm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val data = "0a3a".hexToByteArray() // Too short
        RequestProductInformationCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = "0b3a01020304050607ff".hexToByteArray() // Length says 11, but actual is 10
        RequestProductInformationCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongCommandCode() {
        val data =
            "0a3901020304050607ff".hexToByteArray() // Wrong command code (0x39 instead of 0x3a)
        RequestProductInformationCommand.fromByteArray(data)
    }

    @Test
    fun testConstants() {
        assertEquals(0x3a.toShort(), RequestProductInformationCommand.COMMAND_CODE)
        assertEquals(
            10,
            RequestProductInformationCommand.COMMAND_LENGTH,
        ) // 1 (length) + 1 (command code) + 8 (IDM)
    }

    @Test
    fun testRoundTrip() {
        val originalCommand = RequestProductInformationCommand(ANOTHER_IDM)

        val bytes = originalCommand.toByteArray()
        val parsedCommand = RequestProductInformationCommand.fromByteArray(bytes)

        assertArrayEquals(originalCommand.idm, parsedCommand.idm)
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
            val command = RequestProductInformationCommand(idm)
            val bytes = command.toByteArray()
            val parsed = RequestProductInformationCommand.fromByteArray(bytes)

            assertArrayEquals("Failed for IDM: ${idm.toHexString()}", idm, parsed.idm)
        }
    }
}
