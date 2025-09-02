package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for SearchServiceCodeCommand */
class SearchServiceCodeCommandTest {

    private val IDM = "013933333333e6f5"

    @Test
    fun testSearchServiceCodeCommand_fromByteArray() {
        val testCases =
            arrayOf(
                "0c0a${IDM}0000" to 0,
                "0c0a${IDM}0100" to 1,
                "0c0a${IDM}0200" to 2,
                "0c0a${IDM}0300" to 3,
            )

        val expectedIdm = IDM.hexToByteArray()

        testCases.forEach { (commandHex, expectedIndex) ->
            val commandBytes = commandHex.hexToByteArray()
            val command = SearchServiceCodeCommand.fromByteArray(commandBytes)

            assertArrayEquals("Failed for index $expectedIndex", expectedIdm, command.idm)
            assertEquals("Failed for index $expectedIndex", expectedIndex, command.index)
        }
    }

    @Test
    fun testSearchServiceCodeCommand_fromByteArray_tooShort() {
        val shortData = "0c0a${IDM}00".hexToByteArray() // 11 bytes instead of 12
        try {
            SearchServiceCodeCommand.fromByteArray(shortData)
            fail("Expected IllegalArgumentException for too short data")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("must be at least") == true)
        }
    }

    @Test
    fun testSearchServiceCodeCommand_fromByteArray_wrongCommandCode() {
        val wrongCommandData =
            "0c0b${IDM}0000".hexToByteArray() // Command code 0x0B instead of 0x0A
        try {
            SearchServiceCodeCommand.fromByteArray(wrongCommandData)
            fail("Expected IllegalArgumentException for wrong command code")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid command code") == true)
        }
    }

    @Test
    fun testSearchServiceCodeCommand_toByteArray() {
        val idm = IDM.hexToByteArray()
        val command = SearchServiceCodeCommand(idm, 0)
        val bytes = command.toByteArray()

        val expected = "0c0a${IDM}0000".hexToByteArray()
        assertArrayEquals(expected, bytes)
    }

    @Test
    fun testSearchServiceCodeCommand_roundTrip() {
        val originalHex = "0c0a${IDM}0000"
        val originalBytes = originalHex.hexToByteArray()
        val command = SearchServiceCodeCommand.fromByteArray(originalBytes)
        val roundTripBytes = command.toByteArray()

        assertArrayEquals(originalBytes, roundTripBytes)
    }
}
