package com.kormax.felicatool.service.steps

import com.kormax.felicatool.authentication.AuthenticationGoal
import com.kormax.felicatool.authentication.AuthenticationOutcome
import com.kormax.felicatool.authentication.AuthenticationProviderSession
import com.kormax.felicatool.authentication.AuthenticationQuery
import com.kormax.felicatool.authentication.DesAuthenticationScope
import com.kormax.felicatool.authentication.OfferBoundAuthenticationTransport
import com.kormax.felicatool.authentication.SystemProfile
import com.kormax.felicatool.felica.Authentication1Des
import com.kormax.felicatool.felica.Authentication2Des
import com.kormax.felicatool.felica.EncryptedCommand
import com.kormax.felicatool.felica.EncryptedResponse
import com.kormax.felicatool.logging.ScanLog
import com.kormax.felicatool.nfc.NfcTargetUnavailableException
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.ScanStep
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.service.systemProfiles
import com.kormax.felicatool.ui.ScanStepIcon
import kotlinx.coroutines.CancellationException

private const val TAG = "FeliCaAuth"

internal object AuthenticateSystemsStep :
    ScanStep(
        id = "authenticate_systems",
        title = "Authenticate Systems",
        description = "Authenticate to each system and retrieve IDi/PMi",
        icon = ScanStepIcon.LOCK,
    ) {
    override fun commandSupport(context: CardScanContext): CommandSupport =
        if (context.commands.authentication1Des.supported == CommandSupport.SUPPORTED) {
            CommandSupport.SUPPORTED
        } else {
            CommandSupport.UNSUPPORTED
        }

    override suspend fun ScanSession.perform(): StepOutput {
        val profiles = scanContext.systemProfiles()
        val sessions =
            authenticationProviderSessions(profiles)
                ?: return StepOutput("Authentication providers are unavailable")
        return performWithSessions(sessions, profiles)
    }

    private suspend fun ScanSession.performWithSessions(
        sessions: List<AuthenticationProviderSession>,
        profiles: List<SystemProfile>,
    ): StepOutput {
        val outcomes = mutableListOf<String>()
        scanContext.systemScanContexts.toList().forEachIndexed { index, systemContext ->
            val systemCode = systemContext.systemCode
            val systemIdm = systemContext.idm
            val label = systemCode?.toHexString()?.uppercase() ?: "unknown"
            if (systemCode == null || systemIdm == null) {
                outcomes += "System $label: unavailable"
                return@forEachIndexed
            }

            val profile = profiles.firstOrNull {
                it.systemCode == label && it.idm == systemIdm.toHexString().uppercase()
            }
            if (profile == null) {
                outcomes += "System $label: no usable DES key versions"
                return@forEachIndexed
            }

            var finalOutcome = "no matching credentials"
            var stopSystem = false
            for (provider in sessions) {
                if (stopSystem) break
                val offers =
                    try {
                        provider.offers(
                            AuthenticationQuery(
                                profile = profile,
                                goal = AuthenticationGoal.GetSystemAuthenticationInfo,
                            ),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        ScanLog.e(
                            TAG,
                            "System $label: provider offer generation failed",
                            error,
                        )
                        finalOutcome = "authentication unavailable"
                        emptyList()
                    }
                for (offer in offers) {
                    if (stopSystem) break
                    val transport = SessionOfferBoundTransport(this, offer.scope)
                    val outcome =
                        try {
                            provider.authenticate(offer, transport)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: NfcTargetUnavailableException) {
                            throw error
                        } catch (error: Exception) {
                            ScanLog.e(
                                TAG,
                                "System $label: ${provider.descriptor.displayName} authentication failed",
                                error,
                            )
                            finalOutcome = "authentication unavailable"
                            transport.recoverMode0()
                            null
                        } ?: continue

                    when (outcome) {
                        is AuthenticationOutcome.Success -> {
                            try {
                                scanContext =
                                    scanContext.copy(
                                        systemScanContexts =
                                            scanContext.systemScanContexts.mapIndexed {
                                                systemIndex,
                                                current ->
                                                if (systemIndex != index) current
                                                else
                                                    current.copy(
                                                        idi = outcome.information.issueId,
                                                        pmi = outcome.information.issueParameter,
                                                    )
                                            },
                                    )
                                finalOutcome = "authenticated"
                            } finally {
                                outcome.channel.close()
                                transport.recoverMode0()
                            }
                            stopSystem = true
                        }
                        AuthenticationOutcome.CredentialMiss -> Unit
                        AuthenticationOutcome.NoResponse -> finalOutcome = "no response"
                        is AuthenticationOutcome.HardFailure -> {
                            ScanLog.w(
                                TAG,
                                "System $label: ${provider.descriptor.displayName}: ${outcome.reason}",
                            )
                            finalOutcome = "authentication response invalid"
                            transport.recoverMode0()
                            stopSystem = true
                        }
                    }
                }
            }
            outcomes += "System $label: $finalOutcome"
        }
        return StepOutput(outcomes.joinToString("\n"))
    }
}

internal class SessionOfferBoundTransport(
    private val session: ScanSession,
    override val scope: DesAuthenticationScope,
) : OfferBoundAuthenticationTransport {
    override val systemCode: String = scope.systemCode
    override val idm: String = scope.idm

    override suspend fun authentication1(
        challenge1A: ByteArray,
        attempts: Int,
    ): Authentication1Des.Response? =
        try {
            session.executeCommand(
                withSelectedSystemCode = systemCode.hexToByteArray(),
                attempts = attempts,
            ) {
                Authentication1Des.Command(
                    this@SessionOfferBoundTransport.idm.hexToByteArray(),
                    scope.areaCodes.map { it.hexToByteArray() }.toTypedArray(),
                    scope.nodeCodes.map { it.hexToByteArray() }.toTypedArray(),
                    challenge1A,
                )
            }
        } catch (_: TransceiveTimeoutException) {
            null
        }

    override suspend fun authentication2(
        challenge2B: ByteArray,
        attempts: Int,
    ): Authentication2Des.Response.Encrypted? =
        try {
            session.executeCommand(
                withSelectedSystemCode = systemCode.hexToByteArray(),
                attempts = attempts,
            ) {
                Authentication2Des.Command(
                    this@SessionOfferBoundTransport.idm.hexToByteArray(),
                    challenge2B,
                )
            }
        } catch (_: TransceiveTimeoutException) {
            null
        }

    override suspend fun <T : EncryptedResponse> transceiveSecure(
        command: EncryptedCommand<T>,
        attempts: Int,
    ): T? =
        try {
            session.executeCommand(
                withSelectedSystemCode = systemCode.hexToByteArray(),
                attempts = attempts,
            ) {
                command
            }
        } catch (_: TransceiveTimeoutException) {
            null
        }

    override suspend fun recoverMode0() {
        session.recoverMode0(systemCode.hexToByteArray(), idm.hexToByteArray())
    }
}
