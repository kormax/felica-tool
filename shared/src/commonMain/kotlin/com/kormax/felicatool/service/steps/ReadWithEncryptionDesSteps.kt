package com.kormax.felicatool.service.steps

import com.kormax.felicatool.authentication.AuthenticationGoal
import com.kormax.felicatool.authentication.AuthenticationOffer
import com.kormax.felicatool.authentication.AuthenticationOutcome
import com.kormax.felicatool.authentication.AuthenticationProviderSession
import com.kormax.felicatool.authentication.AuthenticationQuery
import com.kormax.felicatool.authentication.SecureChannel
import com.kormax.felicatool.authentication.SystemProfile
import com.kormax.felicatool.felica.BlockListElement
import com.kormax.felicatool.felica.ErrorLocationIndication
import com.kormax.felicatool.felica.ReadWithEncryptionDes
import com.kormax.felicatool.felica.Status
import com.kormax.felicatool.logging.ScanLog
import com.kormax.felicatool.nfc.NfcTargetUnavailableException
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.LiveAuthenticatedScope
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.ScanStep
import com.kormax.felicatool.service.SessionOfferBoundTransport
import com.kormax.felicatool.service.StepBehaviorUnexpected
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.service.StepPreconditionNotMet
import com.kormax.felicatool.service.StepSkipped
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.service.authenticationRequiredServices
import com.kormax.felicatool.service.formatStatus
import com.kormax.felicatool.service.profileIn
import com.kormax.felicatool.service.systemProfiles
import com.kormax.felicatool.ui.ScanStepIcon
import kotlinx.coroutines.CancellationException

private const val TAG = "FeliCaAuth"

internal object ReadWithEncryptionDesDetermineSupportedStep :
    ScanStep(
        id = "read_with_encryption_des_determine_supported",
        title = "Read With Encryption DES: Supported",
        description =
            "Probe if Read With Encryption DES is supported by sending a single-block read request after authenticating",
        icon = ScanStepIcon.SEARCH,
    ) {
    override fun commandSupport(context: CardScanContext): CommandSupport =
        if (context.commands.authentication1Des.supported == CommandSupport.SUPPORTED) {
            CommandSupport.SUPPORTED
        } else {
            CommandSupport.UNSUPPORTED
        }

    override fun withCommandSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(readWithEncryptionDes = readWithEncryptionDes.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val profiles = scanContext.systemProfiles()
        if (profiles.isEmpty()) throw StepSkipped("No key-protected services found")
        val sessions =
            authenticationProviderSessions(profiles)
                ?: throw StepSkipped("No authentication providers are available")

        var credentialsTried = 0
        for (systemContext in scanContext.systemScanContexts) {
            val profile = systemContext.profileIn(profiles) ?: continue
            val targetCodes = systemContext.readTestTargetCodes()
            if (targetCodes.isEmpty()) continue

            var serviceCode: String? = null
            var status: String? = null
            val result =
                withFirstAuthenticatedScope(sessions, profile, targetCodes) { channel, order ->
                    serviceCode = channel.scope.nodeCodes[order]
                    val response =
                        channel.transceive(
                            ReadWithEncryptionDes.Command.Plain(
                                listOf(BlockListElement(order, blockNumber = 0)),
                            ),
                        ) ?: return@withFirstAuthenticatedScope false
                    status = formatStatus(response)
                    true
                }
            when (result) {
                AuthenticatedScopeResult.NoOffers -> continue
                is AuthenticatedScopeResult.NotAuthenticated -> {
                    credentialsTried += result.credentialsTried
                    continue
                }
                AuthenticatedScopeResult.ReplyLost ->
                    throw TransceiveTimeoutException("No response to encrypted read")
                AuthenticatedScopeResult.Completed -> Unit
            }

            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            return StepOutput(
                buildString {
                    appendLine("Read With Encryption DES command is supported")
                    appendLine("System: ${profile.systemCode}; Service: $serviceCode; Block: 0000")
                    appendLine("($status)")
                }
                    .trim(),
            )
        }
        if (credentialsTried > 0) {
            throw StepPreconditionNotMet("No credentials accepted out of $credentialsTried")
        }
        throw StepSkipped("No credentials available")
    }
}

