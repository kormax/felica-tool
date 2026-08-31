package com.kormax.felicatool.felica

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemStatusTest {
    @Test
    fun parsesVersion0() {
        val status = SystemStatus.fromByteArray("00f401".hexToByteArray()) as SystemStatus.V0

        assertFalse(status.desSystemInitializationCommandsDisabled)
        assertFalse(status.desNodeIssuanceCommandsDisabled)
        assertTrue(status.rfuD0Bit2)
        assertTrue(status.desAuthenticationStrictAreaListValidation)
        assertTrue(status.rfuD0Bit4)
        assertTrue(status.rfuD0Bit5)
        assertTrue(status.rfuD0Bit6)
        assertTrue(status.rfuD0Bit7)
        assertTrue(status.unknownFlag)
        assertEquals("00f401", status.toByteArray().toHexString())
    }

    @Test
    fun version0EqualityUsesExtraContents() {
        val first = SystemStatus.fromByteArray("00f4000102".hexToByteArray())
        val second = SystemStatus.fromByteArray("00f4000102".hexToByteArray())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun unsupportedVersionEqualityUsesUnknownDataContents() {
        val first = SystemStatus.fromByteArray("010102".hexToByteArray())
        val second = SystemStatus.fromByteArray("010102".hexToByteArray())

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }
}
