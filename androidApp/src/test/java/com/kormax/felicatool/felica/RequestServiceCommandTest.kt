package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class RequestServiceCommandTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
    }

    @Test
    fun testRequestServiceCommandCreation() {
        val idm = IDM
        val nodeCodes =
            arrayOf(
                "0008".hexToByteArray(), // Service code 0x0008
                "FFFF".hexToByteArray(), // System code
            )

        val command = RequestServiceCommand(idm, nodeCodes)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(2, command.nodeCodes.size)
        assertEquals("0008".hexToByteArray().toList(), command.nodeCodes[0].toList())
        assertEquals("FFFF".hexToByteArray().toList(), command.nodeCodes[1].toList())
    }

    @Test
    fun testRequestServiceCommandCreationWithNodes() {
        val idm = IDM
        val service = Service(100, ServiceAttribute.RandomRwWithKey)
        val area = Area(50, AreaAttribute.CanCreateSubArea, 200, AreaAttribute.EndSubArea)
        val nodes = listOf<Node>(service, area)

        val command = RequestServiceCommand(idm, nodes)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(2, command.nodeCodes.size)
        assertEquals(service.code.toList(), command.nodeCodes[0].toList())
        assertEquals(area.code.toList(), command.nodeCodes[1].toList())
    }

    @Test
    fun testRequestServiceCommandToByteArray() {
        val idm = IDM
        val nodeCodes = arrayOf("0008".hexToByteArray())

        val command = RequestServiceCommand(idm, nodeCodes)
        val data = command.toByteArray()

        // Expected: length(1) + command_code(1) + idm(8) + number_of_nodes(1) + node_code(2) = 13
        // bytes
        assertEquals(13, data.size)
        assertEquals(13.toByte(), data[0]) // Length
        assertEquals(0x02.toByte(), data[1]) // Command code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(1.toByte(), data[10]) // Number of nodes
        assertEquals(
            "0008".hexToByteArray().toList(),
            data.sliceArray(11..12).toList(),
        ) // Node code
    }

    @Test
    fun testRequestServiceCommandFromByteArray() {
        val data = "0D02010203040506070801000A".hexToByteArray()

        val command = RequestServiceCommand.fromByteArray(data)

        assertEquals(IDM.toList(), command.idm.toList())
        assertEquals(1, command.nodeCodes.size)
        assertEquals("000A".hexToByteArray().toList(), command.nodeCodes[0].toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestServiceCommandEmptyNodeCodes() {
        val idm = IDM
        RequestServiceCommand(idm, emptyList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestServiceCommandTooManyNodeCodes() {
        val idm = IDM
        val nodeCodes = (1..33).map { "0008".hexToByteArray() }.toTypedArray()
        RequestServiceCommand(idm, nodeCodes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testRequestServiceCommandInvalidNodeCodeSize() {
        val idm = IDM
        val nodeCodes = arrayOf("00".hexToByteArray()) // Too short
        RequestServiceCommand(idm, nodeCodes)
    }
}