internal object ReadWithEncryptionDesDetermineErrorIndicationStep :
    ScanStep(
        id = "read_with_encryption_des_determine_error_indication",
        title = "Read With Encryption DES: Determine type of error indication",
        description = "How errors are indicated when reading key-protected blocks",
        icon = ScanStepIcon.SEARCH,
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

        val invalidBlockNumber = 127
        var credentialsTried = 0
        for (systemContext in scanContext.systemScanContexts) {
            val profile = systemContext.profileIn(profiles) ?: continue
            val targetCodes = systemContext.readTestTargetCodes()
            if (targetCodes.isEmpty()) continue

            var determined: ErrorLocationIndication? = null
            var status: String? = null
            val result =
                withFirstAuthenticatedScope(sessions, profile, targetCodes) { channel, order ->
                    val response =
                        channel.transceive(
                            ReadWithEncryptionDes.Command.Plain(
                                listOf(
                                    BlockListElement(order, blockNumber = 0),
                                    BlockListElement(order, blockNumber = 0),
                                    BlockListElement(
                                        order,
                                        blockNumber = invalidBlockNumber,
                                    ),
                                ),
                            ),
                        ) ?: return@withFirstAuthenticatedScope false
                    status = formatStatus(response)
                    if (response.status is Status.IllegalBlockNumber) {
                        determined =
                            when (response.statusFlag1.toInt() and 0xFF) {
                                0xFF -> ErrorLocationIndication.FLAG
                                0x04 -> ErrorLocationIndication.BITMASK
                                0x03 -> ErrorLocationIndication.INDEX
                                else -> null
                            }
                    }
                    true
                }
            when (result) {
                AuthenticatedScopeResult.NoOffers -> continue
                is AuthenticatedScopeResult.NotAuthenticated -> {
                    credentialsTried += result.credentialsTried
                    continue
                }
                AuthenticatedScopeResult.ReplyLost ->
                    throw TransceiveTimeoutException("No response to encrypted read")
                AuthenticatedScopeResult.Completed -> Unit
            }

            val indication = determined ?: ErrorLocationIndication.UNKNOWN
            scanContext = scanContext.withCommands {
                copy(
                    readWithEncryptionDes =
                        readWithEncryptionDes.copy(
                            supported = CommandSupport.SUPPORTED,
                            errorLocationIndication = indication,
                        ),
                )
            }
            if (determined == null) {
                throw StepBehaviorUnexpected(
                    "Error indication fallback to ${indication.name}: unexpected status ($status)",
                )
            }
            return StepOutput("Encrypted read error indication: ${indication.name} ($status)")
        }
        if (credentialsTried > 0) {
            throw StepPreconditionNotMet("No credentials accepted out of $credentialsTried")
        }
        throw StepSkipped("No credentials available")
    }
}

