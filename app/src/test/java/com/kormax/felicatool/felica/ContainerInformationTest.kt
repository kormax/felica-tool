package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for ContainerInformation */
class ContainerInformationTest {

    companion object {
        private val SAMPLE_FORMAT_VERSION = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        private val SAMPLE_MODEL_INFO =
            byteArrayOf(0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B)
    }

    @Test
    fun testContainerInformation_creation() {
        val containerInfo = ContainerInformation(SAMPLE_FORMAT_VERSION, SAMPLE_MODEL_INFO)

        assertArrayEquals(SAMPLE_FORMAT_VERSION, containerInfo.formatVersionCarrierInformation)
        assertArrayEquals(SAMPLE_MODEL_INFO, containerInfo.mobilePhoneModelInformation)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testContainerInformation_invalidFormatVersionSize() {
        val invalidFormatVersion = byteArrayOf(0x01, 0x02, 0x03, 0x04) // Wrong size
        ContainerInformation(invalidFormatVersion, SAMPLE_MODEL_INFO)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testContainerInformation_invalidModelInfoSize() {
        val invalidModelInfo =
            byteArrayOf(0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A) // Wrong size
        ContainerInformation(SAMPLE_FORMAT_VERSION, invalidModelInfo)
    }

    @Test
    fun testContainerInformation_equals() {
        val containerInfo1 = ContainerInformation(SAMPLE_FORMAT_VERSION, SAMPLE_MODEL_INFO)
        val containerInfo2 = ContainerInformation(SAMPLE_FORMAT_VERSION, SAMPLE_MODEL_INFO)
        val containerInfo3 =
            ContainerInformation(byteArrayOf(0x05, 0x04, 0x03, 0x02, 0x01), SAMPLE_MODEL_INFO)

        assertEquals(containerInfo1, containerInfo2)
        assertNotEquals(containerInfo1, containerInfo3)
    }
}
