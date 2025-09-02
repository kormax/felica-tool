package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for SetParameterCommand */
class SetParameterCommandTest {

    companion object {
        private val TEST_IDM = "01020304050607ff".hexToByteArray()
        private val ANOTHER_IDM = "1122334455667788".hexToByteArray()
    }

    @Test
    fun testSetParameterCommand_defaultCreation() {
        val command = SetParameterCommand(TEST_IDM)

        assertArrayEquals(TEST_IDM, command.idm)
        assertEquals(SetParameterCommand.EncryptionType.SRM_TYPE1, command.encryptionType)
        assertEquals(SetParameterCommand.PacketType.NODECODESIZE_2, command.packetType)
    }

    @Test
    fun testSetParameterCommand_withCustomParameters() {
        val command =
            SetParameterCommand(
                TEST_IDM,
                SetParameterCommand.EncryptionType.SRM_TYPE2,
                SetParameterCommand.PacketType.NODECODESIZE_4,
            )

        assertArrayEquals(TEST_IDM, command.idm)
        assertEquals(SetParameterCommand.EncryptionType.SRM_TYPE2, command.encryptionType)
        assertEquals(SetParameterCommand.PacketType.NODECODESIZE_4, command.packetType)
    }

    @Test
    fun testSetParameterCommand_toByteArray_defaults() {
        val command = SetParameterCommand(TEST_IDM)
        val bytes = command.toByteArray()

        // Check length
        assertEquals(SetParameterCommand.COMMAND_LENGTH, bytes.size)
        assertEquals(SetParameterCommand.COMMAND_LENGTH.toByte(), bytes[0])

        // Check command code
        assertEquals(SetParameterCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }

        // Check reserved bytes (4 bytes after IDM)
        assertEquals(0x00.toByte(), bytes[10])
        assertEquals(0x00.toByte(), bytes[11])
        assertEquals(0x00.toByte(), bytes[12])
        assertEquals(0x00.toByte(), bytes[13])

        // Check encryption type (SRM_TYPE1 -> 0x0)
        assertEquals(0x00.toByte(), bytes[14])

        // Check packet type (NODECODESIZE_2 -> 0x0)
        assertEquals(0x00.toByte(), bytes[15])

        // Check reserved bytes (2 bytes at end)
        assertEquals(0x00.toByte(), bytes[16])
        assertEquals(0x00.toByte(), bytes[17])
    }

    @Test
    fun testSetParameterCommand_toByteArray_type2() {
        val command =
            SetParameterCommand(
                TEST_IDM,
                SetParameterCommand.EncryptionType.SRM_TYPE2,
                SetParameterCommand.PacketType.NODECODESIZE_4,
            )
        val bytes = command.toByteArray()

        // Check encryption type (SRM_TYPE2 -> 0x1)
        assertEquals(0x01.toByte(), bytes[14])

        // Check packet type (NODECODESIZE_4 -> 0x1)
        assertEquals(0x01.toByte(), bytes[15])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSetParameterCommand_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        SetParameterCommand(invalidIdm)
    }

