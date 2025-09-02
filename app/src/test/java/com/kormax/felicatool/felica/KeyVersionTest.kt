package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for KeyVersion */
class KeyVersionTest {

    @Test
    fun testKeyVersion_basic() {
        // Test basic KeyVersion creation from bytes
        val keyVersion = KeyVersion("0102".hexToByteArray())

        assertEquals(513, keyVersion.toInt()) // 0x0201 in little-endian = 513
        assertFalse(keyVersion.isMissing)
    }

    @Test
    fun testKeyVersion_fromInt() {
        // Test KeyVersion creation from integer
        val keyVersion = KeyVersion.fromInt(513)

        assertArrayEquals("0102".hexToByteArray(), keyVersion.toByteArray())
        assertEquals(513, keyVersion.toInt())
        assertEquals("0102", keyVersion.toHexString())
    }

    @Test
    fun testKeyVersion_fromHex() {
        // Test KeyVersion creation from hex string
        val keyVersion = KeyVersion("0102".hexToByteArray())

        assertArrayEquals("0102".hexToByteArray(), keyVersion.toByteArray())
        assertEquals(513, keyVersion.toInt())
        assertEquals("0102", keyVersion.toHexString())
    }

    @Test
    fun testKeyVersion_fromHex_withPrefix() {
        // Test KeyVersion creation from hex string with 0x prefix
        val keyVersion = KeyVersion("0102".hexToByteArray())

        assertArrayEquals("0102".hexToByteArray(), keyVersion.toByteArray())
        assertEquals(513, keyVersion.toInt())
        assertEquals("0102", keyVersion.toHexString())
    }

    @Test
    fun testKeyVersion_fromHex_withSpaces() {
        // Test KeyVersion creation from hex string with spaces
        val keyVersion = KeyVersion("0102".hexToByteArray())

        assertArrayEquals("0102".hexToByteArray(), keyVersion.toByteArray())
        assertEquals(513, keyVersion.toInt())
        assertEquals("0102", keyVersion.toHexString())
    }

    @Test
    fun testKeyVersion_roundTrip_int() {
        // Test that fromInt and toInt are inverses
        val values = listOf(0, 1, 255, 256, 513, 65534, 65535)

        values.forEach { value ->
            val keyVersion = KeyVersion.fromInt(value)
            assertEquals("Round trip failed for value $value", value, keyVersion.toInt())
        }
    }

    @Test
    fun testKeyVersion_roundTrip_hex() {
        // Test that fromHex and toHexString are inverses
        val hexValues = listOf("0000", "0001", "00ff", "0100", "0102", "fffe", "ffff")

        hexValues.forEach { hex ->
            val keyVersion = KeyVersion(hex.hexToByteArray())
            assertEquals("Round trip failed for hex $hex", hex, keyVersion.toHexString())
        }
    }

    @Test
    fun testKeyVersion_nonExistent() {
        // Test MISSING constant
        assertTrue(KeyVersion.MISSING.isMissing)
        assertEquals(65535.toShort(), KeyVersion.MISSING.toInt().toShort())
    }

    @Test
    fun testKeyVersion_zero() {
        // Test INITIAL constant
        assertFalse(KeyVersion.INITIAL.isMissing)
        assertEquals(0, KeyVersion.INITIAL.toInt())
    }

    @Test
    fun testKeyVersion_littleEndian() {
        // Test little-endian byte order
        val keyVersion = KeyVersion(byteArrayOf(0x34.toByte(), 0x12.toByte()))

        assertEquals(0x1234, keyVersion.toInt())
        assertEquals("3412", keyVersion.toHexString())
    }

    @Test
    fun testKeyVersion_allByteCombinations() {
        // Test various byte combinations to ensure proper little-endian handling
        val testCases =
            listOf(
                byteArrayOf(0x00.toByte(), 0x00.toByte()) to 0x0000,
                byteArrayOf(0x01.toByte(), 0x00.toByte()) to 0x0001,
                byteArrayOf(0x00.toByte(), 0x01.toByte()) to 0x0100,
                byteArrayOf(0xFF.toByte(), 0x00.toByte()) to 0x00FF,
                byteArrayOf(0x00.toByte(), 0xFF.toByte()) to 0xFF00,
                byteArrayOf(0xFF.toByte(), 0xFF.toByte()) to 0xFFFF,
                byteArrayOf(0x34.toByte(), 0x12.toByte()) to 0x1234,
                byteArrayOf(0xAB.toByte(), 0xCD.toByte()) to 0xCDAB,
            )

        testCases.forEach { (bytes, expectedInt) ->
            val keyVersion = KeyVersion(bytes)
            assertEquals("Failed for bytes ${bytes.toHexString()}", expectedInt, keyVersion.toInt())
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testKeyVersion_invalidSize_empty() {
        // Test that empty byte array throws exception
        KeyVersion(byteArrayOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testKeyVersion_invalidSize_single() {
        // Test that single byte array throws exception
        KeyVersion(byteArrayOf(0x01))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testKeyVersion_invalidSize_triple() {
        // Test that three byte array throws exception
        KeyVersion(byteArrayOf(0x01, 0x02, 0x03))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testKeyVersion_fromInt_negative() {
        // Test that negative integer throws exception
        KeyVersion.fromInt(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testKeyVersion_fromInt_tooLarge() {
        // Test that integer larger than 65535 throws exception
        KeyVersion.fromInt(65536)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testKeyVersion_fromHex_tooShort() {
        // Test that hex string too short throws exception
        KeyVersion.fromByteArray("012".hexToByteArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testKeyVersion_fromHex_tooLong() {
        // Test that hex string too long throws exception
        KeyVersion.fromByteArray("01234".hexToByteArray())
    }
}
