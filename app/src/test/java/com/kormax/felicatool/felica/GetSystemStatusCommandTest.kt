package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for GetSystemStatusCommand */
class GetSystemStatusCommandTest {

    companion object {
        private const val IDM = "0123456789abcdef"
    }

    @Test
    fun testGetSystemStatusCommand_creation() {
        val idm = IDM.hexToByteArray()
        val command = GetSystemStatusCommand(idm)

        assertArrayEquals(idm, command.idm)
        assertArrayEquals(byteArrayOf(0x00, 0x00), command.reserved)
    }

    @Test
    fun testGetSystemStatusCommand_creation_withReserved() {
        val idm = IDM.hexToByteArray()
        val reserved = byteArrayOf(0x12, 0x34)
        val command = GetSystemStatusCommand(idm, reserved)

        assertArrayEquals(idm, command.idm)
        assertArrayEquals(reserved, command.reserved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusCommand_invalidReservedSize() {
        val idm = IDM.hexToByteArray()
        val invalidReserved = byteArrayOf(0x00) // Wrong size
        GetSystemStatusCommand(idm, invalidReserved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusCommand_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        GetSystemStatusCommand(invalidIdm)
    }

    @Test
    fun testGetSystemStatusCommand_toByteArray() {
        val idm = IDM.hexToByteArray()
        val command = GetSystemStatusCommand(idm)
        val bytes = command.toByteArray()

        // Check length (1 + 1 + 8 + 2 = 12 bytes)
        assertEquals(12, bytes.size)
        assertEquals(12.toByte(), bytes[0]) // Length

        // Check command code
        assertEquals(GetSystemStatusCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }

        // Check reserved bytes (2 bytes)
        assertEquals(0x00.toByte(), bytes[10])
        assertEquals(0x00.toByte(), bytes[11])
    }

    @Test
    fun testGetSystemStatusCommand_fromByteArray() {
        // Length(1) + CommandCode(1) + IDM(8) + Reserved(2) = 12 bytes
        val data = "0c38${IDM}0000".hexToByteArray()

        val command = GetSystemStatusCommand.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), command.idm)
        assertArrayEquals(byteArrayOf(0x00, 0x00), command.reserved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusCommand_fromByteArray_tooShort() {
        val shortData = "0b38${IDM}00".hexToByteArray() // Length says 11 but data is 12 bytes
        GetSystemStatusCommand.fromByteArray(shortData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusCommand_fromByteArray_wrongCommandCode() {
        val wrongCommandData =
            "0c37${IDM}0000".hexToByteArray() // Command code 0x37 instead of 0x38
        GetSystemStatusCommand.fromByteArray(wrongCommandData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetSystemStatusCommand_fromByteArray_lengthMismatch() {
        val wrongLengthData =
            "0e38${IDM}0000".hexToByteArray() // Length says 14 but data is 12 bytes
        GetSystemStatusCommand.fromByteArray(wrongLengthData)
    }

    @Test
    fun testGetSystemStatusCommand_roundTrip() {
        val idm = IDM.hexToByteArray()
        val command = GetSystemStatusCommand(idm)
        val bytes = command.toByteArray()
        val parsedCommand = GetSystemStatusCommand.fromByteArray(bytes)

        assertArrayEquals(command.idm, parsedCommand.idm)
    }
}
