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
        assertArrayEquals(ByteArray(0), command.trailingData)
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

    @Test
    fun testRequestResponseCommand_trailingDataToByteArray() {
        val idm = IDM.hexToByteArray()
        val trailingData = "55".hexToByteArray()
        val command = RequestResponseCommand(idm, trailingData)
        val bytes = command.toByteArray()

        assertEquals(11, bytes.size)
        assertEquals(11.toByte(), bytes[0])
        assertEquals(RequestResponseCommand.COMMAND_CODE.toByte(), bytes[1])
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }
        assertEquals(trailingData[0], bytes[10])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestResponseCommand_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        RequestResponseCommand(invalidIdm)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestResponseCommand_frameTooLong() {
        RequestResponseCommand(
            IDM.hexToByteArray(),
            ByteArray(
                RequestResponseCommand.MAX_FRAME_LENGTH - RequestResponseCommand.MIN_LENGTH + 1
            ),
        )
    }

    @Test
    fun testFromByteArray_valid() {
        // 0a04 + IDM (8 bytes)
        val data = "0a04${IDM}".hexToByteArray()

        val command = RequestResponseCommand.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), command.idm)
        assertArrayEquals(ByteArray(0), command.trailingData)
    }

    @Test
    fun testFromByteArray_trailingData() {
        val trailingData = "aabb".hexToByteArray()
        val data = "0c04${IDM}aabb".hexToByteArray()

        val command = RequestResponseCommand.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), command.idm)
        assertArrayEquals(trailingData, command.trailingData)
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