    @Test
    fun testFromByteArray_defaults() {
        // Create command with default parameters
        val originalCommand = SetParameterCommand(TEST_IDM)
        val data = originalCommand.toByteArray()

        val parsedCommand = SetParameterCommand.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedCommand.idm)
        assertEquals(SetParameterCommand.EncryptionType.SRM_TYPE1, parsedCommand.encryptionType)
        assertEquals(SetParameterCommand.PacketType.NODECODESIZE_2, parsedCommand.packetType)
    }

    @Test
    fun testFromByteArray_type2() {
        // Create command with TYPE2 parameters
        val originalCommand =
            SetParameterCommand(
                ANOTHER_IDM,
                SetParameterCommand.EncryptionType.SRM_TYPE2,
                SetParameterCommand.PacketType.NODECODESIZE_4,
            )
        val data = originalCommand.toByteArray()

        val parsedCommand = SetParameterCommand.fromByteArray(data)

        assertArrayEquals(ANOTHER_IDM, parsedCommand.idm)
        assertEquals(SetParameterCommand.EncryptionType.SRM_TYPE2, parsedCommand.encryptionType)
        assertEquals(SetParameterCommand.PacketType.NODECODESIZE_4, parsedCommand.packetType)
    }

    @Test
    fun testFromByteArray_specificBytes() {
        // 122001020304050607ff00000000000000
        val data = "122001020304050607ff0000000000000000".hexToByteArray()

        val command = SetParameterCommand.fromByteArray(data)

        assertArrayEquals(TEST_IDM, command.idm)
        assertEquals(SetParameterCommand.EncryptionType.SRM_TYPE1, command.encryptionType)
        assertEquals(SetParameterCommand.PacketType.NODECODESIZE_2, command.packetType)
    }

    @Test
    fun testFromByteArray_type2Specific() {
        // 122011223344556677880000000001100000
        val data = "122011223344556677880000000001010000".hexToByteArray()

        val command = SetParameterCommand.fromByteArray(data)

        assertArrayEquals(ANOTHER_IDM, command.idm)
        assertEquals(SetParameterCommand.EncryptionType.SRM_TYPE2, command.encryptionType)
        assertEquals(SetParameterCommand.PacketType.NODECODESIZE_4, command.packetType)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val data = "1200".hexToByteArray() // Too short
        SetParameterCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data =
            "132001020304050607ff00000000000000"
                .hexToByteArray() // Length says 19, but actual is 18
        SetParameterCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongCommandCode() {
        val data =
            "120101020304050607ff00000000000000"
                .hexToByteArray() // Wrong command code (0x01 instead of 0x20)
        SetParameterCommand.fromByteArray(data)
    }

    @Test
    fun testConstants() {
        assertEquals(0x20.toShort(), SetParameterCommand.COMMAND_CODE)
        assertEquals(0x00, SetParameterCommand.EncryptionType.SRM_TYPE1.value)
        assertEquals(0x01, SetParameterCommand.EncryptionType.SRM_TYPE2.value)
        assertEquals(0x0, SetParameterCommand.PacketType.NODECODESIZE_2.value)
        assertEquals(0x1, SetParameterCommand.PacketType.NODECODESIZE_4.value)
    }

    @Test
    fun testEnumFromValue() {
        // Test EncryptionType.fromValue
        assertEquals(
            SetParameterCommand.EncryptionType.SRM_TYPE1,
            SetParameterCommand.EncryptionType.fromValue(0x00),
        )
        assertEquals(
            SetParameterCommand.EncryptionType.SRM_TYPE2,
            SetParameterCommand.EncryptionType.fromValue(0x01),
        )

        // Test PacketType.fromValue
        assertEquals(
            SetParameterCommand.PacketType.NODECODESIZE_2,
            SetParameterCommand.PacketType.fromValue(0x0),
        )
        assertEquals(
            SetParameterCommand.PacketType.NODECODESIZE_4,
            SetParameterCommand.PacketType.fromValue(0x1),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testEncryptionType_invalidValue() {
        SetParameterCommand.EncryptionType.fromValue(0x99)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPacketType_invalidValue() {
        SetParameterCommand.PacketType.fromValue(0x99)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_nonZeroReservedD0D3() {
        // 122001020304050607ff010000000000 - has 0x01 in first reserved byte
        val data = "122001020304050607ff010000000000".hexToByteArray()
        SetParameterCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_nonZeroReservedD6D7() {
        // 122001020304050607ff000000000001 - has 0x01 in last reserved byte
        val data = "122001020304050607ff000000000001".hexToByteArray()
        SetParameterCommand.fromByteArray(data)
    }

    @Test
    fun testRoundTrip() {
        val originalCommand =
            SetParameterCommand(
                TEST_IDM,
                SetParameterCommand.EncryptionType.SRM_TYPE2,
                SetParameterCommand.PacketType.NODECODESIZE_4,
            )

        val bytes = originalCommand.toByteArray()
        val parsedCommand = SetParameterCommand.fromByteArray(bytes)

        assertArrayEquals(originalCommand.idm, parsedCommand.idm)
        assertEquals(originalCommand.encryptionType, parsedCommand.encryptionType)
        assertEquals(originalCommand.packetType, parsedCommand.packetType)
        assertArrayEquals(bytes, parsedCommand.toByteArray())
    }
}
