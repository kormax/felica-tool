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

private data class Authentication1DesNonImmediateNodeTestTarget(
    val systemContext: SystemScanContext,
    val rootArea: Area,
    val node: Node,
    val systemIndex: Int,
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

private val authentication1DesAreaContainmentComparator =
    compareBy<Area> { it.endNumber - it.number }
        .thenByDescending { it.number }
        .thenBy { it.attribute.canCreateSubArea }
        .thenBy { it.fullCode.toHexString() }

private fun Area.hasSameRangeButCannotNestIn(candidate: Area): Boolean =
    number == candidate.number &&
        endNumber == candidate.endNumber &&
        attribute.canCreateSubArea &&
        !candidate.attribute.canCreateSubArea

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

private fun CardScanContext.findBestAuthentication1DesAuthRequiredServiceTarget():
    Authentication1DesServiceTestTarget? {
    val candidates = mutableListOf<Authentication1DesServiceTestTarget>()

    systemScanContexts.forEach { systemContext ->
        val rootArea =
            systemContext.nodes.filterIsInstance<Area>().firstOrNull { it.isRoot } ?: Area.ROOT
        if (!systemContext.hasAuthentication1DesKey(rootArea)) {
            return@forEach
        }

        systemContext.nodes
            .filterIsInstance<Service>()
            .filter { service ->
                service.attribute !is ServiceAttribute.Unknown &&
                    service.attribute.authenticationRequired &&
                    systemContext.hasAuthentication1DesKey(service)
            }
            .forEach { service ->
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
            { it.service.number },
            { it.service.attribute.value },
        )
    )
}

private fun SystemScanContext.authentication1DesAreaPath(node: Node): List<Area> {
    val areas = nodes.filterIsInstance<Area>()
    val leafToRoot = mutableListOf<Area>()
    val visited = mutableSetOf<Area>()
    var child: Node = node

    while (true) {
        val childArea = child as? Area
        val parent =
            areas
                .filter { candidate ->
                    candidate !in visited &&
                        child.belongsTo(candidate) &&
                        (childArea == null ||
                            (candidate != childArea &&
                                !childArea.hasSameRangeButCannotNestIn(candidate)))
                }
                .minWithOrNull(authentication1DesAreaContainmentComparator) ?: return emptyList()

        leafToRoot += parent
        if (parent.isRoot) {
            return leafToRoot.asReversed()
        }
        visited += parent
        child = parent
    }
}

private fun CardScanContext.findAuthentication1DesNonImmediateNodeTarget():
    Authentication1DesNonImmediateNodeTestTarget? {
    val candidates = mutableListOf<Authentication1DesNonImmediateNodeTestTarget>()

    systemScanContexts.forEachIndexed { systemIndex, systemContext ->
        val rootArea =
            systemContext.nodes.filterIsInstance<Area>().firstOrNull { it.isRoot } ?: Area.ROOT
        if (!systemContext.hasAuthentication1DesKey(rootArea)) {
            return@forEachIndexed
        }

        val nonRootAreas =
            systemContext.nodes.filterIsInstance<Area>().filter { area ->
                area != rootArea && !area.isRoot && area.belongsTo(rootArea)
            }

        systemContext.nodes
            .filterIsInstance<Service>()
            .filter { service ->
                service.attribute !is ServiceAttribute.Unknown &&
                    systemContext.hasAuthentication1DesKey(service) &&
                    nonRootAreas.any { area -> service.belongsTo(area) }
            }
            .forEach { service ->
                candidates +=
                    Authentication1DesNonImmediateNodeTestTarget(
                        systemContext = systemContext,
                        rootArea = rootArea,
                        node = service,
                        systemIndex = systemIndex,
                    )
            }

        nonRootAreas
            .filter { candidate ->
                systemContext.hasAuthentication1DesKey(candidate) &&
                    nonRootAreas.any { parent ->
                        parent != candidate && candidate.belongsTo(parent)
                    }
            }
            .forEach { area ->
                candidates +=
                    Authentication1DesNonImmediateNodeTestTarget(
                        systemContext = systemContext,
                        rootArea = rootArea,
                        node = area,
                        systemIndex = systemIndex,
                    )
            }
    }

    return candidates.minWithOrNull(
        compareBy<Authentication1DesNonImmediateNodeTestTarget>(
            {
                when (val node = it.node) {
                    is Service -> if (node.attribute.authenticationRequired) 0 else 1
                    is Area -> 2
                    else -> 3
                }
            },
            { it.systemIndex },
            { it.node.number },
            { it.node.attribute.value },
        )
    )
}

private fun CardScanContext.findAuthentication1DesAreaListWithoutRootAreaTarget():
    Authentication1DesBehaviorCommandTarget? {
    for (systemContext in systemScanContexts) {
        val services =
            systemContext.nodes
                .filterIsInstance<Service>()
                .filter { service ->
                    service.attribute.authenticationRequired &&
                        systemContext.hasAuthentication1DesKey(service)
                }
                .sortedWith(compareBy<Service> { it.number }.thenBy { it.attribute.value })

        for (service in services) {
            val fullPath = systemContext.authentication1DesAreaPath(service)
            val parent = fullPath.lastOrNull() ?: continue
            if (parent.isRoot || fullPath.any { !systemContext.hasAuthentication1DesKey(it) }) {
                continue
            }
            return Authentication1DesBehaviorCommandTarget(
                systemContext = systemContext,
                areaEntries =
                    fullPath.drop(1).map { area ->
                        Authentication1DesCodeEntry(describeNode(area), area.code)
                    },
                nodeEntries =
                    listOf(Authentication1DesCodeEntry(describeNode(service), service.code)),
            )
        }
    }
    return null
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
        val systemCodeHex =
            testTarget.systemContext.systemCode?.toHexString()?.uppercase() ?: "unknown"
        ScanLog.d(
            "CardScanService",
            "Selected system $systemCodeHex for DES authentication using root area ${testTarget.rootArea.code.toHexString()} in area and node lists (node count: ${testTarget.systemContext.nodes.size})",
        )

        // Generate a random challenge1A (8 bytes)
        val challenge1A = ByteArray(8) { 0x00.toByte() }
        val modeBeforeCheck = currentMode

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
                appendLine("Authenticate1 DES support check:")
                appendLine("System: $systemCodeHex")
                appendLine("Mode before check: $modeBeforeCheck")
                appendLine("Area list:")
                areasToAuth.forEachIndexed { index, area ->
                    appendLine("  ${index + 1}. ${describeNode(area)}")
                }
                appendLine("Node list:")
                nodesToAuth.forEachIndexed { index, node ->
                    appendLine("  ${index + 1}. ${describeNode(node)}")
                }
                appendLine("Challenge1A: ${challenge1A.toHexString().uppercase()}")
                appendLine(
                    "Challenge1B: ${authenticateResponse.challenge1B.toHexString().uppercase()}"
                )
                appendLine(
                    "Challenge2A: ${authenticateResponse.challenge2A.toHexString().uppercase()}"
                )
                appendLine("Authenticate1 DES: ${CommandSupport.SUPPORTED.toOutputLabel()}")
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

internal object Authentication1DesAreaListWithoutNodeImmediateParentAreaSupportedStep :
    ScanStep(
        id = "authentication1_des_determine_area_list_without_node_immediate_parent_area_supported",
        title = "Authenticate1 DES: Area List Without Node Immediate Parent Area Supported",
        description =
            "Check whether Authenticate1 DES accepts a node when its immediate parent area is omitted from the area list",
        icon = ScanStepIcon.LOCK,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        requireAuthentication1DesSupported("area list without node immediate parent area support")

        val preferredTarget = scanContext.findAuthentication1DesNonImmediateNodeTarget()
        if (preferredTarget == null) {
            throw StepSkipped(
                "No DES-keyed node found under a non-root area; cannot check Authenticate1 DES area list without node immediate parent area support."
            )
        }

        val modeBeforeCheck =
            requireMode0ForAuthentication1DesBehavior(
                "area list without node immediate parent area support"
            )

        val challenge1A = ByteArray(8) { 0x00.toByte() }
        val areasToAuth = listOf(preferredTarget.rootArea)
        // Area0 may appear in both lists: this is allowed because key updates can target areas.
        val nodesToAuth = listOf(preferredTarget.rootArea, preferredTarget.node)

        val response =
            executeAuthentication1DesBehaviorCommand(
                systemContext = preferredTarget.systemContext,
                areaCodes = areasToAuth.map { it.code }.toTypedArray(),
                nodeCodes = nodesToAuth.map { it.code }.toTypedArray(),
                challenge1A = challenge1A,
            )

        val areaListWithoutNodeImmediateParentAreaSupported =
            authentication1DesBehaviorSupport(response)
        scanContext = scanContext.withCommands {
            copy(
                authentication1Des =
                    authentication1Des.copy(
                        areaListWithoutNodeImmediateParentAreaSupported =
                            areaListWithoutNodeImmediateParentAreaSupported
                    )
            )
        }

        return StepOutput(
            buildString {
                appendLine(
                    "Authenticate1 DES area list without node immediate parent area support check:"
                )
                appendLine(
                    "System: ${preferredTarget.systemContext.systemCode?.toHexString()?.uppercase() ?: "unknown"}"
                )
                appendLine("Mode before check: $modeBeforeCheck")
                appendLine("Area list:")
                appendLine("  1. ${describeNode(preferredTarget.rootArea)}")
                appendLine("Node list:")
                appendLine("  1. ${describeNode(preferredTarget.rootArea)}")
                appendLine("  2. ${describeNode(preferredTarget.node)}")
                appendLine("Challenge1A: ${challenge1A.toHexString().uppercase()}")
                if (response != null) {
                    appendLine("Challenge1B: ${response.challenge1B.toHexString().uppercase()}")
                    appendLine("Challenge2A: ${response.challenge2A.toHexString().uppercase()}")
                } else {
                    appendLine("No response after $AUTHENTICATION1_DES_BEHAVIOR_ATTEMPTS attempts")
                }
                appendLine(
                    "Area list without node immediate parent area: ${areaListWithoutNodeImmediateParentAreaSupported.toOutputLabel()}"
                )
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
                    appendLine("No response after $AUTHENTICATION1_DES_BEHAVIOR_ATTEMPTS attempts")
                }
                appendLine("$supportLabel: ${support.toOutputLabel()}")
            }
                .trim()
        )
    }
}

