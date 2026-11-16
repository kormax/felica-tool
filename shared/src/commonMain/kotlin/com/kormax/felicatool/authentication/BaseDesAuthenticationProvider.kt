package com.kormax.felicatool.authentication

import com.kormax.felicatool.felica.Authentication2Des
import com.kormax.felicatool.felica.SecureCommand
import com.kormax.felicatool.felica.SecureResponse
import com.kormax.felicatool.logging.ScanLog
import com.kormax.felicatool.nfc.NfcTargetUnavailableException
import com.kormax.felicatool.security.DES_BLOCK_SIZE
import com.kormax.felicatool.security.DesCryptography
import com.kormax.felicatool.security.DesSecureSession
import com.kormax.felicatool.security.SecretBytes
import com.kormax.felicatool.security.SecureRandomSource
import kotlinx.coroutines.CancellationException

private const val TAG = "FeliCaAuth"

internal abstract class BaseDesAuthenticationProvider(
    private val providerDescriptor: AuthenticationProviderDescriptor,
    private val cryptography: DesCryptography,
    private val randomSource: SecureRandomSource,
) {
    protected suspend fun authenticate(
        offer: AuthenticationOffer,
        transport: OfferBoundAuthenticationTransport,
        resolveContexts: suspend (AuthenticationOffer) -> List<DesAuthenticationContext>,
    ): AuthenticationOutcome {
        require(offer.providerId == providerDescriptor.id) {
            "Offer belongs to a different provider"
        }
        require(transport.systemCode == offer.scope.systemCode) {
            "Offer system does not match transport"
        }
        require(transport.idm == offer.scope.idm) { "Offer IDm does not match transport" }
        require(transport.scope == offer.scope) { "Offer scope does not match transport" }

        ScanLog.i(
            TAG,
            "${providerDescriptor.displayName}: resolving credentials for system ${offer.scope.systemCode} " +
                "(${offer.transmittedCodeCount} transmitted codes)",
        )
        val contexts = resolveContexts(offer)
        ScanLog.i(
            TAG,
            "${providerDescriptor.displayName}: resolved ${contexts.size} credential alternative(s)",
        )
        return try {
            authenticateWithContexts(offer, contexts, transport)
        } finally {
            contexts.forEach(DesAuthenticationContext::close)
        }
    }

    private suspend fun authenticateWithContexts(
        offer: AuthenticationOffer,
        contexts: List<DesAuthenticationContext>,
        transport: OfferBoundAuthenticationTransport,
    ): AuthenticationOutcome {
        for ((index, context) in contexts.withIndex()) {
            ScanLog.i(
                TAG,
                "${providerDescriptor.displayName}: Authentication1 candidate ${index + 1}/${contexts.size} " +
                    "for system ${offer.scope.systemCode}",
            )
            val random1 = randomSource.nextSecretBytes(DES_BLOCK_SIZE)
            try {
                val challenge1A = context.encryptChallenge1A(random1)
                val authentication1 = transport.authentication1(challenge1A)
                if (authentication1 == null) {
                    ScanLog.w(
                        TAG,
                        "${providerDescriptor.displayName}: Authentication1 returned no response",
                    )
                    transport.recoverMode0()
                    return AuthenticationOutcome.NoResponse
                }
                if (!context.verifyChallenge1B(random1, authentication1.challenge1B)) {
                    ScanLog.i(
                        TAG,
                        "${providerDescriptor.displayName}: Challenge1B mismatch; trying next credential",
                    )
                    transport.recoverMode0()
                    continue
                }
                ScanLog.i(
                    TAG,
                    "${providerDescriptor.displayName}: Challenge1B verified; sending Authentication2",
                )

                val random2 = context.decryptChallenge2A(authentication1.challenge2A)
                try {
                    val challenge2B = context.encryptChallenge2B(random2)
                    val authentication2 = transport.authentication2(challenge2B, attempts = 3)
                    if (authentication2 == null) {
                        ScanLog.w(
                            TAG,
                            "${providerDescriptor.displayName}: Authentication2 returned no response",
                        )
                        transport.recoverMode0()
                        return AuthenticationOutcome.NoResponse
                    }

                    val transactionId = random1.transactionId()
                    val session =
                        try {
                            DesSecureSession(transactionId, random2, cryptography)
                        } finally {
                            transactionId.fill(0)
                        }
                    try {
                        val plain =
                            try {
                                session.openAuthentication2(authentication2)
                            } catch (firstFailure: IllegalArgumentException) {
                                ScanLog.w(
                                    TAG,
                                    "${providerDescriptor.displayName}: Authentication2 validation failed; retrying once",
                                    firstFailure,
                                )
                                val retry = transport.authentication2(challenge2B, attempts = 1)
                                if (retry == null) {
                                    session.close()
                                    return AuthenticationOutcome.HardFailure(
                                        "Authentication2 validation retry returned no response",
                                    )
                                }
                                try {
                                    session.openAuthentication2(retry)
                                } catch (secondFailure: IllegalArgumentException) {
                                    session.close()
                                    return AuthenticationOutcome.HardFailure(
                                        secondFailure.message
                                            ?: "Authentication2 validation failed",
                                    )
                                }
                            }
                        ScanLog.i(
                            TAG,
                            "${providerDescriptor.displayName}: Authentication2 verified successfully",
                        )
                        return AuthenticationOutcome.Success(
                            information =
                                SystemAuthenticationInfo(plain.issueId, plain.issueParameter),
                            channel = LocalDesSecureChannel(offer.scope, session, transport),
                        )
                    } catch (error: Throwable) {
                        session.close()
                        throw error
                    }
                } finally {
                    random2.close()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: NfcTargetUnavailableException) {
                throw error
            } catch (error: Exception) {
                ScanLog.e(
                    TAG,
                    "${providerDescriptor.displayName}: candidate ${index + 1}/${contexts.size} failed",
                    error,
                )
                transport.recoverMode0()
            } finally {
                random1.close()
            }
        }
        ScanLog.i(
            TAG,
            "${providerDescriptor.displayName}: all credential alternatives were rejected",
        )
        return AuthenticationOutcome.CredentialMiss
    }

    private fun DesSecureSession.openAuthentication2(
        response: Authentication2Des.Response.Encrypted,
    ): Authentication2Des.Response.Plain {
        val payload =
            open(
                Authentication2Des.Response.Encrypted.RESPONSE_CODE.toInt(),
                response.encryptedData,
            )
        return try {
            Authentication2Des.Response.Plain.fromByteArray(payload)
        } finally {
            payload.fill(0)
        }
    }

    private fun SecretBytes.transactionId(): ByteArray {
        val bytes = copyToByteArray()
        return try {
            bytes.copyOfRange(2, 8)
        } finally {
            bytes.fill(0)
        }
    }
}

internal interface DesAuthenticationContext : AutoCloseable {
    suspend fun encryptChallenge1A(random1: SecretBytes): ByteArray

    suspend fun verifyChallenge1B(random1: SecretBytes, challenge1B: ByteArray): Boolean

    suspend fun decryptChallenge2A(challenge2A: ByteArray): SecretBytes

    suspend fun encryptChallenge2B(random2: SecretBytes): ByteArray
}

private class LocalDesSecureChannel(
    override val scope: DesAuthenticationScope,
    private val session: DesSecureSession,
    private val transport: OfferBoundAuthenticationTransport,
) : SecureChannel {
    private var interrupted = false

    override suspend fun <T : SecureResponse> transceive(command: SecureCommand<T>): T? {
        check(!interrupted) { "Secure channel was interrupted by a lost or invalid reply" }
        interrupted = true
        val encrypted = command.encrypted(session.seal(command.code, command.toByteArray()))
        val response = transport.transceiveSecure(encrypted) ?: return null
        return command
            .responseFromByteArray(session.open(command.code + 1, response.encryptedData))
            .also { interrupted = false }
    }

    override suspend fun close() = session.close()
}
