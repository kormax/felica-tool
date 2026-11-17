package com.kormax.felicatool.util

import java.io.File
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class SystemIssueInformationInterpreterTest {
    private val currentDate = LocalDate(2026, 8, 24)

    @Before
    fun loadIssuerDataset() {
        IssuerRegistry.ensureInitialized(issuersDatasetFile().readText())
    }

    @Test
    fun testDecodesGenericPackedIssuanceDateAndIssuerSerial() {
        val idi = bytes("0102ABCD3117002A")

        assertEquals(
            LocalDate(2024, 8, 23),
            SystemIssueInformationInterpreter.decodeIssuanceDate(
                systemCode = "0003",
                idi = idi,
                manufacturingDate = LocalDate(2024, 1, 1),
                currentDate = currentDate,
            ),
        )
        assertEquals(
            "JHABCD24082300042",
            SystemIssueInformationInterpreter.decodeSerialNumber("0003", idi),
        )
        assertEquals(
            "JHABC D240 8230 0042",
            SystemIssueInformationInterpreter.formatSerialNumberForDisplay(
                "0003",
                idi,
                "JHABCD24082300042",
            ),
        )
    }

    @Test
    fun testDecodesFe00IssuanceAsLittleEndianDayOffset() {
        assertEquals(
            LocalDate(2000, 1, 2),
            SystemIssueInformationInterpreter.decodeIssuanceDate(
                systemCode = "fe00",
                idi = bytes("0000010000000000"),
                manufacturingDate = LocalDate(2000, 1, 1),
                currentDate = currentDate,
            ),
        )
    }

    @Test
    fun testDecodesBcdIssuanceDate() {
        assertEquals(
            LocalDate(2012, 5, 14),
            SystemIssueInformationInterpreter.decodeIssuanceDate(
                "811D",
                bytes("2012051401020304"),
                LocalDate(2012, 5, 10),
                currentDate,
            ),
        )
    }

    @Test
    fun testDecodesCardNumbersWithoutIssuanceDate() {
        val idi = bytes("0101ABCD0BEBC200")
        val pmi = bytes("1234567800E1F505")

        assertEquals(
            "100000000",
            SystemIssueInformationInterpreter.decodeSerialNumber("8005", idi, pmi),
        )
        assertEquals(
            "200000000",
            SystemIssueInformationInterpreter.decodeSerialNumber("8008", idi, pmi),
        )
        assertNull(SystemIssueInformationInterpreter.decodeSerialNumber("8005", idi))
        assertNull(
            SystemIssueInformationInterpreter.decodeIssuanceDate(
                systemCode = "8008",
                idi = idi,
                manufacturingDate = null,
                currentDate = currentDate,
            ),
        )
    }

    @Test
    fun testRejectsUnverifiedOrImplausibleInterpretations() {
        val idi = bytes("0102ABCD3117002A")

        assertNull(SystemIssueInformationInterpreter.decodeSerialNumber("04C7", idi))
        assertNull(
            SystemIssueInformationInterpreter.decodeIssuanceDate(
                systemCode = "04C7",
                idi = idi,
                manufacturingDate = null,
                currentDate = currentDate,
            ),
        )
        assertNull(
            SystemIssueInformationInterpreter.decodeSerialNumber(
                "811D",
                bytes("FFFFABCD3117002A"),
            ),
        )
        assertNull(
            SystemIssueInformationInterpreter.decodeIssuanceDate(
                systemCode = "811D",
                idi = idi,
                manufacturingDate = LocalDate(2025, 1, 1),
                currentDate = currentDate,
            ),
        )
        assertNull(
            SystemIssueInformationInterpreter.decodeIssuanceDate(
                systemCode = "811D",
                idi = bytes("0102ABCD31E1002A"),
                manufacturingDate = null,
                currentDate = currentDate,
            ),
        )
    }

    private fun bytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private fun issuersDatasetFile(): File {
        var directory: File? = File(System.getProperty("user.dir").orEmpty()).absoluteFile
        while (directory != null) {
            val candidate = File(directory, DATASET_RELATIVE_PATH)
            if (candidate.isFile) {
                return candidate
            }
            directory = directory.parentFile
        }
        error("Could not locate $DATASET_RELATIVE_PATH")
    }

    private companion object {
        const val DATASET_RELATIVE_PATH =
            "shared/src/commonMain/composeResources/files/issuers.json"
    }
}
