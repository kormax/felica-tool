package com.kormax.felicatool.felica

import com.kormax.felicatool.security.AndroidDesCryptography
import com.kormax.felicatool.security.DesSecureSession
import com.kormax.felicatool.security.SecretBytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Authentication2DesTest {
    @Test
    fun testCommandRoundTrips() {
        val command =
            Authentication2Des.Command(
                idm = "0102030405060708".hexToByteArray(),
                challenge2B = "1112131415161718".hexToByteArray(),
            )

        val parsed = Authentication2Des.Command.fromByteArray(command.toByteArray())

        assertArrayEquals(command.toByteArray(), parsed.toByteArray())
        assertEquals(0x12, command.toByteArray()[1].toInt())
    }

    @Test
    fun testEncryptedResponseIsOneOpaque32BytePayload() {
        val encryptedData = ByteArray(32) { it.toByte() }
        val response = Authentication2Des.Response.Encrypted(encryptedData)
        val parsed = Authentication2Des.Response.Encrypted.fromByteArray(response.toByteArray())

        assertArrayEquals(encryptedData, parsed.encryptedData)
        assertEquals(34, response.toByteArray().size)
    }

    @Test
    fun testSessionOpensKnownResponse() {
        val plain =
            session(TRANSACTION_ID).use {
                Authentication2Des.Response.Plain.fromByteArray(
                    it.open(RESPONSE_CODE, KNOWN_RESPONSE.hexToByteArray()),
                )
            }

        assertArrayEquals("1111111111111111".hexToByteArray(), plain.issueId)
        assertArrayEquals("2222222222222222".hexToByteArray(), plain.issueParameter)
    }

    @Test
    fun testSessionRejectsTamperedResponse() {
        val tampered = KNOWN_RESPONSE.hexToByteArray()
        tampered[0] = (tampered[0].toInt() xor 0x40).toByte()

        session(TRANSACTION_ID).use {
            assertThrows(IllegalArgumentException::class.java) {
                it.open(RESPONSE_CODE, tampered)
            }
        }
    }

    @Test
    fun testSessionRejectsWrongTransactionId() {
        session("A10203040506").use {
            assertThrows(IllegalArgumentException::class.java) {
                it.open(RESPONSE_CODE, KNOWN_RESPONSE.hexToByteArray())
            }
        }
    }

    private fun session(transactionId: String): DesSecureSession {
        val sessionKey = SecretBytes.fromByteArray("1011121314151617".hexToByteArray())
        return try {
            DesSecureSession(transactionId.hexToByteArray(), sessionKey, AndroidDesCryptography())
        } finally {
            sessionKey.close()
        }
    }

    private companion object {
        const val KNOWN_RESPONSE =
            "6AA2DC01123CE0BC098AFAB84443D67732D7A645FBFC0CCF23C630FBA0C7AC98"
        const val TRANSACTION_ID = "010203040506"
        val RESPONSE_CODE = Authentication2Des.Response.Encrypted.RESPONSE_CODE.toInt()
    }
}
