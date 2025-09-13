package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class GetContainerPropertyResponseTest {

    @Test
    fun testResponseCreation_validData() {
        val data = byteArrayOf(0x01, 0x02, 0x03)
        val response = GetContainerPropertyResponse(data)

        assertArrayEquals(data, response.data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testResponseCreation_emptyData() {
        GetContainerPropertyResponse(byteArrayOf())
    }

    @Test
    fun testToByteArray_singleByte() {
        val data = byteArrayOf(0x42)
        val response = GetContainerPropertyResponse(data)
        val expectedBytes =
            byteArrayOf(
                0x03, // length (base 2 + data 1)
                0x2F, // response code
                0x42, // data
            )

        assertArrayEquals(expectedBytes, response.toByteArray())
    }

    @Test
    fun testToByteArray_multipleBytes() {
        val data = byteArrayOf(0x01, 0x20, 0x03, 0x04)
        val response = GetContainerPropertyResponse(data)
        val expectedBytes =
            byteArrayOf(
                0x06, // length (base 2 + data 4)
                0x2F, // response code
                0x01,
                0x20,
                0x03,
                0x04, // data
            )

        assertArrayEquals(expectedBytes, response.toByteArray())
    }

    @Test
    fun testFromByteArray_validResponse() {
        val bytes =
            byteArrayOf(
                0x05, // length
                0x2F, // response code
                0x01,
                0x20,
                0x03, // data (3 bytes)
            )

        val response = GetContainerPropertyResponse.fromByteArray(bytes)
        val expectedData = byteArrayOf(0x01, 0x20, 0x03)

        assertArrayEquals(expectedData, response.data)
    }

    @Test
    fun testFromByteArray_minimumValidResponse() {
        val bytes =
            byteArrayOf(
                0x03, // length (minimum: 2 base + 1 data)
                0x2F, // response code
                0x42, // data (1 byte)
            )

        val response = GetContainerPropertyResponse.fromByteArray(bytes)
        val expectedData = byteArrayOf(0x42)

        assertArrayEquals(expectedData, response.data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val bytes = byteArrayOf(0x02, 0x2F) // no data bytes
        GetContainerPropertyResponse.fromByteArray(bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_lengthMismatch() {
        val bytes = byteArrayOf(0x05, 0x2F, 0x01) // length says 5, actual is 3
        GetContainerPropertyResponse.fromByteArray(bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_wrongResponseCode() {
        val bytes = byteArrayOf(0x03, 0x2E, 0x42) // wrong response code (0x2E instead of 0x2F)
        GetContainerPropertyResponse.fromByteArray(bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_noDataBytes() {
        val bytes = byteArrayOf(0x02, 0x2F) // only length and response code, no data
        GetContainerPropertyResponse.fromByteArray(bytes)
    }

    @Test
    fun testConstants() {
        assertEquals(0x2F.toByte(), GetContainerPropertyResponse.RESPONSE_CODE)
        assertEquals(3, GetContainerPropertyResponse.MIN_LENGTH) // 2 base + 1 minimum data
    }

    @Test
    fun testRoundTrip() {
        val originalData = byteArrayOf(0x01, 0x20, 0x03, 0x04, 0x05)
        val originalResponse = GetContainerPropertyResponse(originalData)

        val bytes = originalResponse.toByteArray()
        val parsedResponse = GetContainerPropertyResponse.fromByteArray(bytes)

        assertArrayEquals(originalData, parsedResponse.data)
    }

    @Test
    fun testLargeDataArray() {
        // Test with larger data array (20 bytes)
        val data = ByteArray(20) { it.toByte() }
        val response = GetContainerPropertyResponse(data)

        val bytes = response.toByteArray()
        assertEquals(22, bytes.size) // 2 base + 20 data
        assertEquals(22.toByte(), bytes[0]) // length
        assertEquals(0x2F.toByte(), bytes[1]) // response code

        // Verify data portion
        for (i in data.indices) {
            assertEquals(data[i], bytes[i + 2])
        }
    }

    @Test
    fun testProperty1ResponseData() {
        // Property 1 typically returns 1 byte (0x01)
        val response = GetContainerPropertyResponse(byteArrayOf(0x01))
        val bytes = response.toByteArray()

        assertArrayEquals(byteArrayOf(0x03, 0x2F, 0x01), bytes)
    }

    @Test
    fun testProperty2ResponseData() {
        // Property 2 typically returns 1 byte (0x20)
        val response = GetContainerPropertyResponse(byteArrayOf(0x20))
        val bytes = response.toByteArray()

        assertArrayEquals(byteArrayOf(0x03, 0x2F, 0x20), bytes)
    }
}
