package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class RequestServiceV2CommandTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testRequestServiceV2CommandCreation() {
        val idm = IDM
        val nodeCodes =
            arrayOf(
                "0008".hexToByteArray(), // Service code 0x0008
                "FFFF".hexToByteArray(), // System code
            )

        val command = RequestServiceV2Command(idm, nodeCodes)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(2, command.nodeCodes.size)
        assertEquals("0008".hexToByteArray().toList(), command.nodeCodes[0].toList())
        assertEquals("FFFF".hexToByteArray().toList(), command.nodeCodes[1].toList())
    }

    @Test
    fun testRequestServiceV2CommandCreationWithNodes() {
        val idm = IDM
        val service = Service(100, ServiceAttribute.RandomRwWithKey)
        val area = Area(50, AreaAttribute.CanCreateSubArea, 200, AreaAttribute.EndSubArea)
        val nodes = listOf<Node>(service, area)

        val command = RequestServiceV2Command(idm, nodes)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(2, command.nodeCodes.size)
        assertEquals(service.code.toList(), command.nodeCodes[0].toList())
        assertEquals(area.code.toList(), command.nodeCodes[1].toList())
    }

    @Test
    fun testRequestServiceV2CommandToByteArray() {
        val idm = IDM
        val nodeCodes = arrayOf("0008".hexToByteArray())

        val command = RequestServiceV2Command(idm, nodeCodes)
        val data = command.toByteArray()

        // Expected: length(1) + command_code(1) + idm(8) + number_of_nodes(1) + node_code(2) = 13
        // bytes
        assertEquals(13, data.size)
        assertEquals(13.toByte(), data[0]) // Length
        assertEquals(0x32.toByte(), data[1]) // Command code (Request Service v2)
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(1.toByte(), data[10]) // Number of nodes
        assertEquals(
            "0008".hexToByteArray().toList(),
            data.sliceArray(11..12).toList(),
        ) // Node code
    }

    @Test
    fun testRequestServiceV2CommandFromByteArray() {
        val originalCommand =
            RequestServiceV2Command(IDM, arrayOf("0008".hexToByteArray(), "FFFF".hexToByteArray()))
        val data = originalCommand.toByteArray()
        val parsedCommand = RequestServiceV2Command.fromByteArray(data)

        assertEquals(originalCommand.idm.toList(), parsedCommand.idm.toList())
        assertEquals(originalCommand.nodeCodes.size, parsedCommand.nodeCodes.size)
        originalCommand.nodeCodes.forEachIndexed { index, nodeCode ->
            assertEquals(nodeCode.toList(), parsedCommand.nodeCodes[index].toList())
        }
    }

    @Test
    fun testRequestServiceV2CommandEmptyNodeCodes() {
        val idm = IDM
        val nodeCodes = emptyArray<ByteArray>()

        assertThrows(IllegalArgumentException::class.java) {
            RequestServiceV2Command(idm, nodeCodes)
        }
    }

    @Test
    fun testRequestServiceV2CommandTooManyNodes() {
        val idm = IDM
        val nodeCodes = Array(33) { "0008".hexToByteArray() } // 33 nodes > max 32

        assertThrows(IllegalArgumentException::class.java) {
            RequestServiceV2Command(idm, nodeCodes)
        }
    }

    @Test
    fun testRequestServiceV2CommandInvalidNodeCodeSize() {
        val idm = IDM
        val nodeCodes = arrayOf("00".hexToByteArray()) // Only 1 byte instead of 2

        assertThrows(IllegalArgumentException::class.java) {
            RequestServiceV2Command(idm, nodeCodes)
        }
    }
}
