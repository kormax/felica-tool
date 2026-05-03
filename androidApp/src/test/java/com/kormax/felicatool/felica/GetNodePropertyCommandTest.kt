package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class GetNodePropertyCommandTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testGetNodePropertyCommandCreation() {
        val idm = IDM
        val nodeCodes =
            arrayOf(
                "0008".hexToByteArray(), // Service code 0x0008
                "FFFF".hexToByteArray(), // System code
            )
        val nodePropertyType = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE

        val command = GetNodePropertyCommand(idm, nodePropertyType, nodeCodes)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(nodePropertyType, command.nodePropertyType)
        assertEquals(2, command.nodeCodes.size)
        assertEquals("0008".hexToByteArray().toList(), command.nodeCodes[0].toList())
        assertEquals("FFFF".hexToByteArray().toList(), command.nodeCodes[1].toList())
    }

    @Test
    fun testGetNodePropertyCommandCreationWithNodes() {
        val idm = IDM
        val service = Service(100, ServiceAttribute.RandomRwWithKey)
        val area = Area(50, AreaAttribute.CanCreateSubArea, 200, AreaAttribute.EndSubArea)
        val nodes = listOf<Node>(service, area)
        val nodePropertyType = NodePropertyType.MAC_COMMUNICATION

        val command = GetNodePropertyCommand(idm, nodePropertyType, nodes)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(nodePropertyType, command.nodePropertyType)
        assertEquals(2, command.nodeCodes.size)
        assertEquals(service.code.toList(), command.nodeCodes[0].toList())
        assertEquals(area.code.toList(), command.nodeCodes[1].toList())
    }

    @Test
    fun testGetNodePropertyCommandCreationWithNodeArray() {
        val idm = IDM
        val service = Service(100, ServiceAttribute.RandomRwWithKey)
        val area = Area(50, AreaAttribute.CanCreateSubArea, 200, AreaAttribute.EndSubArea)
        val nodes = arrayOf<Node>(service, area)
        val nodePropertyType = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE

        val command = GetNodePropertyCommand(idm, nodePropertyType, nodes.toList())

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(nodePropertyType, command.nodePropertyType)
        assertEquals(2, command.nodeCodes.size)
        assertEquals(service.code.toList(), command.nodeCodes[0].toList())
        assertEquals(area.code.toList(), command.nodeCodes[1].toList())
    }

    @Test
    fun testGetNodePropertyCommandToByteArray() {
        val idm = IDM
        val nodeCodes = arrayOf("0008".hexToByteArray())
        val nodePropertyType = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE

        val command = GetNodePropertyCommand(idm, nodePropertyType, nodeCodes)
        val data = command.toByteArray()

        // Expected: length(1) + command_code(1) + idm(8) + type(1) + number_of_nodes(1) +
        // node_code(2)
        // = 14 bytes
        assertEquals(14, data.size)
        assertEquals(14.toByte(), data[0]) // Length
        assertEquals(0x28.toByte(), data[1]) // Command code (Get Node Property)
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(nodePropertyType.value.toByte(), data[10]) // Node property type
        assertEquals(1.toByte(), data[11]) // Number of nodes
        assertEquals(
            "0008".hexToByteArray().toList(),
            data.sliceArray(12..13).toList(),
        ) // Node code
    }

    @Test
    fun testGetNodePropertyCommandFromByteArray() {
        val originalCommand =
            GetNodePropertyCommand(
                IDM,
                NodePropertyType.MAC_COMMUNICATION,
                arrayOf("0008".hexToByteArray(), "FFFF".hexToByteArray()),
            )
        val data = originalCommand.toByteArray()
        val parsedCommand = GetNodePropertyCommand.fromByteArray(data)

        assertEquals(originalCommand.idm.toList(), parsedCommand.idm.toList())
        assertEquals(originalCommand.nodePropertyType, parsedCommand.nodePropertyType)
        assertEquals(originalCommand.nodeCodes.size, parsedCommand.nodeCodes.size)
        originalCommand.nodeCodes.forEachIndexed { index, nodeCode ->
            assertEquals(nodeCode.toList(), parsedCommand.nodeCodes[index].toList())
        }
    }

    @Test
    fun testGetNodePropertyCommandEmptyNodeCodes() {
        val idm = IDM
        val nodeCodes = emptyArray<ByteArray>()
        val nodePropertyType = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE

        assertThrows(IllegalArgumentException::class.java) {
            GetNodePropertyCommand(idm, nodePropertyType, nodeCodes)
        }
    }

    @Test
    fun testGetNodePropertyCommandTooManyNodes() {
        val idm = IDM
        val nodeCodes = Array(17) { "0008".hexToByteArray() } // 17 nodes > max 16
        val nodePropertyType = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE

        assertThrows(IllegalArgumentException::class.java) {
            GetNodePropertyCommand(idm, nodePropertyType, nodeCodes)
        }
    }

    @Test
    fun testGetNodePropertyCommandInvalidNodeCodeSize() {
        val idm = IDM
        val nodeCodes = arrayOf("00".hexToByteArray()) // Only 1 byte instead of 2
        val nodePropertyType = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE

        assertThrows(IllegalArgumentException::class.java) {
            GetNodePropertyCommand(idm, nodePropertyType, nodeCodes)
        }
    }

    @Test
    fun testGetNodePropertyCommandFromByteArrayInvalidCommandCode() {
        val data =
            "0E2901020304050607080001000008"
                .hexToByteArray() // Wrong command code 0x29 instead of 0x28

        assertThrows(IllegalArgumentException::class.java) {
            GetNodePropertyCommand.fromByteArray(data)
        }
    }

    @Test
    fun testGetNodePropertyCommandFromByteArrayInvalidNodePropertyType() {
        val data = "0E280102030405060708FF01000008".hexToByteArray() // Invalid property type 0xFF

        assertThrows(IllegalArgumentException::class.java) {
            GetNodePropertyCommand.fromByteArray(data)
        }
    }
}