internal object ReadWithEncryptionDesDetermineMaxBlocksStep :
    ScanStep(
        id = "read_with_encryption_des_determine_max_blocks",
        title = "Read With Encryption DES: Determine Max Blocks",
        description = "How many key-protected blocks can be read in one request",
        icon = ScanStepIcon.SEARCH,
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

        var credentialsTried = 0
        for (systemContext in scanContext.systemScanContexts) {
            val profile = systemContext.profileIn(profiles) ?: continue
            val targetCodes = systemContext.readTestTargetCodes()
            if (targetCodes.isEmpty()) continue

            var maxBlocks = 0
            val result =
                withFirstAuthenticatedScope(sessions, profile, targetCodes) { channel, order ->
                    var replyLost = false
                    maxBlocks =
                        determineCommandLimit(
                            "read_with_encryption_des",
                            "max_blocks_per_request",
                            ReadWithEncryptionDes.Command.MAX_BLOCKS,
                        ) { count ->
                            if (replyLost) return@determineCommandLimit false
                            val response =
                                channel.transceive(
                                    ReadWithEncryptionDes.Command.Plain(
                                        List(count) { BlockListElement(order, blockNumber = 0) },
                                    ),
                                )
                            if (response == null) {
                                replyLost = true
                                return@determineCommandLimit false
                            }
                            if (response.isStatusSuccessful) {
                                return@determineCommandLimit response.blockData.size == count
                            }
                            when (response.status) {
                                is Status.IllegalNumberOfBlock,
                                is Status.IllegalBlockNumber -> false
                                else -> {
                                    scanContext = scanContext.withCommands {
                                        copy(
                                            readWithEncryptionDes =
                                                readWithEncryptionDes.copy(
                                                    supported = CommandSupport.SUPPORTED,
                                                    maxBlocksPerRequest = 1,
                                                ),
                                        )
                                    }
                                    throw StepBehaviorUnexpected(
                                        "Maximum blocks fallback to 1: unexpected status (${formatStatus(response)})",
                                    )
                                }
                            }
                        }
                    !replyLost
                }
            when (result) {
                AuthenticatedScopeResult.NoOffers -> continue
                is AuthenticatedScopeResult.NotAuthenticated -> {
                    credentialsTried += result.credentialsTried
                    continue
                }
                AuthenticatedScopeResult.ReplyLost ->
                    throw TransceiveTimeoutException("No response to encrypted read")
                AuthenticatedScopeResult.Completed -> Unit
            }

            if (maxBlocks == 0) {
                throw RuntimeException(
                    "Unable to determine maximum blocks per request - even 1 block failed",
                )
            }
            scanContext = scanContext.withCommands {
                copy(
                    readWithEncryptionDes =
                        readWithEncryptionDes.copy(
                            supported = CommandSupport.SUPPORTED,
                            maxBlocksPerRequest = maxBlocks,
                        ),
                )
            }
            return StepOutput("Encrypted read max blocks per request: $maxBlocks")
        }
        if (credentialsTried > 0) {
            throw StepPreconditionNotMet("No credentials accepted out of $credentialsTried")
        }
        throw StepSkipped("No credentials available")
    }
}

internal object ReadWithEncryptionDesDetermineExtendedBlockListElementSupportedStep :
    ScanStep(
        id = "read_with_encryption_des_determine_extended_block_list_element_supported",
        title = "Read With Encryption DES: Extended BLE Supported",
        description =
            "Check whether Read With Encryption DES accepts a three-byte block list element",
        icon = ScanStepIcon.SEARCH,
    ) {
    override fun commandSupport(context: CardScanContext): CommandSupport =
        if (
            context.commands.authentication1Des.supported == CommandSupport.SUPPORTED &&
                context.commands.readWithEncryptionDes.supported != CommandSupport.UNSUPPORTED
        ) {
            context.commands.readWithEncryptionDes.extendedBlockListElementSupported
        } else {
            CommandSupport.UNSUPPORTED
        }

    override fun withCommandSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(
            readWithEncryptionDes =
                readWithEncryptionDes.copy(extendedBlockListElementSupported = support),
        )
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val profiles = scanContext.systemProfiles()
        if (profiles.isEmpty()) throw StepSkipped("No key-protected services found")
        val sessions =
            authenticationProviderSessions(profiles)
                ?: throw StepSkipped("No authentication providers are available")

        var credentialsTried = 0
        for (systemContext in scanContext.systemScanContexts) {
            val profile = systemContext.profileIn(profiles) ?: continue
            val targetCodes = systemContext.readTestTargetCodes()
            if (targetCodes.isEmpty()) continue

            var serviceCode: String? = null
            var accepted = false
            var status: String? = null
            val result =
                withFirstAuthenticatedScope(sessions, profile, targetCodes) { channel, order ->
                    serviceCode = channel.scope.nodeCodes[order]
                    val response =
                        channel.transceive(
                            ReadWithEncryptionDes.Command.Plain(
                                listOf(BlockListElement(order, blockNumber = 0, extended = true)),
                            ),
                        ) ?: return@withFirstAuthenticatedScope false
                    status = formatStatus(response)
                    accepted =
                        when (response.status) {
                            is Status.Success,
                            is Status.IllegalBlockNumber,
                            is Status.AuthenticationRequired,
                            is Status.RandomChallengeWriteRequired -> true
                            else -> false
                        }
                    true
                }
            when (result) {
                AuthenticatedScopeResult.NoOffers -> continue
                is AuthenticatedScopeResult.NotAuthenticated -> {
                    credentialsTried += result.credentialsTried
                    continue
                }
                AuthenticatedScopeResult.ReplyLost ->
                    throw TransceiveTimeoutException("No response to encrypted read")
                AuthenticatedScopeResult.Completed -> Unit
            }

            if (!accepted) {
                throw RuntimeException("Extended block list element was not accepted: $status")
            }
            scanContext = withCommandSupport(scanContext, CommandSupport.SUPPORTED)
            return StepOutput(
                "System: ${profile.systemCode}; Service: $serviceCode; Block: 0000\n" +
                    "Extended block list element accepted\nStatus: $status",
            )
        }
        if (credentialsTried > 0) {
            throw StepPreconditionNotMet("No credentials accepted out of $credentialsTried")
        }
        throw StepSkipped("No credentials available")
    }
}

