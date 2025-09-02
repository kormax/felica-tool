package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class ValueLimitedPurseServicePropertyTest {

    @Test
    fun testValueLimitedPurseServicePropertyCreation() {
        val property =
            ValueLimitedPurseServiceProperty(
                enabled = true,
                upperLimit = 50000,
                lowerLimit = -10000,
                generationNumber = 15,
            )

        assertTrue(property.enabled)
        assertEquals(50000, property.upperLimit)
        assertEquals(-10000, property.lowerLimit)
        assertEquals(15, property.generationNumber)
    }

    @Test
    fun testValueLimitedPurseServicePropertyToByteArray() {
        val property =
            ValueLimitedPurseServiceProperty(
                enabled = true,
                upperLimit = 10000, // 0x00002710 in little endian: 10 27 00 00
                lowerLimit = -2110, // 0xFFFFF7C2 in little endian: C2 F7 FF FF
                generationNumber = 42, // 0x2A
            )

        val data = property.toByteArray()

        assertEquals(10, data.size)
        assertEquals(0x01.toByte(), data[0]) // enabled flag
        assertEquals(0x10.toByte(), data[1]) // upper limit LSB
        assertEquals(0x27.toByte(), data[2])
        assertEquals(0x00.toByte(), data[3])
        assertEquals(0x00.toByte(), data[4]) // upper limit MSB
        assertEquals(0xC2.toByte(), data[5]) // lower limit LSB
        assertEquals(0xF7.toByte(), data[6])
        assertEquals(0xFF.toByte(), data[7])
        assertEquals(0xFF.toByte(), data[8]) // lower limit MSB
        assertEquals(0x2A.toByte(), data[9]) // generation number
    }

    @Test
    fun testValueLimitedPurseServicePropertyFromByteArray() {
        val data =
            "01" + "10270000" + "C2F7FFFF" + "2A" // enabled + upper(10000) + lower(-2110) + gen(42)
        val property = ValueLimitedPurseServiceProperty.fromByteArray(data.hexToByteArray())

        assertTrue(property.enabled)
        assertEquals(10000, property.upperLimit)
        assertEquals(-2110, property.lowerLimit)
        assertEquals(42, property.generationNumber)
    }

    @Test
    fun testValueLimitedPurseServicePropertyDisabled() {
        val property =
            ValueLimitedPurseServiceProperty(
                enabled = false,
                upperLimit = -1, // 0xFFFFFFFF when disabled
                lowerLimit = -1, // 0xFFFFFFFF when disabled
                generationNumber = 255, // 0xFF when disabled
            )

        val data = property.toByteArray()

        assertEquals(10, data.size)
        assertEquals(0x00.toByte(), data[0]) // disabled flag
        assertEquals(0xFF.toByte(), data[1]) // all upper limit bytes = 0xFF
        assertEquals(0xFF.toByte(), data[2])
        assertEquals(0xFF.toByte(), data[3])
        assertEquals(0xFF.toByte(), data[4])
        assertEquals(0xFF.toByte(), data[5]) // all lower limit bytes = 0xFF
        assertEquals(0xFF.toByte(), data[6])
        assertEquals(0xFF.toByte(), data[7])
        assertEquals(0xFF.toByte(), data[8])
        assertEquals(0xFF.toByte(), data[9]) // generation = 0xFF
    }

    @Test
    fun testValueLimitedPurseServicePropertyFromByteArrayInvalidSize() {
        val data = "0110270000".hexToByteArray() // Only 5 bytes instead of 10

        assertThrows(IllegalArgumentException::class.java) {
            ValueLimitedPurseServiceProperty.fromByteArray(data)
        }
    }

    @Test
    fun testValueLimitedPurseServicePropertySizeBytes() {
        assertEquals(10, ValueLimitedPurseServiceProperty.SIZE_BYTES)
    }

    @Test
    fun testValueLimitedPurseServicePropertyRoundTrip() {
        val original =
            ValueLimitedPurseServiceProperty(
                enabled = true,
                upperLimit = 123456,
                lowerLimit = -54321,
                generationNumber = 77,
            )

        val data = original.toByteArray()
        val parsed = ValueLimitedPurseServiceProperty.fromByteArray(data)

        assertEquals(original, parsed)
    }
}
