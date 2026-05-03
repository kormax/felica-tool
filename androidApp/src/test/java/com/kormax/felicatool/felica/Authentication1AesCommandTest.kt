package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class Authentication1AesCommandTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
        private val CHALLENGE_1A =
            "AABBCCDDEEFF00112233445566778899".hexToByteArray() // 16 bytes for AES
    }

    @Test
    fun testAuthentication1AesCommandCreationWithNodeCodes() {
        val idm = IDM
        val nodeCodes =
            arrayOf("0010".hexToByteArray(), "0020".hexToByteArray(), "1001".hexToByteArray())
        val challenge1A = CHALLENGE_1A
        val flag: Byte = 0x00

        val command = Authentication1AesCommand(idm, nodeCodes, challenge1A, flag)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(nodeCodes.size, command.nodeCodes.size)
        assertEquals(challenge1A.toList(), command.challenge1A.toList())
        assertEquals(flag, command.flag)
        assertEquals(nodeCodes.size, command.timeoutUnits)

        // Verify node codes
        assertEquals(nodeCodes[0].toList(), command.nodeCodes[0].toList())
        assertEquals(nodeCodes[1].toList(), command.nodeCodes[1].toList())
        assertEquals(nodeCodes[2].toList(), command.nodeCodes[2].toList())
    }

    @Test
    fun testAuthentication1AesCommandCreationWithNodes() {
        val idm = IDM
        val nodes =
            listOf(
                Area(1, AreaAttribute.CanCreateSubArea, 2, AreaAttribute.CanCreateSubArea),
                Service(16, ServiceAttribute.RandomRwWithKey),
            )
        val challenge1A = CHALLENGE_1A
        val flag: Byte = 0x03

        val command = Authentication1AesCommand(idm, nodes, challenge1A, flag)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(nodes.size, command.nodeCodes.size)
        assertEquals(challenge1A.toList(), command.challenge1A.toList())
        assertEquals(flag, command.flag)
        assertEquals(nodes[0].code.toList(), command.nodeCodes[0].toList())
        assertEquals(nodes[1].code.toList(), command.nodeCodes[1].toList())
    }

    @Test
    fun testAuthentication1AesCommandToByteArray() {
        val idm = IDM
        val nodeCodes = arrayOf("0010".hexToByteArray(), "1001".hexToByteArray())
        val challenge1A = CHALLENGE_1A
        val flag: Byte = 0x03

        val command = Authentication1AesCommand(idm, nodeCodes, challenge1A, flag)
        val data = command.toByteArray()

        // Expected: length(1) + command_code(1) + idm(8) + flag(1) + node_count(1) + node_codes(4)
        // + challenge1A(16) = 32 bytes
        assertEquals(32, data.size)
        assertEquals(32.toByte(), data[0]) // Length
        assertEquals(0x40.toByte(), data[1]) // Command code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(flag, data[10]) // Flag byte
        assertEquals(nodeCodes.size.toByte(), data[11]) // Node count
        assertEquals(nodeCodes[0].toList(), data.sliceArray(12..13).toList()) // First node code
        assertEquals(nodeCodes[1].toList(), data.sliceArray(14..15).toList()) // Second node code
        assertEquals(challenge1A.toList(), data.sliceArray(16..31).toList()) // Challenge1A
    }

    @Test
    fun testAuthentication1AesCommandFromByteArray() {
        // Command with IDM, flag, two node codes, and challenge1A
        // Length(1) + Command(1) + IDM(8) + Flag(1) + NodeCount(1) + NodeCodes(4) + Challenge1A(16)
        // = 32 bytes
        val data =
            "20400102030405060708030200101001AABBCCDDEEFF00112233445566778899".hexToByteArray()

        val command = Authentication1AesCommand.fromByteArray(data)

        assertEquals(IDM.toList(), command.idm.toList())
        assertEquals(2, command.nodeCodes.size)
        assertEquals(0x03.toByte(), command.flag)
        assertEquals("0010".hexToByteArray().toList(), command.nodeCodes[0].toList())
        assertEquals("1001".hexToByteArray().toList(), command.nodeCodes[1].toList())
        assertEquals(CHALLENGE_1A.toList(), command.challenge1A.toList())
    }

    @Test
    fun testAuthentication1AesCommandRoundTrip() {
        val idm = IDM
        val nodeCodes = arrayOf("0010".hexToByteArray(), "1001".hexToByteArray())
        val challenge1A = CHALLENGE_1A
        val flag: Byte = 0x05

        val originalCommand = Authentication1AesCommand(idm, nodeCodes, challenge1A, flag)
        val data = originalCommand.toByteArray()
        val parsedCommand = Authentication1AesCommand.fromByteArray(data)

        assertEquals(originalCommand.idm.toList(), parsedCommand.idm.toList())
        assertEquals(originalCommand.nodeCodes.size, parsedCommand.nodeCodes.size)
        assertEquals(originalCommand.challenge1A.toList(), parsedCommand.challenge1A.toList())
        assertEquals(originalCommand.flag, parsedCommand.flag)

        // Verify node codes
        assertEquals(originalCommand.nodeCodes[0].toList(), parsedCommand.nodeCodes[0].toList())
        assertEquals(originalCommand.nodeCodes[1].toList(), parsedCommand.nodeCodes[1].toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesCommandEmptyNodes() {
        val idm = IDM
        val nodeCodes = emptyArray<ByteArray>()
        val challenge1A = CHALLENGE_1A
        Authentication1AesCommand(idm, nodeCodes, challenge1A)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesCommandInvalidChallenge1ASize() {
        val idm = IDM
        val nodeCodes = arrayOf("0010".hexToByteArray())
        val invalidChallenge = "112233445566778899AABBCC".hexToByteArray() // Too short (12 bytes)
        Authentication1AesCommand(idm, nodeCodes, invalidChallenge)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesCommandInvalidNodeCodeSize() {
        val idm = IDM
        val nodeCodes = arrayOf("00".hexToByteArray()) // Too short
        val challenge1A = CHALLENGE_1A
        Authentication1AesCommand(idm, nodeCodes, challenge1A)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1AesCommandTooManyNodes() {
        val idm = IDM
        val nodeCodes = Array(17) { "0010".hexToByteArray() } // Too many nodes (max is 16)
        val challenge1A = CHALLENGE_1A
        Authentication1AesCommand(idm, nodeCodes, challenge1A)
    }
}
