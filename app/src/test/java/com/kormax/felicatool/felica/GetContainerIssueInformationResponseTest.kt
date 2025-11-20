package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for GetContainerIssueInformationResponse */
class GetContainerIssueInformationResponseTest {

    companion object {
        private val TEST_IDM = "01020304050607ff".hexToByteArray()
        private val ANOTHER_IDM = "1122334455667788".hexToByteArray()
        private val SAMPLE_FORMAT_VERSION = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)
        private val SAMPLE_MODEL_INFO =
            byteArrayOf(0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4A, 0x4B)
    }

    @Test
    fun testGetContainerIssueInformationResponse_creation() {
        val containerInfo = ContainerInformation(SAMPLE_FORMAT_VERSION, SAMPLE_MODEL_INFO)
        val response = GetContainerIssueInformationResponse(TEST_IDM, containerInfo)

        assertArrayEquals(TEST_IDM, response.idm)
        assertEquals(containerInfo, response.containerInformation)
        assertEquals("01020304050607FF", response.idm.toHexString().uppercase())
    }

    @Test
    fun testGetContainerIssueInformationResponse_toByteArray() {
        val containerInfo = ContainerInformation(SAMPLE_FORMAT_VERSION, SAMPLE_MODEL_INFO)
        val response = GetContainerIssueInformationResponse(TEST_IDM, containerInfo)
        val bytes = response.toByteArray()

        // Check length
        assertEquals(GetContainerIssueInformationResponse.RESPONSE_LENGTH, bytes.size)
        assertEquals(GetContainerIssueInformationResponse.RESPONSE_LENGTH.toByte(), bytes[0])

        // Check response code
        assertEquals(GetContainerIssueInformationResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM
        for (i in TEST_IDM.indices) {
            assertEquals("IDM byte $i", TEST_IDM[i], bytes[2 + i])
        }

        // Check container information
        for (i in SAMPLE_FORMAT_VERSION.indices) {
            assertEquals("Format version byte $i", SAMPLE_FORMAT_VERSION[i], bytes[10 + i])
        }
        for (i in SAMPLE_MODEL_INFO.indices) {
            assertEquals("Model info byte $i", SAMPLE_MODEL_INFO[i], bytes[15 + i])
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun testGetContainerIssueInformationResponse_invalidIdmSize() {
        val containerInfo = ContainerInformation(SAMPLE_FORMAT_VERSION, SAMPLE_MODEL_INFO)
        val invalidIdm = byteArrayOf(0x01.toByte()) // Too short
        GetContainerIssueInformationResponse(invalidIdm, containerInfo)
    }

    @Test
    fun testFromByteArray_basic() {
        val containerInfo = ContainerInformation(SAMPLE_FORMAT_VERSION, SAMPLE_MODEL_INFO)
        val originalResponse = GetContainerIssueInformationResponse(TEST_IDM, containerInfo)
        val data = originalResponse.toByteArray()

        val parsedResponse = GetContainerIssueInformationResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, parsedResponse.idm)
        assertEquals(containerInfo, parsedResponse.containerInformation)
    }

    @Test
    fun testFromByteArray_specificBytes() {
        // 1a2301020304050607ff01020304054142434445464748494a4b (length=26, response_code=0x23,
        // idm=01020304050607ff, format_version=0102030405, model_info=4142434445464748494a4b)
        val data = "1a2301020304050607ff01020304054142434445464748494a4b".hexToByteArray()

        val response = GetContainerIssueInformationResponse.fromByteArray(data)

        assertArrayEquals(TEST_IDM, response.idm)
        assertArrayEquals(
            SAMPLE_FORMAT_VERSION,
            response.containerInformation.formatVersionCarrierInformation,
        )
        assertArrayEquals(
            SAMPLE_MODEL_INFO,
            response.containerInformation.mobilePhoneModelInformation,
        )
    }

    @Test
    fun testFromByteArray_anotherIdm() {
        // 1a23112233445566778801020304054142434445464748494a4b (length=26, response_code=0x23,
        // idm=1122334455667788, format_version=0102030405, model_info=4142434445464748494a4b)
        val data = "1a23112233445566778801020304054142434445464748494a4b".hexToByteArray()

        val response = GetContainerIssueInformationResponse.fromByteArray(data)

        assertArrayEquals(ANOTHER_IDM, response.idm)
        assertArrayEquals(
            SAMPLE_FORMAT_VERSION,
            response.containerInformation.formatVersionCarrierInformation,
        )
        assertArrayEquals(
            SAMPLE_MODEL_INFO,
            response.containerInformation.mobilePhoneModelInformation,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val data = "1a23".hexToByteArray() // Too short
        GetContainerIssueInformationResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val data =
            "1b2301020304050607ff01020304054142434445464748494a4b"
                .hexToByteArray() // Length says 27, but actual is 26
        GetContainerIssueInformationResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongResponseCode() {
        val data =
            "1a2201020304050607ff01020304054142434445464748494a4b"
                .hexToByteArray() // Wrong response code (0x22 instead of 0x23)
        GetContainerIssueInformationResponse.fromByteArray(data)
    }

    @Test
    fun testConstants() {
        assertEquals(0x23.toByte(), GetContainerIssueInformationResponse.RESPONSE_CODE.toByte())
        assertEquals(
            26,
            GetContainerIssueInformationResponse.RESPONSE_LENGTH,
        ) // 1 (length) + 1 (response code) + 8 (IDM) + 16 (container info)
    }

    @Test
    fun testRoundTrip() {
        val containerInfo = ContainerInformation(SAMPLE_FORMAT_VERSION, SAMPLE_MODEL_INFO)
        val originalResponse = GetContainerIssueInformationResponse(ANOTHER_IDM, containerInfo)

        val bytes = originalResponse.toByteArray()
        val parsedResponse = GetContainerIssueInformationResponse.fromByteArray(bytes)

        assertArrayEquals(originalResponse.idm, parsedResponse.idm)
        assertEquals(originalResponse.containerInformation, parsedResponse.containerInformation)
        assertArrayEquals(bytes, parsedResponse.toByteArray())
    }

    @Test
    fun testMultipleIdms() {
        val testIdms =
            listOf(
                "01020304050607ff".hexToByteArray(),
                "1122334455667788".hexToByteArray(),
                "0000000000000000".hexToByteArray(),
                "ffffffffffffffff".hexToByteArray(),
            )

        val containerInfo = ContainerInformation(SAMPLE_FORMAT_VERSION, SAMPLE_MODEL_INFO)

        for (idm in testIdms) {
            val response = GetContainerIssueInformationResponse(idm, containerInfo)
            val bytes = response.toByteArray()
            val parsed = GetContainerIssueInformationResponse.fromByteArray(bytes)

            assertArrayEquals("Failed for IDM: ${idm.toHexString()}", idm, parsed.idm)
            assertEquals(
                "Failed for container info with IDM: ${idm.toHexString()}",
                containerInfo,
                parsed.containerInformation,
            )
        }
    }
}
