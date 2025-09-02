package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestSpecificationVersionCommand */
class RequestSpecificationVersionCommandTest {

    companion object {
        private const val IDM = "0123456789abcdef"
        private val RESERVED = byteArrayOf(0x00, 0x00)
    }

    @Test
    fun testRequestSpecificationVersionCommand_creation() {
        val idm = IDM.hexToByteArray()
        val command = RequestSpecificationVersionCommand(idm)

        assertArrayEquals(idm, command.idm)
        assertArrayEquals(RESERVED, command.reserved)
    }

    @Test
    fun testRequestSpecificationVersionCommand_creation_withReserved() {
        val idm = IDM.hexToByteArray()
        val command = RequestSpecificationVersionCommand(idm, RESERVED)

        assertArrayEquals(idm, command.idm)
        assertArrayEquals(RESERVED, command.reserved)
    }

    @Test
    fun testRequestSpecificationVersionCommand_toByteArray() {
        val idm = IDM.hexToByteArray()
        val command = RequestSpecificationVersionCommand(idm)
        val bytes = command.toByteArray()

        // Check length (1 + 1 + 8 + 2 = 12 bytes)
        assertEquals(12, bytes.size)
        assertEquals(12.toByte(), bytes[0]) // Length

        // Check command code
        assertEquals(RequestSpecificationVersionCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }

        // Check reserved (2 bytes)
        assertEquals(0x00.toByte(), bytes[10])
        assertEquals(0x00.toByte(), bytes[11])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestSpecificationVersionCommand_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        RequestSpecificationVersionCommand(invalidIdm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestSpecificationVersionCommand_invalidReservedSize() {
        val idm = IDM.hexToByteArray()
        val invalidReserved = byteArrayOf(0x00) // Too short
        RequestSpecificationVersionCommand(idm, invalidReserved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestSpecificationVersionCommand_invalidReservedValue() {
        val idm = IDM.hexToByteArray()
        val invalidReserved = byteArrayOf(0x00, 0x01) // Not 0000h
        RequestSpecificationVersionCommand(idm, invalidReserved)
    }

    @Test
    fun testFromByteArray_valid() {
        // 0c3c + IDM (8 bytes) + reserved (2 bytes)
        val data = "0c3c${IDM}0000".hexToByteArray()

        val command = RequestSpecificationVersionCommand.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), command.idm)
        assertArrayEquals(RESERVED, command.reserved)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val shortData = "0b3c${IDM}00".hexToByteArray() // 11 bytes instead of 12
        RequestSpecificationVersionCommand.fromByteArray(shortData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooLong() {
        val longData = "0d3c${IDM}000000".hexToByteArray() // 13 bytes instead of 12
        RequestSpecificationVersionCommand.fromByteArray(longData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongCommandCode() {
        val wrongCommandData =
            "0c3d${IDM}0000".hexToByteArray() // Command code 0x3D instead of 0x3C
        RequestSpecificationVersionCommand.fromByteArray(wrongCommandData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = "0d3c${IDM}0000".hexToByteArray() // Length says 13, but actual length is 12
        RequestSpecificationVersionCommand.fromByteArray(data)
    }

    @Test
    fun testRequestSpecificationVersionCommand_roundTrip() {
        val idm = IDM.hexToByteArray()
        val command = RequestSpecificationVersionCommand(idm)
        val bytes = command.toByteArray()
        val parsedCommand = RequestSpecificationVersionCommand.fromByteArray(bytes)

        assertArrayEquals(command.idm, parsedCommand.idm)
        assertArrayEquals(command.reserved, parsedCommand.reserved)
    }
}
