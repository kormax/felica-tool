package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

class Authentication1DesCommandTest {

    companion object {
        private val IDM = "0102030405060708".hexToByteArray()
        private val CHALLENGE_1A = "1122334455667788".hexToByteArray()
    }

    @Test
    fun testAuthentication1DesCommandCreationWithCodes() {
        val idm = IDM
        val areaCodes = arrayOf("0010".hexToByteArray(), "0020".hexToByteArray())
        val serviceCodes = arrayOf("0008".hexToByteArray())
        val challenge1A = CHALLENGE_1A

        val command = Authentication1DesCommand(idm, areaCodes, serviceCodes, challenge1A)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(2, command.areaCodes.size)
        assertEquals(1, command.serviceCodes.size)
        assertEquals("0010".hexToByteArray().toList(), command.areaCodes[0].toList())
        assertEquals("0020".hexToByteArray().toList(), command.areaCodes[1].toList())
        assertEquals("0008".hexToByteArray().toList(), command.serviceCodes[0].toList())
        assertEquals(challenge1A.toList(), command.challenge1A.toList())
    }

    @Test
    fun testAuthentication1DesCommandCreationWithNodes() {
        val idm = IDM
        val areaNodes =
            listOf(Area(1, AreaAttribute.CAN_CREATE_SUB_AREA, 10, AreaAttribute.END_SUB_AREA))
        val serviceNodes = listOf(Service(100, ServiceAttribute.RANDOM_RW_WITH_KEY))
        val challenge1A = CHALLENGE_1A

        val command = Authentication1DesCommand(idm, areaNodes, serviceNodes, challenge1A)

        assertEquals(idm.toList(), command.idm.toList())
        assertEquals(1, command.areaCodes.size)
        assertEquals(1, command.serviceCodes.size)
        assertEquals(areaNodes[0].code.toList(), command.areaCodes[0].toList())
        assertEquals(serviceNodes[0].code.toList(), command.serviceCodes[0].toList())
        assertEquals(challenge1A.toList(), command.challenge1A.toList())
    }

    @Test
    fun testAuthentication1DesCommandToByteArray() {
        val idm = IDM
        val areaCodes = arrayOf("0010".hexToByteArray())
        val serviceCodes = arrayOf("0008".hexToByteArray())
        val challenge1A = CHALLENGE_1A

        val command = Authentication1DesCommand(idm, areaCodes, serviceCodes, challenge1A)
        val data = command.toByteArray()

        // Expected: length(1) + command_code(1) + idm(8) + area_count(1) + area_code(2) +
        //           service_count(1) + service_code(2) + challenge1A(8) = 24 bytes
        assertEquals(24, data.size)
        assertEquals(24.toByte(), data[0]) // Length
        assertEquals(0x10.toByte(), data[1]) // Command code
        assertEquals(idm.toList(), data.sliceArray(2..9).toList()) // IDM
        assertEquals(1.toByte(), data[10]) // Number of area codes
        assertEquals(
            "0010".hexToByteArray().toList(),
            data.sliceArray(11..12).toList(),
        ) // Area code
        assertEquals(1.toByte(), data[13]) // Number of service codes
        assertEquals(
            "0008".hexToByteArray().toList(),
            data.sliceArray(14..15).toList(),
        ) // Service code
        assertEquals(challenge1A.toList(), data.sliceArray(16..23).toList()) // Challenge1A
    }

    @Test
    fun testAuthentication1DesCommandFromByteArray() {
        // Command with 1 area code, 1 service code, and challenge1A
        val data = "181001020304050607080100100100081122334455667788".hexToByteArray()

        val command = Authentication1DesCommand.fromByteArray(data)

        assertEquals(IDM.toList(), command.idm.toList())
        assertEquals(1, command.areaCodes.size)
        assertEquals(1, command.serviceCodes.size)
        assertEquals("0010".hexToByteArray().toList(), command.areaCodes[0].toList())
        assertEquals("0008".hexToByteArray().toList(), command.serviceCodes[0].toList())
        assertEquals(CHALLENGE_1A.toList(), command.challenge1A.toList())
    }

    @Test
    fun testAuthentication1DesCommandRoundTrip() {
        val idm = IDM
        val areaCodes = arrayOf("0010".hexToByteArray(), "0020".hexToByteArray())
        val serviceCodes = arrayOf("0008".hexToByteArray(), "0009".hexToByteArray())
        val challenge1A = CHALLENGE_1A

        val originalCommand = Authentication1DesCommand(idm, areaCodes, serviceCodes, challenge1A)
        val data = originalCommand.toByteArray()
        val parsedCommand = Authentication1DesCommand.fromByteArray(data)

        assertEquals(originalCommand.idm.toList(), parsedCommand.idm.toList())
        assertEquals(originalCommand.areaCodes.size, parsedCommand.areaCodes.size)
        assertEquals(originalCommand.serviceCodes.size, parsedCommand.serviceCodes.size)
        assertEquals(originalCommand.areaCodes[0].toList(), parsedCommand.areaCodes[0].toList())
        assertEquals(originalCommand.areaCodes[1].toList(), parsedCommand.areaCodes[1].toList())
        assertEquals(
            originalCommand.serviceCodes[0].toList(),
            parsedCommand.serviceCodes[0].toList(),
        )
        assertEquals(
            originalCommand.serviceCodes[1].toList(),
            parsedCommand.serviceCodes[1].toList(),
        )
        assertEquals(originalCommand.challenge1A.toList(), parsedCommand.challenge1A.toList())
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1DesCommandEmptyNodes() {
        val idm = IDM
        val areaCodes = emptyArray<ByteArray>()
        val serviceCodes = emptyArray<ByteArray>()
        val challenge1A = CHALLENGE_1A
        Authentication1DesCommand(idm, areaCodes, serviceCodes, challenge1A)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1DesCommandInvalidChallenge1ASize() {
        val idm = IDM
        val areaCodes = arrayOf("0010".hexToByteArray())
        val serviceCodes = emptyArray<ByteArray>()
        val invalidChallenge = "112233".hexToByteArray() // Too short
        Authentication1DesCommand(idm, areaCodes, serviceCodes, invalidChallenge)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1DesCommandInvalidAreaCodeSize() {
        val idm = IDM
        val areaCodes = arrayOf("00".hexToByteArray()) // Too short
        val serviceCodes = emptyArray<ByteArray>()
        val challenge1A = CHALLENGE_1A
        Authentication1DesCommand(idm, areaCodes, serviceCodes, challenge1A)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testAuthentication1DesCommandInvalidServiceCodeSize() {
        val idm = IDM
        val areaCodes = emptyArray<ByteArray>()
        val serviceCodes = arrayOf("00".hexToByteArray()) // Too short
        val challenge1A = CHALLENGE_1A
        Authentication1DesCommand(idm, areaCodes, serviceCodes, challenge1A)
    }
}
