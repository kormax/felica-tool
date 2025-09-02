package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestResponseCommand */
class RequestResponseCommandTest {

    companion object {
        private const val IDM = "0123456789abcdef"
    }

    @Test
    fun testRequestResponseCommand_creation() {
        val idm = IDM.hexToByteArray()
        val command = RequestResponseCommand(idm)

        assertArrayEquals(idm, command.idm)
    }

    @Test
    fun testRequestResponseCommand_toByteArray() {
        val idm = IDM.hexToByteArray()
        val command = RequestResponseCommand(idm)
        val bytes = command.toByteArray()

        // Check length (1 + 1 + 8 = 10 bytes)
        assertEquals(10, bytes.size)
        assertEquals(10.toByte(), bytes[0]) // Length

        // Check command code
        assertEquals(RequestResponseCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestResponseCommand_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        RequestResponseCommand(invalidIdm)
    }

    @Test
    fun testFromByteArray_valid() {
        // 0a04 + IDM (8 bytes)
        val data = "0a04${IDM}".hexToByteArray()

        val command = RequestResponseCommand.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), command.idm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val shortData = "0904${IDM.substring(0, 14)}".hexToByteArray() // 9 bytes instead of 10
        RequestResponseCommand.fromByteArray(shortData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongCommandCode() {
        val wrongCommandData = "0a05${IDM}".hexToByteArray() // Command code 0x05 instead of 0x04
        RequestResponseCommand.fromByteArray(wrongCommandData)
    }

    @Test
    fun testRequestResponseCommand_roundTrip() {
        val idm = IDM.hexToByteArray()
        val command = RequestResponseCommand(idm)
        val bytes = command.toByteArray()
        val parsedCommand = RequestResponseCommand.fromByteArray(bytes)

        assertArrayEquals(command.idm, parsedCommand.idm)
    }
}
