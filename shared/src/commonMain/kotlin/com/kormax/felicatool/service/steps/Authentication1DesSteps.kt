package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

private const val AUTHENTICATION1_DES_BEHAVIOR_ATTEMPTS = 3

private data class Authentication1DesTestTarget(
    val systemContext: SystemScanContext,
    val rootArea: Area,
)

private data class Authentication1DesServiceTestTarget(
    val systemContext: SystemScanContext,
    val rootArea: Area,
    val service: Service,
)

internal data class Authentication1DesCodeEntry(
    val label: String,
    val code: ByteArray,
)

internal data class Authentication1DesBehaviorCommandTarget(
    val systemContext: SystemScanContext,
    val areaEntries: List<Authentication1DesCodeEntry>,
    val nodeEntries: List<Authentication1DesCodeEntry>,
)

private fun SystemScanContext.hasAuthentication1DesKey(node: Node): Boolean =
    nodeDesKeyVersions.containsKey(node) ||
        (!nodeAesKeyVersions.containsKey(node) && nodeKeyVersions.containsKey(node))

private fun CardScanContext.findBestAuthentication1DesTarget(): Authentication1DesTestTarget? {
    var bestTarget: Authentication1DesTestTarget? = null
    var bestNodeCount = -1

    for (systemContext in systemScanContexts) {
        val rootArea =
            systemContext.nodes.filterIsInstance<Area>().firstOrNull { it.isRoot } ?: Area.ROOT
        if (!systemContext.hasAuthentication1DesKey(rootArea)) {
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

private fun CardScanContext.findBestAuthentication1DesServiceTarget():
    Authentication1DesServiceTestTarget? {
    val candidates = mutableListOf<Authentication1DesServiceTestTarget>()

    systemScanContexts.forEach { systemContext ->
        val rootArea =
            systemContext.nodes.filterIsInstance<Area>().firstOrNull { it.isRoot } ?: Area.ROOT
        if (!systemContext.hasAuthentication1DesKey(rootArea)) {
            return@forEach
        }

        systemContext.nodes.filterIsInstance<Service>().forEach { service ->
            candidates +=
                Authentication1DesServiceTestTarget(
                    systemContext = systemContext,
                    rootArea = rootArea,
                    service = service,
                )
        }
    }

    return candidates.minWithOrNull(
        compareBy<Authentication1DesServiceTestTarget>(
            { if (it.systemContext.hasAuthentication1DesKey(it.service)) 0 else 1 },
            { it.service.number },
        )
    )
}

private fun ScanSession.requireAuthentication1DesSupported(featureName: String) {
    if (scanContext.commands.authentication1Des.supported != CommandSupport.SUPPORTED) {
        throw StepSkipped("Authenticate1 DES support is not confirmed; cannot check $featureName")
    }
}

private fun ScanSession.requireMode0ForAuthentication1DesBehavior(featureName: String): Mode {
    val modeBeforeCheck = currentMode
    if (modeBeforeCheck != Mode.Mode0) {
        throw StepPreconditionNotMet(
            "Authenticate1 DES $featureName requires Mode 0 (current: $modeBeforeCheck)."
        )
    }
    return modeBeforeCheck
}

private suspend fun ScanSession.executeAuthentication1DesBehaviorCommand(
    systemContext: SystemScanContext,
    areaCodes: Array<ByteArray>,
    nodeCodes: Array<ByteArray>,
    challenge1A: ByteArray,
): Authentication1DesResponse? =
    try {
        executeCommand(
            withSelectedSystemCode = systemContext.systemCode,
            withResetToMode0 = true,
            attempts = AUTHENTICATION1_DES_BEHAVIOR_ATTEMPTS,
        ) {
            Authentication1DesCommand(
                idm = idm,
                areaCodes = areaCodes.map { it.copyOf() }.toTypedArray(),
                nodeCodes = nodeCodes.map { it.copyOf() }.toTypedArray(),
                challenge1A = challenge1A,
            )
        }
    } catch (e: TransceiveTimeoutException) {
        null
    }

private fun authentication1DesBehaviorSupport(
    response: Authentication1DesResponse?
): CommandSupport =
    if (response != null) {
        CommandSupport.SUPPORTED
    } else {
        CommandSupport.UNSUPPORTED
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

internal object Authentication1DesIncompleteAreaPathForNodeSupportedStep :
    ScanStep(
        id = "authentication1_des_determine_incomplete_area_path_for_node_supported",
        title = "Authenticate1 DES: Incomplete Area Path For Node Supported",
        description =
            "Check whether Authenticate1 DES accepts nodes whose full area authentication path is not provided",
        icon = ScanStepIcon.LOCK,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        requireAuthentication1DesSupported("incomplete area path for node support")

        val preferredTarget = scanContext.findBestAuthentication1DesTarget()
        if (preferredTarget == null) {
            throw StepSkipped(
                "No suitable system found for Authenticate1 DES incomplete area path for node support (root area with valid DES key is required)."
            )
        }

        val modeBeforeCheck =
            requireMode0ForAuthentication1DesBehavior("incomplete area path for node support")

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
                "No node found under an area under root area; cannot check Authenticate1 DES incomplete area path for node support."
            )
        }
        val challenge1A = ByteArray(8) { 0x00.toByte() }
        val areasToAuth = listOf(preferredTarget.rootArea)
        // Area0 may appear in both lists: this is allowed because key updates can target areas.
        val nodesToAuth = listOf<Node>(preferredTarget.rootArea, nonImmediateNode)

        val response =
            executeAuthentication1DesBehaviorCommand(
                systemContext = preferredTarget.systemContext,
                areaCodes = areasToAuth.map { it.code }.toTypedArray(),
                nodeCodes = nodesToAuth.map { it.code }.toTypedArray(),
                challenge1A = challenge1A,
            )

        val incompleteAreaPathForNodeSupported = authentication1DesBehaviorSupport(response)
        scanContext = scanContext.withCommands {
            copy(
                authentication1Des =
                    authentication1Des.copy(
                        incompleteAreaPathForNodeSupported = incompleteAreaPathForNodeSupported
                    )
            )
        }

        return StepOutput(
            buildString {
                    appendLine("Authenticate1 DES incomplete area path for node support check:")
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
                            "No response after $AUTHENTICATION1_DES_BEHAVIOR_ATTEMPTS attempts"
                        )
                    }
                    appendLine("Result: ${incompleteAreaPathForNodeSupported.toOutputLabel()}")
                }
                .trim()
        )
    }
}

