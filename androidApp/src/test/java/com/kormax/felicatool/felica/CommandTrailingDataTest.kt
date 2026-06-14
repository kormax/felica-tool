package com.kormax.felicatool.felica

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CommandTrailingDataTest {
    @Test
    fun testFixedPayloadCommandRoundTripsTrailingData() {
        val idm = IDM.hexToByteArray()
        val trailingData = "aabb".hexToByteArray()

        val command = RequestSystemCodeCommand(idm, trailingData)
        val bytes = command.toByteArray()
        val parsedCommand = RequestSystemCodeCommand.fromByteArray(bytes)

        assertArrayEquals("0c0c${IDM}aabb".hexToByteArray(), bytes)
        assertArrayEquals(trailingData, command.trailingData)
        assertArrayEquals(trailingData, parsedCommand.trailingData)
    }

    @Test
    fun testCountDelimitedCommandRoundTripsTrailingData() {
        val idm = IDM.hexToByteArray()
        val nodeCodes = arrayOf("000a".hexToByteArray())
        val trailingData = "aabb".hexToByteArray()

        val command = RequestServiceCommand(idm, nodeCodes, trailingData)
        val bytes = command.toByteArray()
        val parsedCommand = RequestServiceCommand.fromByteArray(bytes)

        assertArrayEquals("0f02010203040506070801000aaabb".hexToByteArray(), bytes)
        assertArrayEquals(trailingData, command.trailingData)
        assertArrayEquals(trailingData, parsedCommand.trailingData)
    }

    @Test
    fun testTrailingDataIsDefensivelyCopied() {
        val idm = IDM.hexToByteArray()
        val trailingData = "aabb".hexToByteArray()
        val command = RequestSystemCodeCommand(idm, trailingData)

        trailingData[0] = 0x00
        val returnedTrailingData = command.trailingData
        returnedTrailingData[1] = 0x00

        assertArrayEquals("aabb".hexToByteArray(), command.trailingData)
    }

    @Test
    fun testEchoTreatsRemainingBytesAsPayload() {
        val command = EchoCommand.fromByteArray("05f000aabb".hexToByteArray())

        assertArrayEquals("aabb".hexToByteArray(), command.data)
        assertArrayEquals(ByteArray(0), command.trailingData)
        assertEquals("05f000aabb", command.toByteArray().toHexString())
    }

    private companion object {
        const val IDM = "0102030405060708"
    }
}
