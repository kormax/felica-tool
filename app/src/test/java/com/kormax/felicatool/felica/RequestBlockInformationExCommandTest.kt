package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class RequestBlockInformationExCommandTest {

    @Test
    fun `test fromByteArray with valid data`() {
        // Sample data: length(1), command_code(1), idm(8), number_of_services(1), service_code(2)
        val data =
            byteArrayOf(
                0x0D.toByte(), // length
                0x1E.toByte(), // command code
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(), // IDM
                0x01.toByte(), // number of services
                0x10.toByte(),
                0x00.toByte(), // service code
            )

        val command = RequestBlockInformationExCommand.fromByteArray(data)

        assertArrayEquals(
            byteArrayOf(
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(),
            ),
            command.idm,
        )
        assertEquals(1, command.nodeCodes.size)
        assertArrayEquals(byteArrayOf(0x10.toByte(), 0x00.toByte()), command.nodeCodes[0])
    }

    @Test
    fun `test toByteArray round trip`() {
        val idm =
            byteArrayOf(
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(),
            )
        val nodeCodes = arrayOf(byteArrayOf(0x10.toByte(), 0x00.toByte()))
        val original = RequestBlockInformationExCommand(idm, nodeCodes)

        val data = original.toByteArray()
        val parsed = RequestBlockInformationExCommand.fromByteArray(data)

        assertArrayEquals(original.idm, parsed.idm)
        assertEquals(original.nodeCodes.size, parsed.nodeCodes.size)
        for (i in 0 until original.nodeCodes.size) {
            assertArrayEquals(original.nodeCodes[i], parsed.nodeCodes[i])
        }
    }

    @Test
    fun `test alternative constructor with nodes`() {
        val idm =
            byteArrayOf(
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(),
            )
        val service = Service(0, ServiceAttribute.PURSE_RW_WITH_KEY)
        val nodes: Array<Node> = arrayOf(service)
        val command = RequestBlockInformationExCommand(idm, nodes)

        assertArrayEquals(idm, command.idm)
        assertEquals(1, command.nodeCodes.size)
        assertArrayEquals(byteArrayOf(0x10.toByte(), 0x00.toByte()), command.nodeCodes[0])
    }

    @Test
    fun `test fromByteArray with invalid length`() {
        val data = byteArrayOf(0x0A.toByte(), 0x0E.toByte()) // length mismatch

        try {
            RequestBlockInformationExCommand.fromByteArray(data)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `test fromByteArray with invalid command code`() {
        val data =
            byteArrayOf(
                0x0B.toByte(), // length
                0x00.toByte(), // invalid command code
                0x01.toByte(),
                0x23.toByte(),
                0x45.toByte(),
                0x67.toByte(),
                0x89.toByte(),
                0xAB.toByte(),
                0xCD.toByte(),
                0xEF.toByte(), // IDM
            )

        try {
            RequestBlockInformationExCommand.fromByteArray(data)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
