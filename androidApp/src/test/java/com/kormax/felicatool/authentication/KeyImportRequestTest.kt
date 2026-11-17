package com.kormax.felicatool.authentication

import java.util.Base64
import java.util.zip.InflaterInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyImportRequestTest {
    @Test
    fun testEncodesCertificatesAsRawByteStringsInDeflatedCbor() {
        val certificates =
            listOf(byteArrayOf(0x30, 0x01, 0x02), byteArrayOf(0x30, 0x7F, 0x00, 0x11))
        val context = byteArrayOf(0xA1.toByte(), 0x61, 0x61, 0x01)
        val request = encodeKeyImportRequest("test-method", certificates, context)

        assertTrue(request.startsWith(KEY_IMPORT_REQUEST_PREFIX))
        val compressed =
            Base64.getUrlDecoder().decode(request.removePrefix(KEY_IMPORT_REQUEST_PREFIX))
        val encoded = InflaterInputStream(compressed.inputStream()).use { it.readBytes() }
        assertEquals(0xA5, encoded[0].toInt() and 0xFF)

        val decoded =
            keyImportRequestCbor.decodeFromByteArray(KeyImportRequest.serializer(), encoded)
        assertEquals("felica-tool-key-import-identity", decoded.format)
        assertEquals(1, decoded.version)
        assertEquals("test-method", decoded.method)
        assertEquals(certificates.size, decoded.certificates.size)
        certificates.zip(decoded.certificates).forEach { (expected, actual) ->
            assertArrayEquals(expected, actual)
        }
        assertArrayEquals(context, decoded.context)
        val certificateHeader = byteArrayOf(0x43, 0x30, 0x01, 0x02)
        assertTrue(encoded.toList().windowed(4).any { it == certificateHeader.toList() })
    }

    @Test
    fun testContextOmitsMissingValues() {
        val context =
            KeyImportContext(
                generated = "2026-11-18T07:12:45Z",
                challenge = byteArrayOf(1, 2, 3),
                device = KeyImportContext.Device("m", "b", "x", "d", "p", "h"),
                android = KeyImportContext.AndroidVersion("17", "37.0", "2026-11-05"),
                keystore = KeyImportContext.Keystore("tee"),
                app = KeyImportContext.App("0.34.0", 34, debug = false),
            )
        val encoded = keyImportRequestCbor.encodeToByteArray(KeyImportContext.serializer(), context)
        assertEquals(0xA6, encoded[0].toInt() and 0xFF)
        val text = encoded.decodeToString()
        listOf("soc", "implementation", "fingerprint").forEach {
            assertTrue(it, it !in text)
        }
        assertTrue("security_patch" in text)
        assertTrue("version_code" in text)
    }

    @Test
    fun testInconclusiveAlgorithmSupportIsNull() {
        val encoded =
            keyImportRequestCbor.encodeToByteArray(
                KeyImportContext.Algorithm.serializer(),
                KeyImportContext.Algorithm(true, false, null),
            )
        assertEquals(0xA3, encoded[0].toInt() and 0xFF)
        assertEquals(0xF6, encoded.last().toInt() and 0xFF)
    }
}
