package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for PollingCommand */
class PollingCommandTest {

    companion object {
        private val SYSTEM_CODE_FE0F = "fe0f".hexToByteArray()
        private val SYSTEM_CODE_FE00 = "fe00".hexToByteArray()
        private val SYSTEM_CODE_0003 = "0003".hexToByteArray()
        private val SYSTEM_CODE_8008 = "8008".hexToByteArray()
    }

    @Test
    fun testPollingCommand_creation() {
        val command = PollingCommand(SYSTEM_CODE_FE0F, RequestCode.NO_REQUEST, TimeSlot.SLOT_1)

        assertArrayEquals(SYSTEM_CODE_FE0F, command.systemCode)
        assertEquals(RequestCode.NO_REQUEST, command.requestCode)
        assertEquals(TimeSlot.SLOT_1, command.timeSlot)
        assertEquals("FE0F", command.systemCode.toHexString().uppercase())
    }

    @Test
    fun testPollingCommand_toByteArray() {
        val command = PollingCommand(SYSTEM_CODE_FE0F, RequestCode.NO_REQUEST, TimeSlot.SLOT_1)
        val bytes = command.toByteArray()

        // Check length
        assertEquals(6, bytes.size)
        assertEquals(6.toByte(), bytes[0]) // Length

        // Check command code
        assertEquals(PollingCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check system code
        assertEquals(SYSTEM_CODE_FE0F[0], bytes[2])
        assertEquals(SYSTEM_CODE_FE0F[1], bytes[3])

        // Check request code
        assertEquals(RequestCode.NO_REQUEST.value, bytes[4])

        // Check time slot
        assertEquals(TimeSlot.SLOT_1.value, bytes[5])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPollingCommand_invalidSystemCodeSize() {
        val invalidSystemCode = byteArrayOf(0x01.toByte()) // Too short
        PollingCommand(invalidSystemCode, RequestCode.NO_REQUEST, TimeSlot.SLOT_1)
    }

    @Test
    fun testFromByteArray_basic() {
        // 0600fe0f0000
        val data = "0600fe0f0000".hexToByteArray()

        val command = PollingCommand.fromByteArray(data)

        assertArrayEquals(SYSTEM_CODE_FE0F, command.systemCode)
        assertEquals(RequestCode.NO_REQUEST, command.requestCode)
        assertEquals(TimeSlot.SLOT_1, command.timeSlot)
    }

    @Test
    fun testFromByteArray_systemCodeRequest() {
        // 0600fe000000
        val data = "0600fe000000".hexToByteArray()

        val command = PollingCommand.fromByteArray(data)

        assertArrayEquals(SYSTEM_CODE_FE00, command.systemCode)
        assertEquals(RequestCode.NO_REQUEST, command.requestCode)
        assertEquals(TimeSlot.SLOT_1, command.timeSlot)
    }

    @Test
    fun testFromByteArray_communicationPerformanceRequest() {
        // 060000030000
        val data = "060000030000".hexToByteArray()

        val command = PollingCommand.fromByteArray(data)

        assertArrayEquals(SYSTEM_CODE_0003, command.systemCode)
        assertEquals(RequestCode.NO_REQUEST, command.requestCode)
        assertEquals(TimeSlot.SLOT_1, command.timeSlot)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_invalidLength() {
        val data = "05".hexToByteArray() // Too short
        PollingCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = "0700fe0f0000".hexToByteArray() // Length says 7, but actual length is 6
        PollingCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_invalidCommandCode() {
        val data = "0601fe0f0000".hexToByteArray() // Wrong command code
        PollingCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_invalidRequestCode() {
        val data = "0600fe0f0300".hexToByteArray() // Invalid request code
        PollingCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_invalidTimeSlot() {
        val data = "0600fe0f0010".hexToByteArray() // Invalid time slot
        PollingCommand.fromByteArray(data)
    }

    @Test
    fun testPollingCommand_defaultConstructor() {
        val command = PollingCommand()

        // Check default system code (wildcard FFFF)
        assertArrayEquals(byteArrayOf(0xFF.toByte(), 0xFF.toByte()), command.systemCode)
        assertEquals("FFFF", command.systemCode.toHexString().uppercase())

        // Check default request code (NO_REQUEST = 0)
        assertEquals(RequestCode.NO_REQUEST, command.requestCode)
        assertEquals(0x00.toByte(), command.requestCode.value)

        // Check default time slot (SLOT_1 = 0)
        assertEquals(TimeSlot.SLOT_1, command.timeSlot)
        assertEquals(0x00.toByte(), command.timeSlot.value)
    }
}
