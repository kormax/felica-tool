package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestCodeListCommand */
class RequestCodeListCommandTest {

    companion object {
        private const val IDM = "0123456789abcdef"
        private val PARENT_NODE_CODE = "ffff".hexToByteArray()
        private const val INDEX = 0
    }

    @Test
    fun testRequestCodeListCommand_creation_withNode() {
        val idm = IDM.hexToByteArray()
        val parentNode = Service(52, ServiceAttribute.RandomRwWithoutKey)
        val command = RequestCodeListCommand(idm, parentNode, INDEX)

        assertArrayEquals(idm, command.idm)
        assertArrayEquals(parentNode.code, command.parentNodeCode)
        assertEquals(INDEX, command.index)
    }

    @Test
    fun testRequestCodeListCommand_toByteArray() {
        val idm = IDM.hexToByteArray()
        val command = RequestCodeListCommand(idm, PARENT_NODE_CODE, INDEX)
        val bytes = command.toByteArray()

        // Check length (1 + 1 + 8 + 2 + 2 = 14 bytes)
        assertEquals(14, bytes.size)
        assertEquals(14.toByte(), bytes[0]) // Length

        // Check command code
        assertEquals(RequestCodeListCommand.COMMAND_CODE.toByte(), bytes[1])

        // Check IDM (8 bytes)
        for (i in 0..7) {
            assertEquals(idm[i], bytes[2 + i])
        }

        // Check parent node code (2 bytes)
        assertArrayEquals(PARENT_NODE_CODE, bytes.sliceArray(10..11))

        // Check index (2 bytes, little endian)
        assertEquals((INDEX and 0xFF).toByte(), bytes[12])
        assertEquals(((INDEX shr 8) and 0xFF).toByte(), bytes[13])
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestCodeListCommand_invalidIdmSize() {
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        RequestCodeListCommand(invalidIdm, PARENT_NODE_CODE, INDEX)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestCodeListCommand_invalidParentNodeCodeSize() {
        val idm = IDM.hexToByteArray()
        val invalidParentNodeCode = byteArrayOf(0xFF.toByte()) // Too short
        RequestCodeListCommand(idm, invalidParentNodeCode, INDEX)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestCodeListCommand_invalidIndex() {
        val idm = IDM.hexToByteArray()
        RequestCodeListCommand(idm, PARENT_NODE_CODE, -1) // Negative index
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestCodeListCommand_indexTooLarge() {
        val idm = IDM.hexToByteArray()
        RequestCodeListCommand(idm, PARENT_NODE_CODE, 0x10000) // Too large
    }

    @Test
    fun testFromByteArray_valid() {
        // 0e1a + IDM (8 bytes) + parent_node_code (2 bytes) + index (2 bytes)
        val data = "0e1a${IDM}ffff0000".hexToByteArray()

        val command = RequestCodeListCommand.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), command.idm)
        assertArrayEquals(PARENT_NODE_CODE, command.parentNodeCode)
        assertEquals(INDEX, command.index)
    }

    @Test
    fun testFromByteArray_withNonZeroIndex() {
        val index = 0x1234
        val data = "0e1a${IDM}ffff3412".hexToByteArray() // index 0x1234 in little endian

        val command = RequestCodeListCommand.fromByteArray(data)

        assertArrayEquals(IDM.hexToByteArray(), command.idm)
        assertArrayEquals(PARENT_NODE_CODE, command.parentNodeCode)
        assertEquals(index, command.index)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val shortData = "0d0e${IDM}ffff00".hexToByteArray() // 13 bytes instead of 14
        RequestCodeListCommand.fromByteArray(shortData)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongCommandCode() {
        val wrongCommandData =
            "0e0f${IDM}ffff0000".hexToByteArray() // Command code 0x0f instead of 0x0e
        RequestCodeListCommand.fromByteArray(wrongCommandData)
    }

    @Test
    fun testRequestCodeListCommand_roundTrip_withNode() {
        val idm = IDM.hexToByteArray()
        val parentNode = Service(1023, ServiceAttribute.RandomRwWithoutKey)
        val command = RequestCodeListCommand(idm, parentNode, INDEX)
        val bytes = command.toByteArray()
        val parsedCommand = RequestCodeListCommand.fromByteArray(bytes)

        assertArrayEquals(command.idm, parsedCommand.idm)
        assertArrayEquals(command.parentNodeCode, parsedCommand.parentNodeCode)
        assertEquals(command.index, parsedCommand.index)
    }
}
