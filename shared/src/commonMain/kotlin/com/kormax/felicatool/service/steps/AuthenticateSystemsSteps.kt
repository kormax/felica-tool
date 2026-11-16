package com.kormax.felicatool.service.steps

import com.kormax.felicatool.authentication.AuthenticationGoal
import com.kormax.felicatool.authentication.AuthenticationOutcome
import com.kormax.felicatool.authentication.AuthenticationProviderSession
import com.kormax.felicatool.authentication.AuthenticationQuery
import com.kormax.felicatool.authentication.SystemProfile
import com.kormax.felicatool.logging.ScanLog
import com.kormax.felicatool.nfc.NfcTargetUnavailableException
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.LiveAuthenticatedScope
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.ScanStep
import com.kormax.felicatool.service.SessionOfferBoundTransport
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.service.StepPreconditionNotMet
import com.kormax.felicatool.service.StepSkipped
import com.kormax.felicatool.service.profileIn
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
                ?: throw StepSkipped("No authentication providers are available")
        return performWithSessions(sessions, profiles)
    }

    private suspend fun ScanSession.performWithSessions(
        sessions: List<AuthenticationProviderSession>,
        profiles: List<SystemProfile>,
    ): StepOutput {
        val outcomes = mutableListOf<String>()
        var profiled = 0
        var concluded = 0
        var authenticated = 0
        var totalCredentialsTried = 0
        scanContext.systemScanContexts.toList().forEachIndexed { index, systemContext ->
            val systemCode = systemContext.systemCode
            val systemIdm = systemContext.idm
            val label = systemCode?.toHexString()?.uppercase() ?: "unknown"
            if (systemCode == null || systemIdm == null) {
                outcomes += "System $label: unavailable"
                return@forEachIndexed
            }

            val profile = systemContext.profileIn(profiles)
            if (profile == null) {
                outcomes += "System $label: no usable DES key versions"
                return@forEachIndexed
            }

            profiled++
            releaseAuthenticatedScope()
            var finalOutcome: String? = null
            var credentialsTried = 0
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
                    credentialsTried++
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
                            liveAuthenticatedScope =
                                LiveAuthenticatedScope(
                                    offer,
                                    outcome.channel,
                                    transport,
                                )
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
                            authenticated++
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
            totalCredentialsTried += credentialsTried
            if (finalOutcome != null) concluded++
            outcomes +=
                "System $label: " +
                    (finalOutcome
                        ?: when (credentialsTried) {
                            0 -> "no credentials available"
                            else -> "no credentials accepted out of $credentialsTried"
                        })
        }
        if (authenticated == 0) {
            when {
                profiled == 0 -> throw StepSkipped(outcomes.joinToString("\n"))
                concluded > 0 -> error(outcomes.joinToString("\n"))
                totalCredentialsTried == 0 -> throw StepSkipped("No credentials available")
                else ->
                    throw StepPreconditionNotMet(
                        "No credentials accepted out of $totalCredentialsTried",
                    )
            }
        }
        return StepOutput(outcomes.joinToString("\n"))
    }
}
