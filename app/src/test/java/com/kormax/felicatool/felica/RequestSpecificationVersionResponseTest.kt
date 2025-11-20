package com.kormax.felicatool.felica

import org.junit.Assert.*
import org.junit.Test

/** Unit tests for RequestSpecificationVersionResponse */
class RequestSpecificationVersionResponseTest {

    private val testIdm =
        byteArrayOf(
            0x01.toByte(),
            0x23.toByte(),
            0x45.toByte(),
            0x67.toByte(),
            0x89.toByte(),
            0xAB.toByte(),
            0xCD.toByte(),
            0xEF.toByte(),
        )

    @Test
    fun testRequestSpecificationVersionResponse_creation() {
        val basicVersion = OptionVersion(5, 0, 0) // 5.0.0 in BCD
        val desVersion = OptionVersion(2, 0, 0) // 2.0.0 in BCD
        val specialOptionVersion = OptionVersion(0, 0, 0) // 0.0.0
        val extendedOverlapVersion = OptionVersion(1, 0, 0) // 1.0.0 in BCD
        val valueLimitedPurseVersion = OptionVersion(3, 0, 0) // 3.0.0 in BCD
        val communicationWithMacVersion = OptionVersion(4, 0, 0) // 4.0.0 in BCD

        val specificationVersion =
            SpecificationVersion(
                formatVersion = 0x00,
                basicVersion = basicVersion,
                desOptionVersion = desVersion,
                specialOptionVersion = specialOptionVersion,
                extendedOverlapOptionVersion = extendedOverlapVersion,
                valueLimitedPurseServiceOptionVersion = valueLimitedPurseVersion,
                communicationWithMacOptionVersion = communicationWithMacVersion,
            )

        val response =
            RequestSpecificationVersionResponse(
                idm = testIdm,
                statusFlag1 = 0x00,
                statusFlag2 = 0x00,
                specificationVersion = specificationVersion,
            )

        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertEquals(0x00.toByte(), response.specificationVersion?.formatVersion)
        assertEquals(basicVersion, response.specificationVersion?.basicVersion)
        assertEquals(desVersion, response.specificationVersion?.desOptionVersion)
        assertEquals(
            extendedOverlapVersion,
            response.specificationVersion?.extendedOverlapOptionVersion,
        )
        assertEquals(
            valueLimitedPurseVersion,
            response.specificationVersion?.valueLimitedPurseServiceOptionVersion,
        )
        assertEquals(
            communicationWithMacVersion,
            response.specificationVersion?.communicationWithMacOptionVersion,
        )
        assertTrue(response.isStatusSuccessful)
    }

    @Test
    fun testRequestSpecificationVersionResponse_unsuccessful() {
        val response =
            RequestSpecificationVersionResponse(
                idm = testIdm,
                statusFlag1 = 0x01, // Error
                statusFlag2 = 0x00,
                specificationVersion = null,
            )

        assertEquals(0x01.toByte(), response.statusFlag1)
        assertFalse(response.isStatusSuccessful)
        assertNull(response.specificationVersion?.formatVersion)
        assertNull(response.specificationVersion?.basicVersion)
    }

    @Test
    fun testRequestSpecificationVersionResponse_toByteArray() {
        val basicVersion = OptionVersion(5, 0, 0) // 5.0.0
        val desVersion = OptionVersion(2, 0, 0) // 2.0.0
        val specialOptionVersion = OptionVersion(0, 0, 0) // 0.0.0
        val extendedOverlapVersion = OptionVersion(1, 0, 0) // 1.0.0
        val valueLimitedPurseVersion = OptionVersion(3, 0, 0) // 3.0.0
        val communicationWithMacVersion = OptionVersion(4, 0, 0) // 4.0.0

        val specificationVersion =
            SpecificationVersion(
                formatVersion = 0x00,
                basicVersion = basicVersion,
                desOptionVersion = desVersion,
                specialOptionVersion = specialOptionVersion,
                extendedOverlapOptionVersion = extendedOverlapVersion,
                valueLimitedPurseServiceOptionVersion = valueLimitedPurseVersion,
                communicationWithMacOptionVersion = communicationWithMacVersion,
            )

        val response =
            RequestSpecificationVersionResponse(
                idm = testIdm,
                statusFlag1 = 0x00,
                statusFlag2 = 0x00,
                specificationVersion = specificationVersion,
            )

        val bytes = response.toByteArray()

        // Check total size: length(1) + response_code(1) + idm(8) + status1(1) + status2(1) +
        // version_data(14) = 26
        assertEquals(26, bytes.size)

        // Check response code
        assertEquals(RequestSpecificationVersionResponse.RESPONSE_CODE.toByte(), bytes[1])

        // Check IDM
        for (i in 0..7) {
            assertEquals(testIdm[i], bytes[2 + i])
        }

        // Check status flags
        assertEquals(0x00.toByte(), bytes[10])
        assertEquals(0x00.toByte(), bytes[11])

        // Check format version
        assertEquals(0x00.toByte(), bytes[12])

        // Check basic version (little endian)
        assertEquals(basicVersion.toByteArray()[0], bytes[13])
        assertEquals(basicVersion.toByteArray()[1], bytes[14])

        // Check number of options
        assertEquals(5.toByte(), bytes[15])

        // Check DES option version
        assertEquals(desVersion.toByteArray()[0], bytes[16])
        assertEquals(desVersion.toByteArray()[1], bytes[17])

        // Check reserved bytes (special option)
        assertEquals(0x00.toByte(), bytes[18])
        assertEquals(0x80.toByte(), bytes[19])

        // Check Extended Overlap option version
        assertEquals(extendedOverlapVersion.toByteArray()[0], bytes[20])
        assertEquals(extendedOverlapVersion.toByteArray()[1], bytes[21])

        // Check Value-Limited Purse Service option version
        assertEquals(valueLimitedPurseVersion.toByteArray()[0], bytes[22])
        assertEquals(valueLimitedPurseVersion.toByteArray()[1], bytes[23])

        // Check Communication with MAC option version
        assertEquals(communicationWithMacVersion.toByteArray()[0], bytes[24])
        assertEquals(communicationWithMacVersion.toByteArray()[1], bytes[25])
    }

