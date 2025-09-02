package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class MacCommunicationPropertyTest {

    @Test
    fun testMacCommunicationPropertyCreationEnabled() {
        val property = MacCommunicationProperty(enabled = true)

        assertTrue(property.enabled)
    }

    @Test
    fun testMacCommunicationPropertyCreationDisabled() {
        val property = MacCommunicationProperty(enabled = false)

        assertFalse(property.enabled)
    }

    @Test
    fun testMacCommunicationPropertyToByteArrayEnabled() {
        val property = MacCommunicationProperty(enabled = true)
        val data = property.toByteArray()

        assertEquals(1, data.size)
        assertEquals(0x01.toByte(), data[0])
    }

    @Test
    fun testMacCommunicationPropertyToByteArrayDisabled() {
        val property = MacCommunicationProperty(enabled = false)
        val data = property.toByteArray()

        assertEquals(1, data.size)
        assertEquals(0x00.toByte(), data[0])
    }

    @Test
    fun testMacCommunicationPropertyFromByteArrayEnabled() {
        val data = "01".hexToByteArray()
        val property = MacCommunicationProperty.fromByteArray(data)

        assertTrue(property.enabled)
    }

    @Test
    fun testMacCommunicationPropertyFromByteArrayDisabled() {
        val data = "00".hexToByteArray()
        val property = MacCommunicationProperty.fromByteArray(data)

        assertFalse(property.enabled)
    }

    @Test
    fun testMacCommunicationPropertyFromByteArrayInvalidSize() {
        val data = "0001".hexToByteArray() // 2 bytes instead of 1

        assertThrows(IllegalArgumentException::class.java) {
            MacCommunicationProperty.fromByteArray(data)
        }
    }

    @Test
    fun testMacCommunicationPropertySizeBytes() {
        assertEquals(1, MacCommunicationProperty.SIZE_BYTES)
    }

    @Test
    fun testMacCommunicationPropertyRoundTripEnabled() {
        val original = MacCommunicationProperty(enabled = true)
        val data = original.toByteArray()
        val parsed = MacCommunicationProperty.fromByteArray(data)

        assertEquals(original, parsed)
    }

    @Test
    fun testMacCommunicationPropertyRoundTripDisabled() {
        val original = MacCommunicationProperty(enabled = false)
        val data = original.toByteArray()
        val parsed = MacCommunicationProperty.fromByteArray(data)

        assertEquals(original, parsed)
    }
}
