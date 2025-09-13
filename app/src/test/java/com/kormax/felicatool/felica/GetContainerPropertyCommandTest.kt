package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class GetContainerPropertyCommandTest {

    @Test
    fun testCommandCreation_withPropertyType() {
        val command = GetContainerPropertyCommand(GetContainerPropertyCommand.Property.PROPERTY_1)

        assertEquals(GetContainerPropertyCommand.Property.PROPERTY_1, command.property)
        assertEquals(0x00.toShort(), command.index)
    }

    @Test
    fun testCommandCreation_withRawIndex() {
        val command = GetContainerPropertyCommand(0x01)

        assertEquals(GetContainerPropertyCommand.Property.PROPERTY_2, command.property)
        assertEquals(0x01.toShort(), command.index)
    }

    @Test
    fun testCommandCreation_withUnknownIndex() {
        val command = GetContainerPropertyCommand(0x05)

        assertEquals(GetContainerPropertyCommand.Property.UNKNOWN::class, command.property::class)
        assertEquals(0x05.toShort(), command.index)
    }

    @Test
    fun testToByteArray() {
        val command = GetContainerPropertyCommand(GetContainerPropertyCommand.Property.PROPERTY_1)
        val expectedData =
            byteArrayOf(
                0x04, // length
                0x2E, // command code
                0x00, // index LSB
                0x00, // index MSB (padding for little-endian)
            )

        assertArrayEquals(expectedData, command.toByteArray())
    }

    @Test
    fun testToByteArray_property2() {
        val command = GetContainerPropertyCommand(GetContainerPropertyCommand.Property.PROPERTY_2)
        val expectedData =
            byteArrayOf(
                0x04, // length
                0x2E, // command code
                0x01, // index LSB
                0x00, // index MSB
            )

        assertArrayEquals(expectedData, command.toByteArray())
    }

    @Test
    fun testToByteArray_unknownIndex() {
        val command = GetContainerPropertyCommand(0x05)
        val expectedData =
            byteArrayOf(
                0x04, // length
                0x2E, // command code
                0x05, // index LSB
                0x00, // index MSB
            )

        assertArrayEquals(expectedData, command.toByteArray())
    }

    @Test
    fun testFromByteArray_validData() {
        val data =
            byteArrayOf(
                0x04, // length
                0x2E, // command code
                0x01, // index LSB
                0x00, // index MSB
            )

        val command = GetContainerPropertyCommand.fromByteArray(data)
        assertEquals(GetContainerPropertyCommand.Property.PROPERTY_2, command.property)
        assertEquals(0x01.toShort(), command.index)
    }

    @Test
    fun testFromByteArray_unknownProperty() {
        val data =
            byteArrayOf(
                0x04, // length
                0x2E, // command code
                0xFF.toByte(), // index LSB (255)
                0x00, // index MSB
            )

        val command = GetContainerPropertyCommand.fromByteArray(data)
        assertEquals(GetContainerPropertyCommand.Property.UNKNOWN::class, command.property::class)
        assertEquals(0xFF.toShort(), command.index)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val data = byteArrayOf(0x03, 0x2E, 0x00) // missing MSB
        GetContainerPropertyCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data = byteArrayOf(0x05, 0x2E, 0x00, 0x00) // length says 5, actual is 4
        GetContainerPropertyCommand.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongCommandCode() {
        val data = byteArrayOf(0x04, 0x2F, 0x00, 0x00) // wrong command code
        GetContainerPropertyCommand.fromByteArray(data)
    }

    @Test
    fun testLittleEndianEncoding() {
        // Test that index is encoded in little-endian format
        val command = GetContainerPropertyCommand(0x1234)
        val bytes = command.toByteArray()

        // In little-endian: LSB first (0x34), then MSB (0x12)
        assertEquals(0x34.toByte(), bytes[2]) // LSB
        assertEquals(0x12.toByte(), bytes[3]) // MSB
    }

    @Test
    fun testRoundTrip() {
        val originalCommand =
            GetContainerPropertyCommand(GetContainerPropertyCommand.Property.PROPERTY_1)
        val bytes = originalCommand.toByteArray()
        val parsedCommand = GetContainerPropertyCommand.fromByteArray(bytes)

        assertEquals(originalCommand.property, parsedCommand.property)
        assertEquals(originalCommand.index, parsedCommand.index)
    }
}
