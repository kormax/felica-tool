package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class InternalAuthenticateAndReadResponseTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
        private val BLOCK_DATA =
            "31303033313132333032313935343931".hexToByteArray() // "1003112302195491"
        private val CHALLENGE = "AABBCCDDEEFF00112233445566778899".hexToByteArray()
        private val MAC = "00112233445566778899AABBCCDDEEFF".hexToByteArray()
    }

    @Test
    fun testInternalAuthenticateAndReadResponseCreationSuccess() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte()
        val statusFlag2 = 0x00.toByte()
        val blockData = arrayOf(BLOCK_DATA)
        val challenge = CHALLENGE
        val mac = MAC

        val response =
            InternalAuthenticateAndReadResponse(
                idm,
                statusFlag1,
                statusFlag2,
                blockData,
                challenge,
                mac,
            )

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(statusFlag1, response.statusFlag1)
        assertEquals(statusFlag2, response.statusFlag2)
        assertTrue(response.isStatusSuccessful)
        assertEquals(1, response.blockData.size)
        assertEquals(BLOCK_DATA.toList(), response.blockData[0].toList())
        assertEquals(challenge.toList(), response.challenge.toList())
        assertEquals(mac.toList(), response.mac.toList())
    }

    @Test
    fun testInternalAuthenticateAndReadResponseCreationError() {
        val idm = IDM
        val statusFlag1 = 0xFF.toByte()
        val statusFlag2 = 0xA1.toByte() // Illegal Number of Service

        val response =
            InternalAuthenticateAndReadResponse(
                idm,
                statusFlag1,
                statusFlag2,
                emptyArray(),
                ByteArray(0),
                ByteArray(0),
            )

        assertEquals(idm.toList(), response.idm.toList())
        assertEquals(statusFlag1, response.statusFlag1)
        assertEquals(statusFlag2, response.statusFlag2)
        assertFalse(response.isStatusSuccessful)
        assertEquals(0, response.blockData.size)
    }

    @Test
    fun testInternalAuthenticateAndReadResponseToByteArraySuccess() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte()
        val statusFlag2 = 0x00.toByte()
        val blockData = arrayOf(BLOCK_DATA)
        val challenge = CHALLENGE
        val mac = MAC

        val response =
            InternalAuthenticateAndReadResponse(
                idm,
                statusFlag1,
                statusFlag2,
                blockData,
                challenge,
                mac,
            )
        val data = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + sf1(1) + sf2(1) + num_blocks(1) +
        //           block_data(16) + challenge(16) + mac(16) = 61 bytes
        assertEquals(61, data.size)
        assertEquals(61.toByte(), data[0]) // Length
        assertEquals(0x35.toByte(), data[1]) // Response code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(0x00.toByte(), data[10]) // Status Flag 1
        assertEquals(0x00.toByte(), data[11]) // Status Flag 2
        assertEquals(1.toByte(), data[12]) // Number of blocks
        assertEquals(BLOCK_DATA.toList(), data.sliceArray(13..28).toList()) // Block data
        assertEquals(challenge.toList(), data.sliceArray(29..44).toList()) // Challenge
        assertEquals(mac.toList(), data.sliceArray(45..60).toList()) // MAC
    }

    @Test
    fun testInternalAuthenticateAndReadResponseToByteArrayError() {
        val idm = IDM
        val statusFlag1 = 0xFF.toByte()
        val statusFlag2 = 0xA1.toByte()

        val response =
            InternalAuthenticateAndReadResponse(
                idm,
                statusFlag1,
                statusFlag2,
                emptyArray(),
                ByteArray(0),
                ByteArray(0),
            )
        val data = response.toByteArray()

        // Expected: length(1) + response_code(1) + idm(8) + sf1(1) + sf2(1) = 12 bytes
        assertEquals(12, data.size)
        assertEquals(12.toByte(), data[0]) // Length
        assertEquals(0x35.toByte(), data[1]) // Response code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(0xFF.toByte(), data[10]) // Status Flag 1
        assertEquals(0xA1.toByte(), data[11]) // Status Flag 2
    }

    @Test
    fun testInternalAuthenticateAndReadResponseFromByteArraySuccess() {
        // Response: success (00 00), 1 block, block data, challenge, mac
        val data =
            ("3D350102030405060708" + // length + response_code + idm
                    "0000" + // status flags
                    "01" + // num blocks
                    "31303033313132333032313935343931" + // block data
                    "AABBCCDDEEFF00112233445566778899" + // challenge
                    "00112233445566778899AABBCCDDEEFF" // mac
                )
                .hexToByteArray()

        val response = InternalAuthenticateAndReadResponse.fromByteArray(data)

        assertEquals(IDM.toList(), response.idm.toList())
        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertTrue(response.isStatusSuccessful)
        assertEquals(1, response.blockData.size)
        assertEquals(BLOCK_DATA.toList(), response.blockData[0].toList())
        assertEquals(CHALLENGE.toList(), response.challenge.toList())
        assertEquals(MAC.toList(), response.mac.toList())
    }

    @Test
    fun testInternalAuthenticateAndReadResponseFromByteArrayError() {
        // Response: error (FF A1)
        val data =
            ("0C350102030405060708" + // length + response_code + idm
                    "FFA1" // status flags (error)
                )
                .hexToByteArray()

        val response = InternalAuthenticateAndReadResponse.fromByteArray(data)

        assertEquals(IDM.toList(), response.idm.toList())
        assertEquals(0xFF.toByte(), response.statusFlag1)
        assertEquals(0xA1.toByte(), response.statusFlag2)
        assertFalse(response.isStatusSuccessful)
        assertEquals(0, response.blockData.size)
    }

    @Test
    fun testInternalAuthenticateAndReadResponseRoundTripSuccess() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte()
        val statusFlag2 = 0x00.toByte()
        val blockData = arrayOf(BLOCK_DATA, "FFEEDDCCBBAA99887766554433221100".hexToByteArray())
        val challenge = CHALLENGE
        val mac = MAC

        val originalResponse =
            InternalAuthenticateAndReadResponse(
                idm,
                statusFlag1,
                statusFlag2,
                blockData,
                challenge,
                mac,
            )
        val data = originalResponse.toByteArray()
        val parsedResponse = InternalAuthenticateAndReadResponse.fromByteArray(data)

        assertEquals(originalResponse.idm.toList(), parsedResponse.idm.toList())
        assertEquals(originalResponse.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(originalResponse.statusFlag2, parsedResponse.statusFlag2)
        assertEquals(originalResponse.blockData.size, parsedResponse.blockData.size)
        for (i in originalResponse.blockData.indices) {
            assertEquals(
                originalResponse.blockData[i].toList(),
                parsedResponse.blockData[i].toList(),
            )
        }
        assertEquals(originalResponse.challenge.toList(), parsedResponse.challenge.toList())
        assertEquals(originalResponse.mac.toList(), parsedResponse.mac.toList())
    }

    @Test
    fun testInternalAuthenticateAndReadResponseRoundTripError() {
        val idm = IDM
        val statusFlag1 = 0xFF.toByte()
        val statusFlag2 = 0xA2.toByte() // Illegal Number of Block

        val originalResponse =
            InternalAuthenticateAndReadResponse(
                idm,
                statusFlag1,
                statusFlag2,
                emptyArray(),
                ByteArray(0),
                ByteArray(0),
            )
        val data = originalResponse.toByteArray()
        val parsedResponse = InternalAuthenticateAndReadResponse.fromByteArray(data)

        assertEquals(originalResponse.idm.toList(), parsedResponse.idm.toList())
        assertEquals(originalResponse.statusFlag1, parsedResponse.statusFlag1)
        assertEquals(originalResponse.statusFlag2, parsedResponse.statusFlag2)
        assertEquals(originalResponse.blockData.size, parsedResponse.blockData.size)
    }

    @Test
    fun testInternalAuthenticateAndReadResponseMultipleBlocks() {
        val idm = IDM
        val statusFlag1 = 0x00.toByte()
        val statusFlag2 = 0x00.toByte()
        val block1 = "11111111111111111111111111111111".hexToByteArray()
        val block2 = "22222222222222222222222222222222".hexToByteArray()
        val block3 = "33333333333333333333333333333333".hexToByteArray()
        val blockData = arrayOf(block1, block2, block3)
        val challenge = CHALLENGE
        val mac = MAC

        val response =
            InternalAuthenticateAndReadResponse(
                idm,
                statusFlag1,
                statusFlag2,
                blockData,
                challenge,
                mac,
            )
        val data = response.toByteArray()
        val parsedResponse = InternalAuthenticateAndReadResponse.fromByteArray(data)

        assertEquals(3, parsedResponse.blockData.size)
        assertEquals(block1.toList(), parsedResponse.blockData[0].toList())
        assertEquals(block2.toList(), parsedResponse.blockData[1].toList())
        assertEquals(block3.toList(), parsedResponse.blockData[2].toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadResponseInvalidBlockSize() {
        val idm = IDM
        val invalidBlock = "1122334455".hexToByteArray() // Too short
        InternalAuthenticateAndReadResponse(idm, 0x00, 0x00, arrayOf(invalidBlock), CHALLENGE, MAC)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadResponseInvalidChallengeSize() {
        val idm = IDM
        val invalidChallenge = "AABBCCDDEEFF".hexToByteArray() // Too short
        InternalAuthenticateAndReadResponse(
            idm,
            0x00,
            0x00,
            arrayOf(BLOCK_DATA),
            invalidChallenge,
            MAC,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadResponseInvalidMacSize() {
        val idm = IDM
        val invalidMac = "00112233445566".hexToByteArray() // Too short
        InternalAuthenticateAndReadResponse(
            idm,
            0x00,
            0x00,
            arrayOf(BLOCK_DATA),
            CHALLENGE,
            invalidMac,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadResponseFromByteArrayTooShort() {
        val data = "0B35".hexToByteArray() // Too short
        InternalAuthenticateAndReadResponse.fromByteArray(data)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadResponseFromByteArrayWrongResponseCode() {
        val data =
            ("0C360102030405060708" + // Wrong response code (0x36 instead of 0x35)
                    "0000")
                .hexToByteArray()
        InternalAuthenticateAndReadResponse.fromByteArray(data)
    }
}