internal suspend fun ScanSession.withFirstAuthenticatedScope(
    sessions: List<AuthenticationProviderSession>,
    profile: SystemProfile,
    requestedNodes: List<String>,
    block: suspend (channel: SecureChannel, order: Int) -> Boolean,
): AuthenticatedScopeResult {
    repeat(2) { attempt ->
        val result = authenticatedScopeAttempt(sessions, profile, requestedNodes, block)
        if (result != AuthenticatedScopeResult.ReplyLost) return result
        ScanLog.w(TAG, "Secure reply lost; re-authenticating (attempt ${attempt + 1})")
        releaseAuthenticatedScope()
    }
    return AuthenticatedScopeResult.ReplyLost
}

internal sealed interface AuthenticatedScopeResult {
    data object Completed : AuthenticatedScopeResult

    data object ReplyLost : AuthenticatedScopeResult

    data class NotAuthenticated(val credentialsTried: Int) : AuthenticatedScopeResult

    data object NoOffers : AuthenticatedScopeResult
}

private suspend fun ScanSession.authenticatedScopeAttempt(
    sessions: List<AuthenticationProviderSession>,
    profile: SystemProfile,
    requestedNodes: List<String>,
    block: suspend (channel: SecureChannel, order: Int) -> Boolean,
): AuthenticatedScopeResult {
    liveAuthenticatedScope?.let { live ->
        val order = live.offer.firstCoveredOrder(requestedNodes)
        if (live.offer.scope.systemCode == profile.systemCode && order >= 0) {
            return live.channel.runBlock(order, block)
        }
        releaseAuthenticatedScope()
    }
    var attempted = 0
    for (provider in sessions) {
        val offers =
            try {
                provider.offers(
                    AuthenticationQuery(
                        profile = profile,
                        goal = AuthenticationGoal.ReadWithEncryption(requestedNodes.toSet()),
                    ),
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ScanLog.e(
                    TAG,
                    "${provider.descriptor.displayName}: offer generation failed",
                    error,
                )
                continue
            }
        for (offer in offers) {
            val order = offer.firstCoveredOrder(requestedNodes)
            if (order < 0) continue
            attempted++
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
                        "${provider.descriptor.displayName}: authentication failed",
                        error,
                    )
                    transport.recoverMode0()
                    continue
                }
            when (outcome) {
                is AuthenticationOutcome.Success -> {
                    liveAuthenticatedScope =
                        LiveAuthenticatedScope(offer, outcome.channel, transport)
                    return outcome.channel.runBlock(order, block)
                }
                is AuthenticationOutcome.HardFailure -> transport.recoverMode0()
                else -> Unit
            }
        }
    }
    return if (attempted > 0) {
        AuthenticatedScopeResult.NotAuthenticated(attempted)
    } else {
        AuthenticatedScopeResult.NoOffers
    }
}

private suspend fun SecureChannel.runBlock(
    order: Int,
    block: suspend (channel: SecureChannel, order: Int) -> Boolean,
): AuthenticatedScopeResult =
    if (block(this, order)) AuthenticatedScopeResult.Completed
    else AuthenticatedScopeResult.ReplyLost

private fun AuthenticationOffer.firstCoveredOrder(requestedNodes: List<String>): Int =
    requestedNodes.firstOrNull { it in coveredNodes }?.let(scope.nodeCodes::indexOf) ?: -1

private fun SystemScanContext.readTestTargetCodes(): List<String> =
    authenticationRequiredServices()
        .entries
        .sortedByDescending { it.value.readTestScore() }
        .map { it.key }