    @Test
    fun testFromByteArray_valid() {
        val basicVersion = OptionVersion(5, 0, 0)
        val desVersion = OptionVersion(2, 0, 0)
        val specialOptionVersion = OptionVersion(0, 0, 0)
        val extendedOverlapVersion = OptionVersion(1, 0, 0)
        val valueLimitedPurseVersion = OptionVersion(3, 0, 0)
        val communicationWithMacVersion = OptionVersion(4, 0, 0)

        val data =
            byteArrayOf(
                26, // length
                RequestSpecificationVersionResponse.RESPONSE_CODE.toByte(), // response code
                *testIdm, // IDM
                0x00, // status flag 1
                0x00, // status flag 2
                0x00, // format version
                *basicVersion.toByteArray(), // basic version
                0x05, // number of options
                *desVersion.toByteArray(), // DES option
                0x00,
                0x00, // reserved (special option)
                *extendedOverlapVersion.toByteArray(), // Extended Overlap option
                *valueLimitedPurseVersion.toByteArray(), // Value-Limited Purse Service option
                *communicationWithMacVersion.toByteArray(), // Communication with MAC option
            )

        val response = RequestSpecificationVersionResponse.fromByteArray(data)

        assertEquals(0x00.toByte(), response.statusFlag1)
        assertEquals(0x00.toByte(), response.statusFlag2)
        assertEquals(0x00.toByte(), response.specificationVersion?.formatVersion)
        assertEquals(basicVersion, response.specificationVersion?.basicVersion)
        assertEquals(desVersion, response.specificationVersion?.desOptionVersion)
        assertEquals(
            OptionVersion(0, 0, 0),
            response.specificationVersion?.specialOptionVersion,
        ) // Special option is 0x0000
        assertEquals(
            extendedOverlapVersion,
            response.specificationVersion?.extendedOverlapOptionVersion,
        )
        assertEquals(
            valueLimitedPurseVersion,
            response.specificationVersion?.valueLimitedPurseServiceOptionVersion,
        )
        assertEquals(
            communicationWithMacVersion,
            response.specificationVersion?.communicationWithMacOptionVersion,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun testFromByteArray_tooShort() {
        val shortData =
            byteArrayOf(
                10,
                RequestSpecificationVersionResponse.RESPONSE_CODE.toByte(),
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
                0x00,
            ) // Too short
        RequestSpecificationVersionResponse.fromByteArray(shortData)
    }

    @Test
    fun testRequestSpecificationVersionResponse_roundTrip() {
        val basicVersion = OptionVersion(5, 0, 0)
        val desVersion = OptionVersion(2, 0, 0)
        val specialOptionVersion = OptionVersion(0, 0, 0)
        val extendedOverlapVersion = OptionVersion(1, 0, 0)
        val valueLimitedPurseVersion = OptionVersion(3, 0, 0)
        val communicationWithMacVersion = OptionVersion(4, 0, 0)

        val specificationVersion =
            SpecificationVersion(
                formatVersion = 0x00,
                basicVersion = basicVersion,
                desOptionVersion = desVersion,
                specialOptionVersion = specialOptionVersion,
                extendedOverlapOptionVersion = extendedOverlapVersion,
                valueLimitedPurseServiceOptionVersion = valueLimitedPurseVersion,
                communicationWithMacOptionVersion = communicationWithMacVersion,
            )

        val original =
            RequestSpecificationVersionResponse(
                idm = testIdm,
                statusFlag1 = 0x00,
                statusFlag2 = 0x00,
                specificationVersion = specificationVersion,
            )

        val bytes = original.toByteArray()
        val parsed = RequestSpecificationVersionResponse.fromByteArray(bytes)

        assertEquals(original.statusFlag1, parsed.statusFlag1)
        assertEquals(original.statusFlag2, parsed.statusFlag2)
        assertEquals(
            original.specificationVersion?.formatVersion,
            parsed.specificationVersion?.formatVersion,
        )
        assertEquals(
            original.specificationVersion?.basicVersion,
            parsed.specificationVersion?.basicVersion,
        )
        assertEquals(
            original.specificationVersion?.desOptionVersion,
            parsed.specificationVersion?.desOptionVersion,
        )
        assertEquals(
            original.specificationVersion?.extendedOverlapOptionVersion,
            parsed.specificationVersion?.extendedOverlapOptionVersion,
        )
        assertEquals(
            original.specificationVersion?.valueLimitedPurseServiceOptionVersion,
            parsed.specificationVersion?.valueLimitedPurseServiceOptionVersion,
        )
        assertEquals(
            original.specificationVersion?.communicationWithMacOptionVersion,
            parsed.specificationVersion?.communicationWithMacOptionVersion,
        )
    }
}
