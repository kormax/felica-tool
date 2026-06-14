package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class InternalAuthenticateAndReadCommandTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
        private val CHALLENGE = "00112233445566778899AABBCCDDEEFF".hexToByteArray()
    }

    @Test
    fun testInternalAuthenticateAndReadCommandCreation() {
        val idm = IDM
        val serviceCodes = arrayOf("0930".hexToByteArray())
        val blockListElements = arrayOf(BlockListElement(0, 0))
        val challenge = CHALLENGE

        val command =
            InternalAuthenticateAndReadCommand(idm, serviceCodes, blockListElements, challenge)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(1, command.serviceCodes.size)
        assertEquals("0930".hexToByteArray().toList(), command.serviceCodes[0].toList())
        assertEquals(1, command.blockListElements.size)
        assertEquals(0, command.blockListElements[0].serviceCodeListOrder)
        assertEquals(0, command.blockListElements[0].blockNumber)
        assertEquals(challenge.toList(), command.challenge.toList())
        assertEquals(0x00.toByte(), command.reserved)
    }

    @Test
    fun testInternalAuthenticateAndReadCommandWithCustomReserved() {
        val idm = IDM
        val serviceCodes = arrayOf("0930".hexToByteArray())
        val blockListElements = arrayOf(BlockListElement(0, 0))
        val challenge = CHALLENGE
        val reserved = 0x42.toByte()

        val command =
            InternalAuthenticateAndReadCommand(
                idm,
                serviceCodes,
                blockListElements,
                challenge,
                reserved,
            )

        assertEquals(reserved, command.reserved)
    }

    @Test
    fun testInternalAuthenticateAndReadCommandToByteArray() {
        val idm = IDM
        val serviceCodes = arrayOf("0930".hexToByteArray())
        val blockListElements = arrayOf(BlockListElement(0, 0))
        val challenge = CHALLENGE

        val command =
            InternalAuthenticateAndReadCommand(idm, serviceCodes, blockListElements, challenge)
        val data = command.toByteArray()

        // Expected: length(1) + command_code(1) + idm(8) + reserved(1) + num_services(1) +
        //           service_code(2) + num_blocks(1) + block_list(2) + challenge(16) = 33 bytes
        assertEquals(33, data.size)
        assertEquals(33.toByte(), data[0]) // Length
        assertEquals(0x34.toByte(), data[1]) // Command code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(0x00.toByte(), data[10]) // Reserved
        assertEquals(1.toByte(), data[11]) // Number of services
        assertEquals(
            "0930".hexToByteArray().toList(),
            data.sliceArray(12..13).toList(),
        ) // Service code
        assertEquals(1.toByte(), data[14]) // Number of blocks
        assertEquals(0x80.toByte(), data[15]) // Block list element D0 (service index 0)
        assertEquals(0x00.toByte(), data[16]) // Block list element D1 (block number 0)
        assertEquals(challenge.toList(), data.sliceArray(17..32).toList()) // Challenge
    }

    @Test
    fun testInternalAuthenticateAndReadCommandFromByteArray() {
        // Command: reserved=00, 1 service (0930), 1 block (service 0, block 0), 16-byte challenge
        val data =
            ("21340102030405060708" + // length + cmd + idm
                    "00" + // reserved
                    "01" + // num services
                    "0930" + // service code
                    "01" + // num blocks
                    "8000" + // block list element (service 0, block 0)
                    "00112233445566778899AABBCCDDEEFF" // challenge
                )
                .hexToByteArray()

        val command = InternalAuthenticateAndReadCommand.fromByteArray(data)

        assertEquals(IDM.toList(), command.idm.toList())
        assertEquals(0x00.toByte(), command.reserved)
        assertEquals(1, command.serviceCodes.size)
        assertEquals("0930".hexToByteArray().toList(), command.serviceCodes[0].toList())
        assertEquals(1, command.blockListElements.size)
        assertEquals(0, command.blockListElements[0].serviceCodeListOrder)
        assertEquals(0, command.blockListElements[0].blockNumber)
        assertEquals(CHALLENGE.toList(), command.challenge.toList())
    }

    @Test
    fun testInternalAuthenticateAndReadCommandRoundTrip() {
        val idm = IDM
        val serviceCodes = arrayOf("0930".hexToByteArray(), "1234".hexToByteArray())
        val blockListElements =
            arrayOf(BlockListElement(0, 0), BlockListElement(1, 5), BlockListElement(0, 3))
        val challenge = CHALLENGE

        val originalCommand =
            InternalAuthenticateAndReadCommand(idm, serviceCodes, blockListElements, challenge)
        val data = originalCommand.toByteArray()
        val parsedCommand = InternalAuthenticateAndReadCommand.fromByteArray(data)

        assertEquals(originalCommand.idm.toList(), parsedCommand.idm.toList())
        assertEquals(originalCommand.reserved, parsedCommand.reserved)
        assertEquals(originalCommand.serviceCodes.size, parsedCommand.serviceCodes.size)
        for (i in originalCommand.serviceCodes.indices) {
            assertEquals(
                originalCommand.serviceCodes[i].toList(),
                parsedCommand.serviceCodes[i].toList(),
            )
        }
        assertEquals(originalCommand.blockListElements.size, parsedCommand.blockListElements.size)
        for (i in originalCommand.blockListElements.indices) {
            assertEquals(originalCommand.blockListElements[i], parsedCommand.blockListElements[i])
        }
        assertEquals(originalCommand.challenge.toList(), parsedCommand.challenge.toList())
    }

    @Test
    fun testInternalAuthenticateAndReadCommandTrailingDataRoundTrip() {
        val idm = IDM
        val serviceCodes = arrayOf("0930".hexToByteArray())
        val blockListElements = arrayOf(BlockListElement(0, 0))
        val trailingData = "AABB".hexToByteArray()

        val originalCommand =
            InternalAuthenticateAndReadCommand(
                idm,
                serviceCodes,
                blockListElements,
                CHALLENGE,
                trailingData = trailingData,
            )
        val data = originalCommand.toByteArray()
        val parsedCommand = InternalAuthenticateAndReadCommand.fromByteArray(data)

        assertEquals(35, data.size)
        assertEquals(35.toByte(), data[0])
        assertEquals(CHALLENGE.toList(), parsedCommand.challenge.toList())
        assertArrayEquals(trailingData, originalCommand.trailingData)
        assertArrayEquals(trailingData, parsedCommand.trailingData)
    }

    @Test
    fun testInternalAuthenticateAndReadCommandMultipleServicesUnordered() {
        // Unlike ReadWithoutEncryption, services can be in any order
        val idm = IDM
        val serviceCodes =
            arrayOf(
                "1234".hexToByteArray(),
                "0930".hexToByteArray(), // Not in ascending order - should be allowed
                "1234".hexToByteArray(), // Repeated - should be allowed
            )
        val blockListElements = arrayOf(BlockListElement(0, 0))
        val challenge = CHALLENGE

        val command =
            InternalAuthenticateAndReadCommand(idm, serviceCodes, blockListElements, challenge)

        assertEquals(3, command.serviceCodes.size)
    }

    @Test
    fun testInternalAuthenticateAndReadCommandBlocksUnordered() {
        // Unlike ReadWithoutEncryption, blocks can be in any order
        val idm = IDM
        val serviceCodes = arrayOf("0930".hexToByteArray(), "1234".hexToByteArray())
        val blockListElements =
            arrayOf(
                BlockListElement(1, 5), // Service 1, block 5
                BlockListElement(0, 0), // Service 0, block 0 - not in service order
                BlockListElement(1, 2), // Service 1, block 2 - not in block order
            )
        val challenge = CHALLENGE

        val command =
            InternalAuthenticateAndReadCommand(idm, serviceCodes, blockListElements, challenge)

        assertEquals(3, command.blockListElements.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadCommandEmptyServiceCodes() {
        val idm = IDM
        InternalAuthenticateAndReadCommand(
            idm,
            emptyArray(),
            arrayOf(BlockListElement(0, 0)),
            CHALLENGE,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadCommandTooManyServiceCodes() {
        val idm = IDM
        val serviceCodes = (1..17).map { "0930".hexToByteArray() }.toTypedArray()
        InternalAuthenticateAndReadCommand(
            idm,
            serviceCodes,
            arrayOf(BlockListElement(0, 0)),
            CHALLENGE,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadCommandEmptyBlockList() {
        val idm = IDM
        InternalAuthenticateAndReadCommand(
            idm,
            arrayOf("0930".hexToByteArray()),
            emptyArray(),
            CHALLENGE,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadCommandTooManyBlocks() {
        val idm = IDM
        val blockListElements = (1..14).map { BlockListElement(0, it) }.toTypedArray()
        InternalAuthenticateAndReadCommand(
            idm,
            arrayOf("0930".hexToByteArray()),
            blockListElements,
            CHALLENGE,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadCommandChallengeTooShort() {
        val idm = IDM
        val shortChallenge = "00112233445566778899AABBCCDDEE".hexToByteArray() // 15 bytes
        InternalAuthenticateAndReadCommand(
            idm,
            arrayOf("0930".hexToByteArray()),
            arrayOf(BlockListElement(0, 0)),
            shortChallenge,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadCommandChallengeTooLong() {
        val idm = IDM
        val longChallenge = "00112233445566778899AABBCCDDEEFF00".hexToByteArray() // 17 bytes
        InternalAuthenticateAndReadCommand(
            idm,
            arrayOf("0930".hexToByteArray()),
            arrayOf(BlockListElement(0, 0)),
            longChallenge,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadCommandInvalidServiceIndex() {
        val idm = IDM
        val serviceCodes = arrayOf("0930".hexToByteArray()) // Only 1 service (index 0)
        val blockListElements = arrayOf(BlockListElement(1, 0)) // References service index 1
        InternalAuthenticateAndReadCommand(idm, serviceCodes, blockListElements, CHALLENGE)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testInternalAuthenticateAndReadCommandInvalidServiceCodeSize() {
        val idm = IDM
        val serviceCodes = arrayOf("09".hexToByteArray()) // Too short
        InternalAuthenticateAndReadCommand(
            idm,
            serviceCodes,
            arrayOf(BlockListElement(0, 0)),
            CHALLENGE,
        )
    }
}