internal abstract class Authentication1DesAreaListBehaviorStep(
    id: String,
    title: String,
    description: String,
    private val featureName: String,
    private val supportLabel: String,
) :
    ScanStep(
        id = id,
        title = title,
        description = description,
        icon = ScanStepIcon.LOCK,
    ) {
    protected abstract fun ScanSession.resolveTarget(): Authentication1DesBehaviorCommandTarget

    protected abstract fun CommandCapabilities.writeSupport(
        support: CommandSupport
    ): CommandCapabilities

    final override suspend fun ScanSession.perform(): StepOutput {
        requireAuthentication1DesSupported(featureName)
        val modeBeforeCheck = requireMode0ForAuthentication1DesBehavior(featureName)
        val testTarget = resolveTarget()
        val challenge1A = ByteArray(8) { 0x00.toByte() }

        val response =
            executeAuthentication1DesBehaviorCommand(
                systemContext = testTarget.systemContext,
                areaCodes = testTarget.areaEntries.map { it.code }.toTypedArray(),
                nodeCodes = testTarget.nodeEntries.map { it.code }.toTypedArray(),
                challenge1A = challenge1A,
            )

        val support = authentication1DesBehaviorSupport(response)
        scanContext = scanContext.withCommands { writeSupport(support) }

        return StepOutput(
            buildString {
                    appendLine("$featureName check:")
                    appendLine(
                        "System: ${testTarget.systemContext.systemCode?.toHexString()?.uppercase() ?: "unknown"}"
                    )
                    appendLine("Mode before check: $modeBeforeCheck")
                    appendLine("Area list:")
                    testTarget.areaEntries.forEachIndexed { index, entry ->
                        appendLine("  ${index + 1}. ${entry.label}")
                    }
                    appendLine("Node list:")
                    testTarget.nodeEntries.forEachIndexed { index, entry ->
                        appendLine("  ${index + 1}. ${entry.label}")
                    }
                    appendLine("Challenge1A: ${challenge1A.toHexString().uppercase()}")
                    if (response != null) {
                        appendLine("Challenge1B: ${response.challenge1B.toHexString().uppercase()}")
                        appendLine("Challenge2A: ${response.challenge2A.toHexString().uppercase()}")
                    } else {
                        appendLine(
                            "No response after $AUTHENTICATION1_DES_BEHAVIOR_ATTEMPTS attempts"
                        )
                    }
                    appendLine("$supportLabel: ${support.toOutputLabel()}")
                }
                .trim()
        )
    }
}

internal object Authentication1DesServiceInAreaPathSupportedStep :
    Authentication1DesAreaListBehaviorStep(
        id = "authentication1_des_determine_service_in_area_path_supported",
        title = "Authenticate1 DES: Service In Area Path Supported",
        description =
            "Check whether Authenticate1 DES accepts a service code in the area path while targeting root area",
        featureName = "Authenticate1 DES service in area path support",
        supportLabel = "Service in area path",
    ) {
    override fun ScanSession.resolveTarget(): Authentication1DesBehaviorCommandTarget {
        val target =
            scanContext.findBestAuthentication1DesServiceTarget()
                ?: throw StepSkipped(
                    "No service found; cannot check Authenticate1 DES service in area path support."
                )
        return Authentication1DesBehaviorCommandTarget(
            systemContext = target.systemContext,
            areaEntries =
                listOf(
                    Authentication1DesCodeEntry(
                        describeNode(target.rootArea),
                        target.rootArea.code,
                    ),
                    Authentication1DesCodeEntry(describeNode(target.service), target.service.code),
                ),
            nodeEntries =
                listOf(
                    Authentication1DesCodeEntry(
                        describeNode(target.rootArea),
                        target.rootArea.code,
                    )
                ),
        )
    }

    override fun CommandCapabilities.writeSupport(support: CommandSupport): CommandCapabilities =
        copy(authentication1Des = authentication1Des.copy(serviceInAreaPathSupported = support))
}
