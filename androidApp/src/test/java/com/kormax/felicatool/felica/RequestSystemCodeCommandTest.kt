package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestSystemCodeCommand */
class RequestSystemCodeCommandTest {

    companion object {
        private const val IDM = "0123456789abcdef"
    }

    @Test
    fun testRequestSystemCodeCommand_creation() {
        val idm = IDM.hexToByteArray()
        val command = RequestSystemCodeCommand(idm)

        assertArrayEquals(idm, command.idm)
    }

    @Test
    fun testRequestSystemCodeCommand_toByteArray() {
        val idm = IDM.hexToByteArray()
        val command = RequestSystemCodeCommand(idm)
        val bytes = command.toByteArray()

        // Check length (1 + 1 + 8 = 10 bytes)
        assertEquals(10, bytes.size)
        assertEquals(10.toByte(), bytes[0]) // Length

        // Check command code
        assertEquals(RequestSystemCodeCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestSystemCodeCommand_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        RequestSystemCodeCommand(invalidIdm)
    }

    @Test
    fun testFromByteArray_valid() {
        // 0a0c + IDM (8 bytes)
        val data = "0a0c${IDM}".hexToByteArray()

        val command = RequestSystemCodeCommand.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), command.idm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val shortData = "090c${IDM.substring(0, 14)}".hexToByteArray() // 9 bytes instead of 10
        RequestSystemCodeCommand.fromByteArray(shortData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongCommandCode() {
        val wrongCommandData = "0a0d${IDM}".hexToByteArray() // Command code 0x0d instead of 0x0c
        RequestSystemCodeCommand.fromByteArray(wrongCommandData)
    }

    @Test
    fun testRequestSystemCodeCommand_roundTrip() {
        val idm = IDM.hexToByteArray()
        val command = RequestSystemCodeCommand(idm)
        val bytes = command.toByteArray()
        val parsedCommand = RequestSystemCodeCommand.fromByteArray(bytes)

        assertArrayEquals(command.idm, parsedCommand.idm)
    }
}