internal object Authentication1DesAreaListWithoutRootAreaSupportedStep :
    Authentication1DesAreaListBehaviorStep(
        id = "authentication1_des_determine_area_list_without_root_area_supported",
        title = "Authenticate1 DES: Area List Without Root Area Supported",
        description =
            "Check whether Authenticate1 DES accepts an area list without the root area when every specified area is a parent of a specified node",
        featureName = "Authenticate1 DES area list without root area support",
        supportLabel = "Area list without root area",
    ) {
    override fun ScanSession.resolveTarget(): Authentication1DesBehaviorCommandTarget =
        scanContext.findAuthentication1DesAreaListWithoutRootAreaTarget()
            ?: throw StepSkipped(
                "No authentication-required DES service with a complete DES-keyed path and a non-root parent was found."
            )

    override fun CommandCapabilities.writeSupport(support: CommandSupport): CommandCapabilities =
        copy(
            authentication1Des = authentication1Des.copy(areaListWithoutRootAreaSupported = support)
        )
}

internal object Authentication1DesAuthenticationRequiredServiceInAreaPathSupportedStep :
    Authentication1DesAreaListBehaviorStep(
        id = "authentication1_des_determine_authentication_required_service_in_area_path_supported",
        title = "Authenticate1 DES: Auth-Required Service In Area Path Supported",
        description =
            "Check whether Authenticate1 DES accepts an authentication-required service code in the area path while targeting root area",
        featureName = "Authenticate1 DES authentication-required service in area path support",
        supportLabel = "Authentication-required service in area path",
    ) {
    override fun ScanSession.resolveTarget(): Authentication1DesBehaviorCommandTarget {
        val target =
            scanContext.findBestAuthentication1DesAuthRequiredServiceTarget()
                ?: throw StepSkipped(
                    "No DES-keyed authentication-required service found; cannot check Authenticate1 DES authentication-required service in area path support."
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
        copy(
            authentication1Des =
                authentication1Des.copy(authenticationRequiredServiceInAreaPathSupported = support)
        )
}
