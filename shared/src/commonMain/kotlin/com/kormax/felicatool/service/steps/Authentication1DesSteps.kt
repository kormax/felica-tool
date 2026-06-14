package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon
import kotlin.time.Duration.Companion.milliseconds

private const val AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS = 3

private data class Authentication1DesTestTarget(
    val systemContext: SystemScanContext,
    val rootArea: Area,
)

private fun CardScanContext.findBestAuthentication1DesTarget(): Authentication1DesTestTarget? {
    var bestTarget: Authentication1DesTestTarget? = null
    var bestNodeCount = -1

    for (systemContext in systemScanContexts) {
        val rootArea =
            systemContext.nodes.filterIsInstance<Area>().firstOrNull { it.isRoot } ?: Area.ROOT
        val hasValidDesKeyOnRootArea =
            systemContext.nodeDesKeyVersions.containsKey(rootArea) ||
                (!systemContext.nodeAesKeyVersions.containsKey(rootArea) &&
                    systemContext.nodeKeyVersions.containsKey(rootArea))
        if (!hasValidDesKeyOnRootArea) {
            continue
        }

        val nodeCount = systemContext.nodes.size
        if (nodeCount > bestNodeCount) {
            bestNodeCount = nodeCount
            bestTarget =
                Authentication1DesTestTarget(systemContext = systemContext, rootArea = rootArea)
        }
    }

    return bestTarget
}

internal object Authentication1DesDetermineSupportedStep :
    CommandSupportScanStep(
        id = "authentication1_des_determine_supported",
        title = "Authenticate1 DES",
        description = "Attempt DES authentication with discovered nodes",
        icon = ScanStepIcon.LOCK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.authentication1Des.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(authentication1Des = authentication1Des.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findBestAuthentication1DesTarget()
        if (testTarget == null) {
            throw StepSkipped(
                "No suitable system found for DES authentication (root area with valid DES key is required)."
            )
        }
        val systemCodeHex = testTarget.systemContext.systemCode?.toHexString() ?: "unknown"
        ScanLog.d(
            "CardScanService",
            "Selected system $systemCodeHex for DES authentication using root area ${testTarget.rootArea.code.toHexString()} in area and node lists (node count: ${testTarget.systemContext.nodes.size})",
        )

        // Generate a random challenge1A (8 bytes)
        val challenge1A = ByteArray(8) { 0x00.toByte() }

        val areasToAuth = listOf(testTarget.rootArea)
        // Area0 may appear in both lists: this is allowed because key updates can target areas.
        val nodesToAuth = listOf<Node>(testTarget.rootArea)

        val authenticateResponse =
            executeCommand(
                withSelectedSystemCode = testTarget.systemContext.systemCode,
                withResetToMode0 = true,
                attempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) {
                Authentication1DesCommand(
                    idm = idm,
                    areaNodes = areasToAuth,
                    nodes = nodesToAuth,
                    challenge1A = challenge1A,
                )
            }

        return StepOutput(
            buildString {
                    appendLine("DES Authentication Results:")
                    appendLine("Selected system: $systemCodeHex")
                    appendLine("Using root area in both area and node lists for support check")
                    appendLine("Challenge1A (sent): ${challenge1A.toHexString()}")
                    appendLine(
                        "Challenge1B (received): ${authenticateResponse.challenge1B.toHexString()}"
                    )
                    appendLine(
                        "Challenge2A (received): ${authenticateResponse.challenge2A.toHexString()}"
                    )
                    appendLine()

                    if (areasToAuth.isNotEmpty()) {
                        appendLine("Areas authenticated:")
                        areasToAuth.forEachIndexed { index, area ->
                            val keyType =
                                when {
                                    testTarget.systemContext.nodeDesKeyVersions.containsKey(area) ->
                                        "DES key"
                                    testTarget.systemContext.nodeKeyVersions.containsKey(area) ->
                                        "Legacy (DES) key"
                                    else -> "Unknown"
                                }
                            appendLine(
                                "  ${index + 1}. Area ${area.number}-${area.endNumber} (${area.code.toHexString()}) - $keyType"
                            )
                        }
                        appendLine()
                    }

                    if (nodesToAuth.isNotEmpty()) {
                        appendLine("Nodes authenticated:")
                        nodesToAuth.forEachIndexed { index, node ->
                            val keyType =
                                when {
                                    testTarget.systemContext.nodeDesKeyVersions.containsKey(node) ->
                                        "DES key"
                                    testTarget.systemContext.nodeKeyVersions.containsKey(node) ->
                                        "Legacy (DES) key"
                                    else -> "Unknown"
                                }
                            appendLine("  ${index + 1}. ${describeNode(node)} - $keyType")
                        }
                        appendLine()
                    }
                }
                .trim()
        )
    }
}

