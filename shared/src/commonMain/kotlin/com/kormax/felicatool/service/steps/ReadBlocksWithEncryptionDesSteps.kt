package com.kormax.felicatool.service.steps

import com.kormax.felicatool.authentication.AuthenticationGoal
import com.kormax.felicatool.authentication.AuthenticationOffer
import com.kormax.felicatool.authentication.AuthenticationOutcome
import com.kormax.felicatool.authentication.AuthenticationQuery
import com.kormax.felicatool.authentication.SecureChannel
import com.kormax.felicatool.felica.ReadWithEncryptionDes
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.logging.ScanLog
import com.kormax.felicatool.nfc.NfcTargetUnavailableException
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.EncryptedBlockReader
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.ScanStep
import com.kormax.felicatool.service.SessionOfferBoundTransport
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.service.StepPreconditionNotMet
import com.kormax.felicatool.service.StepSkipped
import com.kormax.felicatool.service.authenticationRequiredServices
import com.kormax.felicatool.service.blockReadHints
import com.kormax.felicatool.service.loggedChannel
import com.kormax.felicatool.service.profileIn
import com.kormax.felicatool.service.systemProfiles
import com.kormax.felicatool.ui.ScanStepIcon
import kotlinx.coroutines.CancellationException

private const val TAG = "FeliCaAuth"

internal object ReadBlocksWithEncryptionDesStep :
    ScanStep(
        id = "read_blocks_with_encryption_des",
        title = "Read Blocks With Encryption DES",
        description = "Reading block data from services that require authentication",
        icon = ScanStepIcon.LOCK,
    ) {

    override fun commandSupport(context: CardScanContext): CommandSupport =
        if (
            context.commands.authentication1Des.supported == CommandSupport.SUPPORTED &&
                context.commands.readWithEncryptionDes.supported != CommandSupport.UNSUPPORTED
        ) {
            CommandSupport.SUPPORTED
        } else {
            CommandSupport.UNSUPPORTED
        }

    override suspend fun ScanSession.perform(): StepOutput {
        val profiles = scanContext.systemProfiles()
        if (profiles.isEmpty()) throw StepSkipped("No key-protected services found")
        val sessions =
            authenticationProviderSessions(profiles)
                ?: throw StepSkipped("No authentication providers are available")

        val readCapability = scanContext.commands.readWithEncryptionDes
        val errorIndication = readCapability.errorLocationIndication
        val maxBlocksPerRead =
            readCapability.maxBlocksPerRequest ?: ReadWithEncryptionDes.Command.MAX_BLOCKS

        val outcomes = mutableListOf<String>()
        var totalTargets = 0
        var totalReached = 0
        var totalReadServices = 0
        var totalBlocks = 0
        var credentialsOffered = 0

        scanContext.systemScanContexts.toList().forEachIndexed { index, systemContext ->
            val profile = systemContext.profileIn(profiles) ?: return@forEachIndexed
            val label = profile.systemCode
            val targets = systemContext.authenticationRequiredServices()
            if (targets.isEmpty()) {
                outcomes += "System $label: no key-protected services to read"
                return@forEachIndexed
            }

            val remaining = targets.keys.toMutableSet()
            val collected = mutableMapOf<Service, Map<Int, ByteArray>>()
            val reached = mutableSetOf<String>()
            var blocksRead = 0

            suspend fun readCovered(
                channel: SecureChannel,
                offer: AuthenticationOffer,
                coverable: Set<String>,
            ) {
                val covered = coverable.sortedBy { offer.scope.nodeCodes.indexOf(it) }
                reached += covered
                val coveredServices = covered.map { targets.getValue(it) }
                val readBlocks =
                    EncryptedBlockReader(
                            channel = channel,
                            maxBlocksPerRequest = maxBlocksPerRead,
                            errorLocationIndication = errorIndication,
                        )
                        .read(coveredServices, blockReadHints(systemContext, coveredServices))
                remaining.removeAll(covered.toSet())
                readBlocks.forEach { (service, data) ->
                    if (data.isNotEmpty()) {
                        collected[service] = data
                        blocksRead += data.size
                    }
                }
            }

            liveAuthenticatedScope
                ?.takeIf { it.offer.scope.systemCode == profile.systemCode }
                ?.let { live ->
                    val coverable = live.offer.coveredNodes.intersect(remaining)
                    if (coverable.isNotEmpty()) readCovered(live.channel, live.offer, coverable)
                }
            releaseAuthenticatedScope()

            for (provider in sessions) {
                if (remaining.isEmpty()) break
                val offers =
                    try {
                        provider.offers(
                            AuthenticationQuery(
                                profile = profile,
                                goal = AuthenticationGoal.ReadWithEncryption(remaining.toSet()),
                            ),
                        )
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        ScanLog.e(
                            TAG,
                            "System $label: ${provider.descriptor.displayName} offer generation failed",
                            error,
                        )
                        continue
                    }

                for (offer in offers) {
                    if (remaining.isEmpty()) break
                    val coverable = offer.coveredNodes.intersect(remaining)
                    if (coverable.isEmpty()) continue
                    credentialsOffered++

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
                            transport.recoverMode0()
                            continue
                        }

                    when (outcome) {
                        is AuthenticationOutcome.Success -> {
                            try {
                                readCovered(
                                    loggedChannel(outcome),
                                    offer,
                                    coverable,
                                )
                            } finally {
                                outcome.channel.close()
                                transport.recoverMode0()
                            }
                        }
                        AuthenticationOutcome.CredentialMiss -> Unit
                        AuthenticationOutcome.NoResponse -> Unit
                        is AuthenticationOutcome.HardFailure -> {
                            ScanLog.w(
                                TAG,
                                "System $label: ${provider.descriptor.displayName}: ${outcome.reason}",
                            )
                            transport.recoverMode0()
                        }
                    }
                }
            }

            if (collected.isNotEmpty()) {
                scanContext =
                    scanContext.copy(
                        systemScanContexts =
                            scanContext.systemScanContexts.mapIndexed { systemIndex, current ->
                                if (systemIndex != index) current
                                else {
                                    val merged = current.serviceBlockData.toMutableMap()
                                    collected.forEach { (service, data) ->
                                        merged[service] = merged[service].orEmpty() + data
                                    }
                                    current.copy(serviceBlockData = merged)
                                }
                            },
                    )
            }
            totalTargets += targets.size
            totalReached += reached.size
            totalReadServices += collected.size
            totalBlocks += blocksRead
            outcomes +=
                "System $label: ${targets.size} key-protected service(s), " +
                    "${reached.size} reachable with available keys, " +
                    "${collected.size} read ($blocksRead block(s))"
        }

        if (outcomes.isEmpty()) throw StepSkipped("No key-protected services found")
        if (totalTargets > 0 && totalReached == 0) {
            if (credentialsOffered == 0) throw StepSkipped("No credentials available")
            throw StepPreconditionNotMet(
                "No credentials accepted out of $credentialsOffered",
            )
        }
        val summary =
            "$totalReadServices read / $totalReached reachable / $totalTargets key-protected " +
                "service(s), $totalBlocks block(s)"
        return StepOutput(
            result = (listOf(summary) + outcomes).joinToString("\n"),
            collapsedResult = summary,
        )
    }
}
