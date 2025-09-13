package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class GetAreaInformationCommandTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testGetAreaInformationCommandCreation() {
        val idm = IDM
        val nodeCode = "1234".hexToByteArray()

        val command = GetAreaInformationCommand(idm, nodeCode)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(nodeCode.toList(), command.nodeCode.toList())
    }

    @Test
    fun testGetAreaInformationCommandCreationWithNode() {
        val idm = IDM
        val area = Area(50, AreaAttribute.CAN_CREATE_SUB_AREA, 200, AreaAttribute.END_SUB_AREA)

        val command = GetAreaInformationCommand(idm, area)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(area.code.toList(), command.nodeCode.toList())
    }

    @Test
    fun testGetAreaInformationCommandToByteArray() {
        val idm = IDM
        val nodeCode = "ABCD".hexToByteArray()

        val command = GetAreaInformationCommand(idm, nodeCode)
        val bytes = command.toByteArray()

        // Check length (1 + 1 + 8 + 2 = 12 bytes)
        assertEquals(12, bytes.size)
        assertEquals(12.toByte(), bytes[0]) // Length

        // Check command code
        assertEquals(GetAreaInformationCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in idm.indices) {
            assertEquals("IDM byte $i", idm[i], bytes[2 + i])
        }

        // Check node code (2 bytes)
        assertEquals(nodeCode[0], bytes[10])
        assertEquals(nodeCode[1], bytes[11])
    }

    @Test
    fun testGetAreaInformationCommandFromByteArray() {
        val idm = IDM
        val nodeCode = "5678".hexToByteArray()

        val originalCommand = GetAreaInformationCommand(idm, nodeCode)
        val bytes = originalCommand.toByteArray()
        val parsedCommand = GetAreaInformationCommand.fromByteArray(bytes)

        assertEquals(idm.toList(), parsedCommand.idm.toList())
        assertEquals(nodeCode.toList(), parsedCommand.nodeCode.toList())
    }

    @Test
    fun testGetAreaInformationCommandValidation() {
        val idm = IDM

        // Test invalid node code length
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationCommand(idm, byteArrayOf(0x12)) // Only 1 byte
        }

        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationCommand(idm, byteArrayOf(0x12, 0x34, 0x56)) // 3 bytes
        }
    }

    @Test
    fun testGetAreaInformationCommandFromByteArrayValidation() {
        // Test invalid length
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationCommand.fromByteArray(byteArrayOf(0x0A, 0x24)) // Too short
        }

        // Test invalid command code
        val invalidCommandCode =
            byteArrayOf(
                0x0C,
                0x25, // Wrong command code (0x25 instead of 0x24)
                0x01,
                0x02,
                0x03,
                0x04,
                0x05,
                0x06,
                0x07,
                0x08, // IDM
                0x12,
                0x34, // Node code
            )
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationCommand.fromByteArray(invalidCommandCode)
        }

        // Test length mismatch
        val lengthMismatch =
            byteArrayOf(
                0x0B,
                0x24, // Wrong length (11 instead of 12)
                0x01,
                0x02,
                0x03,
                0x04,
                0x05,
                0x06,
                0x07,
                0x08, // IDM
                0x12,
                0x34, // Node code
            )
        assertThrows(IllegalArgumentException::class.java) {
            GetAreaInformationCommand.fromByteArray(lengthMismatch)
        }
    }
}
