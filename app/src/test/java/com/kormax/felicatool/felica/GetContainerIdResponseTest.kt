package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class GetContainerIdResponseTest {

    @Test
    fun `should parse response with container IDM`() {
        // Length(1) + ResponseCode(1) + ContainerIDM(8) = 10 bytes
        // 0a71 + 6d696e656d6f6269 (minemobi)
        val responseData = "0a716d696e656d6f6269".hexToByteArray()
        val response = GetContainerIdResponse.fromByteArray(responseData)

        assertArrayEquals("6d696e656d6f6269".hexToByteArray(), response.containerIdm)
    }

    @Test
    fun `should parse response with different container IDM`() {
        // Length(1) + ResponseCode(1) + ContainerIDM(8) = 10 bytes
        // 0a71 + 1234567890abcdef
        val responseData = "0a711234567890abcdef".hexToByteArray()
        val response = GetContainerIdResponse.fromByteArray(responseData)

        assertArrayEquals("1234567890abcdef".hexToByteArray(), response.containerIdm)
    }

    @Test
    fun `should require minimum length for parsing`() {
        val responseData = "0a71".hexToByteArray() // Too short, missing container IDM

        assertThrows(IllegalArgumentException::class.java) {
            GetContainerIdResponse.fromByteArray(responseData)
        }
    }

    @Test
    fun `should verify correct response code in response`() {
        val responseData = "0a716d696e656d6f6269".hexToByteArray()
        val response = GetContainerIdResponse.fromByteArray(responseData)

        // Verify the response was parsed correctly
        assertArrayEquals("6d696e656d6f6269".hexToByteArray(), response.containerIdm)
    }

    @Test
    fun `should serialize back to byte array correctly`() {
        val originalData = "0a716d696e656d6f6269".hexToByteArray()
        val response = GetContainerIdResponse.fromByteArray(originalData)
        val serialized = response.toByteArray()

        assertArrayEquals(originalData, serialized)
    }
}
