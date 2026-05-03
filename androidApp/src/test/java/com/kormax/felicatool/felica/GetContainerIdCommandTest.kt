package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class GetContainerIdCommandTest {

    @Test
    fun `should create command with correct command code`() {
        val command = GetContainerIdCommand()

        assertEquals(0x70.toShort(), GetContainerIdCommand.COMMAND_CODE)
    }

    @Test
    fun `should serialize to correct byte array`() {
        val command = GetContainerIdCommand()
        val expected =
            "04700000".hexToByteArray() // length(4) + command_code(0x70) + reserved(0000)

        assertArrayEquals(expected, command.toByteArray())
    }

    @Test
    fun `should create instance from companion factory`() {
        val command = GetContainerIdCommand()

        assertNotNull(command)
        assertEquals(0x70.toShort(), GetContainerIdCommand.COMMAND_CODE)
    }
}