internal object Authentication1DesDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<Authentication1DesResponse>(
        id = "authentication1_des_determine_trailing_data_supported",
        title = "Authenticate1 DES - Trailing Data Supported",
        description = "Check whether Authenticate1 DES accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Authenticate1 DES",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.authentication1Des.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(authentication1Des = authentication1Des.copy(trailingDataSupported = support))
    }

    override fun ScanSession.selectedSystemCode(): ByteArray? =
        scanContext.findBestAuthentication1DesTarget()?.systemContext?.systemCode
            ?: throw StepSkipped(
                "No suitable system found for DES authentication (root area with valid DES key is required)."
            )

    override fun ScanSession.resetToMode0AfterCommand(): Boolean = true

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<Authentication1DesResponse> {
        val testTarget =
            scanContext.findBestAuthentication1DesTarget()
                ?: throw StepSkipped(
                    "No suitable system found for DES authentication (root area with valid DES key is required)."
                )
        val challenge1A = ByteArray(8) { 0x00.toByte() }
        return Authentication1DesCommand(
            idm = scope.idm,
            areaNodes = listOf(testTarget.rootArea),
            nodes = listOf<Node>(testTarget.rootArea),
            challenge1A = challenge1A,
            trailingData = trailingData,
        )
    }

    override fun responseLines(response: Authentication1DesResponse): List<String> =
        listOf(
            "Challenge1B: ${response.challenge1B.toHexString()}",
            "Challenge2A: ${response.challenge2A.toHexString()}",
        )
}

internal object Authentication1DesNodeListHierarchyValidationStep :
    ScanStep(
        id = "authentication1_des_node_list_hierarchy_validation",
        title = "Authenticate1 DES: Node List Hierarchy Validation",
        description =
            "Check Authenticate1 DES validation behavior for nodes that aren't immediate children of specified areas",
        icon = ScanStepIcon.LOCK,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.commands.authentication1Des.supported != CommandSupport.SUPPORTED) {
            throw StepSkipped(
                "Authenticate1 DES support is not confirmed; cannot check node-list hierarchy validation"
            )
        }

        val preferredTarget = scanContext.findBestAuthentication1DesTarget()
        if (preferredTarget == null) {
            throw StepSkipped(
                "No suitable system found for Authenticate1 DES node-list hierarchy validation (root area with valid DES key is required)."
            )
        }

        val modeBeforeCheck = currentMode
        if (modeBeforeCheck != Mode.Mode0) {
            throw StepPreconditionNotMet(
                "Authenticate1 DES node-list hierarchy validation requires Mode 0 (current: $modeBeforeCheck)."
            )
        }

        val nonRootAreas =
            preferredTarget.systemContext.nodes.filterIsInstance<Area>().filter { area ->
                area != preferredTarget.rootArea &&
                    !area.isRoot &&
                    area.belongsTo(preferredTarget.rootArea)
            }
        val serviceUnderNonRootArea =
            preferredTarget.systemContext.nodes
                .filterIsInstance<Service>()
                .filter { service -> nonRootAreas.any { area -> service.belongsTo(area) } }
                .sortedBy { it.number }
                .firstOrNull()
        val nestedAreaUnderNonRootArea =
            if (serviceUnderNonRootArea == null) {
                nonRootAreas
                    .filter { candidate ->
                        nonRootAreas.any { parent ->
                            parent != candidate && candidate.belongsTo(parent)
                        }
                    }
                    .sortedBy { it.number }
                    .firstOrNull()
            } else {
                null
            }
        val nonImmediateNode = serviceUnderNonRootArea ?: nestedAreaUnderNonRootArea
        if (nonImmediateNode == null) {
            throw StepSkipped(
                "No node found under an area under root area; cannot check Authenticate1 DES node-list hierarchy validation."
            )
        }
        val challenge1A = ByteArray(8) { 0x00.toByte() }
        val areasToAuth = listOf(preferredTarget.rootArea)
        // Area0 may appear in both lists: this is allowed because key updates can target areas.
        val nodesToAuth = listOf<Node>(preferredTarget.rootArea, nonImmediateNode)

        val response =
            try {
                executeCommand(
                    withSelectedSystemCode = preferredTarget.systemContext.systemCode,
                    withResetToMode0 = true,
                    attempts = AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS,
                    retryDelay = 50.milliseconds,
                ) {
                    Authentication1DesCommand(
                        idm = idm,
                        areaNodes = areasToAuth,
                        nodes = nodesToAuth,
                        challenge1A = challenge1A,
                    )
                }
            } catch (e: TransceiveTimeoutException) {
                null
            }

        val validationBehavior =
            if (response != null) {
                Authentication1DesNodeListHierarchyValidation.LENIENT
            } else {
                Authentication1DesNodeListHierarchyValidation.STRICT
            }
        scanContext = scanContext.withCommands {
            copy(
                authentication1Des =
                    authentication1Des.copy(nodeListHierarchyValidation = validationBehavior)
            )
        }

        return StepOutput(
            buildString {
                    appendLine("Authenticate1 DES node-list validation check:")
                    appendLine(
                        "System: ${preferredTarget.systemContext.systemCode?.toHexString()?.uppercase() ?: "unknown"}"
                    )
                    appendLine("Mode before check: $modeBeforeCheck")
                    appendLine("Area list:")
                    appendLine("  1. ${describeNode(preferredTarget.rootArea)}")
                    appendLine("Node list:")
                    appendLine("  1. ${describeNode(preferredTarget.rootArea)}")
                    appendLine("  2. ${describeNode(nonImmediateNode)}")
                    appendLine("Challenge1A: ${challenge1A.toHexString().uppercase()}")
                    if (response != null) {
                        appendLine("Challenge1B: ${response.challenge1B.toHexString().uppercase()}")
                        appendLine("Challenge2A: ${response.challenge2A.toHexString().uppercase()}")
                    } else {
                        appendLine(
                            "No response after $AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS attempts"
                        )
                    }
                    appendLine("Validation behavior: $validationBehavior")
                }
                .trim()
        )
    }
}
