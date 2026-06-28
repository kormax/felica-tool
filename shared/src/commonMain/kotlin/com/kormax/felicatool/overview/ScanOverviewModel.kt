package com.kormax.felicatool.overview

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.AreaAttribute
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.felica.ServiceAttribute
import com.kormax.felicatool.felica.System
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.service.byteToHex
import com.kormax.felicatool.service.formatBlockNumberHex
import com.kormax.felicatool.util.IcTypeRegistry
import com.kormax.felicatool.util.NodeDefinitionType
import com.kormax.felicatool.util.NodeRegistry
import com.kormax.felicatool.util.ServiceGrouper
import com.kormax.felicatool.util.ServiceIconCatalog
import com.kormax.felicatool.util.ServicePresenceAnalyzer

data class ScanOverviewModel(
    val cardInformation: List<ScanOverviewField>,
    val detectedProviders: List<ScanOverviewProvider>,
    val unknownServiceCount: Int,
    val systems: List<ScanOverviewSystem>,
    val commandSupportSections: List<ScanOverviewCommandSection>,
)

data class ScanOverviewField(
    val label: String,
    val value: String,
    val role: ScanOverviewChipRole = ScanOverviewChipRole.DEFAULT,
)

data class ScanOverviewProvider(
    val name: String,
    val systems: List<String>,
    val nodeCount: Int,
    val iconName: String?,
)

data class ScanOverviewProviderIcon(val name: String, val iconName: String?)

data class ScanOverviewSystem(
    val systemCode: String,
    val title: String,
    val summary: String,
    val providerIcons: List<ScanOverviewProviderIcon>,
    val nodes: List<ScanOverviewNode>,
    val nodeTree: List<ScanOverviewNodeTree>,
    val serviceGroups: List<ScanOverviewServiceGroup>,
)

data class ScanOverviewNode(
    val code: String,
    val title: String,
    val subtitle: String,
    val kind: ScanOverviewNodeKind,
    val source: ScanOverviewNodeSource,
    val providerIcons: List<ScanOverviewProviderIcon>,
    val chips: List<ScanOverviewChip>,
    val detailChips: List<ScanOverviewChip>,
    val details: List<ScanOverviewField>,
)

data class ScanOverviewNodeTree(
    val node: ScanOverviewNode,
    val children: List<ScanOverviewNodeTree>,
)

enum class ScanOverviewNodeKind {
    SYSTEM,
    AREA,
    SERVICE,
}

enum class ScanOverviewNodeSource {
    DISCOVERED,
    REGISTRY,
    HIDDEN,
}

data class ScanOverviewServiceGroup(
    val title: String,
    val subtitle: String,
    val chips: List<ScanOverviewChip>,
    val serviceCodes: List<String>,
    val variants: List<ScanOverviewServiceVariant>,
    val providerIcons: List<ScanOverviewProviderIcon>,
    val areaHeader: ScanOverviewAreaHeader?,
    val groupedUnderArea: Boolean,
)

data class ScanOverviewServiceVariant(
    val code: String,
    val mode: String,
    val authenticationRequired: Boolean,
    val pinRequired: Boolean,
    val hidden: Boolean,
    val unknown: Boolean,
)

data class ScanOverviewCommandSection(
    val title: String,
    val commands: List<ScanOverviewCommandSupport>,
)

data class ScanOverviewCommandSupport(
    val title: String,
    val status: CommandSupport,
    val trailingDataSupport: CommandSupport = CommandSupport.UNKNOWN,
)

data class ScanOverviewChip(val text: String, val role: ScanOverviewChipRole)

enum class ScanOverviewChipRole {
    DEFAULT,
    HIGHLIGHT,
    WARNING,
    INFO,
    SUCCESS,
}

data class ScanOverviewAreaHeader(
    val id: String,
    val title: String,
    val subtitle: String,
    val providerIcons: List<ScanOverviewProviderIcon>,
    val chips: List<ScanOverviewChip>,
)

object ScanOverviewModelBuilder {
    private data class AreaHeaderGroupKey(val number: Int, val endNumber: Int)

    private data class AreaHeaderGroup(val areas: List<Area>, val parentArea: Area?) {
        val number: Int = areas.first().number
        val endNumber: Int = areas.first().endNumber
        val isRoot: Boolean = areas.any { it.isRoot }
        val preferredArea: Area =
            areas.firstOrNull { it.isRoot }
                ?: areas.firstOrNull {
                    val attribute = it.attribute
                    attribute !is AreaAttribute.Unknown && attribute.canCreateSubArea
                }
                ?: areas.first()
        val hasUnknownAttribute: Boolean = areas.any { it.attribute is AreaAttribute.Unknown }
    }

    fun build(scanContext: CardScanContext): ScanOverviewModel {
        val providerDetection = ServicePresenceAnalyzer.detectProviders(scanContext)

        return ScanOverviewModel(
            cardInformation = buildCardInformation(scanContext),
            detectedProviders =
                providerDetection.providers.map { provider ->
                    ScanOverviewProvider(
                        name = provider.provider,
                        systems = provider.systems.toList(),
                        nodeCount = provider.nodeCount,
                        iconName = ServiceIconCatalog.iconNameFor(provider.provider),
                    )
                },
            unknownServiceCount = providerDetection.unknownServiceCount,
            systems = scanContext.systemScanContexts.map { buildSystem(it) },
            commandSupportSections = buildCommandSupport(scanContext),
        )
    }

    private fun buildCardInformation(scanContext: CardScanContext): List<ScanOverviewField> {
        return buildList {
            scanContext.primaryIdm?.let {
                add(ScanOverviewField("Primary IDM", it.toHexString().uppercase()))
            }
            scanContext.pmm?.let { pmm ->
                add(ScanOverviewField("PMM", pmm.toHexString().uppercase()))
                add(
                    ScanOverviewField(
                        "IC",
                        "0x${byteToHex(pmm.icType)}",
                    )
                )
                IcTypeRegistry.resolveIcType(pmm.icType, pmm.romType)?.let { resolution ->
                    add(
                        ScanOverviewField(
                            "IC Type",
                            if (resolution.isUncertain) "${resolution.name}?" else resolution.name,
                            if (resolution.isUncertain) {
                                ScanOverviewChipRole.WARNING
                            } else {
                                ScanOverviewChipRole.DEFAULT
                            },
                        )
                    )
                }
            }
            scanContext.primarySystemCode?.let {
                add(ScanOverviewField("Primary System Code", it.toHexString().uppercase()))
            }
            scanContext.productInformation?.let { productInformation ->
                if (
                    productInformation.success &&
                        productInformation.productInformationData.isNotEmpty()
                ) {
                    add(
                        ScanOverviewField(
                            "Product Information",
                            productInformation.productInformationData.toHexString().uppercase(),
                        )
                    )
                }
            }
            scanContext.containerIssueInformation?.let { containerInformation ->
                add(
                    ScanOverviewField(
                        "Format Version Carrier Info",
                        containerInformation.formatVersionCarrierInformation
                            .toHexString()
                            .uppercase(),
                    )
                )
                add(
                    ScanOverviewField(
                        "Mobile Phone Model",
                        printableOrHex(containerInformation.mobilePhoneModelInformation),
                    )
                )
            }
            scanContext.containerIdm?.let {
                add(ScanOverviewField("Container IDM", it.toHexString().uppercase()))
            }
            scanContext.specificationVersion?.let { specificationVersion ->
                add(
                    ScanOverviewField(
                        "Basic Version",
                        "${specificationVersion.basicVersion.major}.${specificationVersion.basicVersion.minor}",
                    )
                )
            }
        }
    }

    private fun buildSystem(systemContext: SystemScanContext): ScanOverviewSystem {
        val systemCode = systemContext.systemCode?.toHexString()?.uppercase() ?: "Unknown"
        val distinctNodes = systemContext.nodes.distinct()
        val systemName =
            if (systemCode == "Unknown") {
                null
            } else {
                NodeRegistry.getNodeName(systemCode, systemCode, NodeDefinitionType.SYSTEM)
            }
        val title =
            if (systemName != null) "System $systemCode - $systemName" else "System $systemCode"
        val areas = distinctNodes.filterIsInstance<Area>().size
        val services = distinctNodes.filterIsInstance<Service>().size
        val hiddenNodes = systemContext.hiddenNodes.size
        val registryNodes = systemContext.registryPopulatedNodes.size
        val summary =
            buildList {
                    add("$areas areas")
                    add("$services services")
                    if (hiddenNodes > 0) {
                        add("$hiddenNodes hidden")
                    }
                    if (registryNodes > 0) {
                        add("$registryNodes from registry")
                    }
                }
                .joinToString(" | ")
        val areaHeaderGroupsByArea = buildAreaHeaderGroups(systemContext)
        var previousAreaHeaderId: String? = null

        return ScanOverviewSystem(
            systemCode = systemCode,
            title = title,
            summary = summary,
            providerIcons = providerIconsFor(systemProviderNames(systemCode)),
            nodes = distinctNodes.map { buildNode(systemCode, systemContext, it) },
            nodeTree = buildNodeTree(systemCode, systemContext),
            serviceGroups =
                ServiceGrouper.groupServices(systemContext).map { group ->
                    val areaHeader =
                        group.parentArea
                            ?.let { areaHeaderGroupsByArea[it] }
                            ?.let { buildAreaHeader(systemCode, it) }
                    val emittedAreaHeader = areaHeader?.takeIf { header ->
                        header.id != previousAreaHeaderId
                    }
                    previousAreaHeaderId = areaHeader?.id
                    val parentCode = group.parentArea?.fullCode?.toHexString()
                    val primaryServiceCode = group.primaryService.code.toHexString()
                    val primaryServiceBlockData =
                        systemContext.serviceBlockData[group.primaryService]
                    val serviceName =
                        if (systemCode == "Unknown") {
                            null
                        } else {
                            NodeRegistry.getNodeName(
                                systemCode,
                                primaryServiceCode,
                                parentCode,
                                NodeDefinitionType.SERVICE,
                                blockData = primaryServiceBlockData,
                            )
                        }
                    ScanOverviewServiceGroup(
                        title =
                            if (serviceName != null) {
                                "Service #${group.number} - $serviceName"
                            } else {
                                "Service #${group.number}"
                            },
                        subtitle = "",
                        chips =
                            listOf(ScanOverviewChip(group.type.name, ScanOverviewChipRole.INFO)),
                        serviceCodes = group.services.map { it.code.toHexString().uppercase() },
                        variants =
                            group.services.map { service ->
                                ScanOverviewServiceVariant(
                                    code = service.code.toHexString().uppercase(),
                                    mode = serviceModeToken(service.attribute.mode.name),
                                    authenticationRequired =
                                        service.attribute.authenticationRequired,
                                    pinRequired = service.attribute.pinRequired,
                                    hidden = service in systemContext.hiddenNodes,
                                    unknown = service.attribute::class.simpleName == "Unknown",
                                )
                            },
                        providerIcons =
                            providerIconsFor(
                                nodeProviderNames(
                                    systemCode,
                                    primaryServiceCode,
                                    parentCode,
                                    NodeDefinitionType.SERVICE,
                                    blockData = primaryServiceBlockData,
                                )
                            ),
                        areaHeader = emittedAreaHeader,
                        groupedUnderArea = areaHeader != null,
                    )
                },
        )
    }

    private fun buildNode(
        systemCode: String,
        systemContext: SystemScanContext,
        node: Node,
    ): ScanOverviewNode {
        val source =
            when {
                node in systemContext.hiddenNodes -> ScanOverviewNodeSource.HIDDEN
                node in systemContext.registryPopulatedNodes -> ScanOverviewNodeSource.REGISTRY
                else -> ScanOverviewNodeSource.DISCOVERED
            }

        return when (node) {
            is System ->
                ScanOverviewNode(
                    code = systemCode,
                    title =
                        if (systemCode != "Unknown") {
                            NodeRegistry.getNodeName(
                                    systemCode,
                                    systemCode,
                                    NodeDefinitionType.SYSTEM,
                                )
                                ?.let { "System $systemCode - $it" } ?: "System $systemCode"
                        } else {
                            "System (No Code)"
                        },
                    subtitle = "Root node",
                    kind = ScanOverviewNodeKind.SYSTEM,
                    source = source,
                    providerIcons = providerIconsFor(systemProviderNames(systemCode)),
                    chips = emptyList(),
                    detailChips = buildNodeDetailChips(systemContext, node),
                    details = buildNodeDetails(systemContext, node),
                )
            is Area -> {
                val parentCode = findParentArea(node, systemContext)?.fullCode?.toHexString()
                val nodeName =
                    if (systemCode == "Unknown") {
                        null
                    } else {
                        NodeRegistry.getNodeName(
                            systemCode,
                            node.fullCode.toHexString(),
                            parentCode,
                            NodeDefinitionType.AREA,
                        )
                    }
                ScanOverviewNode(
                    code = node.fullCode.toHexString().uppercase(),
                    title =
                        buildString {
                            append(
                                "Area ${node.fullCode.toHexString().uppercase()} (${node.number}-${node.endNumber})"
                            )
                            if (nodeName != null) {
                                append(" - ")
                                append(nodeName)
                            }
                        },
                    subtitle = node.attribute::class.simpleName ?: "Area",
                    kind = ScanOverviewNodeKind.AREA,
                    source = source,
                    providerIcons =
                        providerIconsFor(
                            nodeProviderNames(
                                systemCode,
                                node.fullCode.toHexString(),
                                parentCode,
                                NodeDefinitionType.AREA,
                            )
                        ),
                    chips = buildNodeChips(systemContext, node),
                    detailChips = buildNodeDetailChips(systemContext, node),
                    details = buildNodeDetails(systemContext, node),
                )
            }
            is Service -> {
                val parentCode = findContainingArea(node, systemContext)?.fullCode?.toHexString()
                val blockData = systemContext.serviceBlockData[node]
                val nodeName =
                    if (systemCode == "Unknown") {
                        null
                    } else {
                        NodeRegistry.getNodeName(
                            systemCode,
                            node.code.toHexString(),
                            parentCode,
                            NodeDefinitionType.SERVICE,
                            blockData = blockData,
                        )
                    }
                ScanOverviewNode(
                    code = node.code.toHexString().uppercase(),
                    title =
                        buildString {
                            append(
                                "Service ${node.fullCode.toHexString().uppercase()} (#${node.number})"
                            )
                            if (nodeName != null) {
                                append(" - ")
                                append(nodeName)
                            }
                        },
                    subtitle = "",
                    kind = ScanOverviewNodeKind.SERVICE,
                    source = source,
                    providerIcons =
                        providerIconsFor(
                            nodeProviderNames(
                                systemCode,
                                node.code.toHexString(),
                                parentCode,
                                NodeDefinitionType.SERVICE,
                                blockData = blockData,
                            )
                        ),
                    chips = buildNodeChips(systemContext, node),
                    detailChips = buildNodeDetailChips(systemContext, node),
                    details = buildNodeDetails(systemContext, node),
                )
            }
            else ->
                ScanOverviewNode(
                    code = node.code.toHexString().uppercase(),
                    title = "Node",
                    subtitle = node.attribute::class.simpleName ?: "Unknown",
                    kind = ScanOverviewNodeKind.SERVICE,
                    source = source,
                    providerIcons = emptyList(),
                    chips = emptyList(),
                    detailChips = buildNodeDetailChips(systemContext, node),
                    details = buildNodeDetails(systemContext, node),
                )
        }
    }

    private fun buildNodeChips(
        systemContext: SystemScanContext,
        node: Node,
    ): List<ScanOverviewChip> {
        return buildList {
            when (node) {
                is Service -> {
                    if (node in systemContext.hiddenNodes) {
                        add(ScanOverviewChip("HIDDEN", ScanOverviewChipRole.WARNING))
                    }
                    if (node.attribute is ServiceAttribute.Unknown) {
                        add(ScanOverviewChip("A?", ScanOverviewChipRole.WARNING))
                    } else {
                        add(
                            ScanOverviewChip(
                                if (node.attribute.authenticationRequired) "AUTH" else "FREE",
                                if (node.attribute.authenticationRequired) {
                                    ScanOverviewChipRole.WARNING
                                } else {
                                    ScanOverviewChipRole.HIGHLIGHT
                                },
                            )
                        )
                        if (node.attribute.pinRequired) {
                            add(ScanOverviewChip("PIN", ScanOverviewChipRole.WARNING))
                        }
                        add(
                            ScanOverviewChip(node.attribute.type.name, ScanOverviewChipRole.DEFAULT)
                        )
                        add(
                            ScanOverviewChip(
                                serviceModeToken(node.attribute.mode.name),
                                ScanOverviewChipRole.DEFAULT,
                            )
                        )
                    }
                }
                is Area -> {
                    if (node.isRoot) {
                        add(ScanOverviewChip("ROOT", ScanOverviewChipRole.HIGHLIGHT))
                    }
                    if (node.attribute is AreaAttribute.Unknown) {
                        add(ScanOverviewChip("SA?", ScanOverviewChipRole.WARNING))
                    }
                    if (node.endAttribute is AreaAttribute.Unknown) {
                        add(ScanOverviewChip("EA?", ScanOverviewChipRole.WARNING))
                    }
                    if (
                        node.attribute !is AreaAttribute.Unknown && node.attribute.canCreateSubArea
                    ) {
                        add(ScanOverviewChip("NESTABLE", ScanOverviewChipRole.HIGHLIGHT))
                    }
                }
                else -> Unit
            }
        }
    }

    private fun buildNodeDetailChips(
        systemContext: SystemScanContext,
        node: Node,
    ): List<ScanOverviewChip> {
        return buildList {
            val encryptionIdentifier = systemContext.encryptionIdentifier
            if (encryptionIdentifier != null) {
                systemContext.nodeAesKeyVersions[node]?.let {
                    add(
                        ScanOverviewChip(
                            "${encryptionIdentifier.aesKeyType.name}: v${it.toInt()}",
                            ScanOverviewChipRole.INFO,
                        )
                    )
                }
                systemContext.nodeDesKeyVersions[node]?.let {
                    add(
                        ScanOverviewChip(
                            "${encryptionIdentifier.desKeyType.name}: v${it.toInt()}",
                            ScanOverviewChipRole.INFO,
                        )
                    )
                }
            } else {
                systemContext.nodeKeyVersions[node]?.let {
                    add(ScanOverviewChip("KEY v${it.toInt()}", ScanOverviewChipRole.INFO))
                }
                systemContext.nodeAesKeyVersions[node]?.let {
                    add(ScanOverviewChip("AES v${it.toInt()}", ScanOverviewChipRole.INFO))
                }
                systemContext.nodeDesKeyVersions[node]?.let {
                    add(ScanOverviewChip("DES v${it.toInt()}", ScanOverviewChipRole.INFO))
                }
            }

            if (node is Service) {
                systemContext.nodeValueLimitedPurseProperties[node]?.let { purseProperty ->
                    if (purseProperty.enabled) {
                        add(ScanOverviewChip("VLPS", ScanOverviewChipRole.INFO))
                    }
                }
                systemContext.nodeMacCommunicationProperties[node]?.let { macProperty ->
                    if (macProperty.enabled) {
                        add(ScanOverviewChip("MAC", ScanOverviewChipRole.INFO))
                    }
                }
            }

            val blockCount =
                systemContext.nodeBlockCounts[node]?.takeUnless { it.isInvalid }?.toInt()
            val assignedCount =
                systemContext.nodeAssignedBlockCounts[node]?.takeUnless { it.isInvalid }?.toInt()
            val freeCount =
                systemContext.nodeFreeBlockCounts[node]?.takeUnless { it.isInvalid }?.toInt()
            if (blockCount != null || assignedCount != null || freeCount != null) {
                val blockText =
                    when {
                        assignedCount != null && freeCount != null ->
                            "Blocks: $assignedCount assigned, $freeCount free"
                        assignedCount != null -> "Blocks: $assignedCount assigned"
                        freeCount != null -> "Blocks: $freeCount free"
                        else -> "Blocks: ${blockCount ?: 0}"
                    }
                add(ScanOverviewChip(blockText, ScanOverviewChipRole.INFO))
            }
        }
    }

    private fun buildNodeDetails(
        systemContext: SystemScanContext,
        node: Node,
    ): List<ScanOverviewField> {
        return buildList {
            if (node is System) {
                systemContext.idm?.let {
                    add(ScanOverviewField("IDM", it.toHexString().uppercase()))
                }
                systemContext.systemStatus?.let {
                    add(ScanOverviewField("System Status", it.toHexString().uppercase()))
                }
                add(
                    ScanOverviewField(
                        "Total Areas",
                        systemContext.nodes.filterIsInstance<Area>().size.toString(),
                    )
                )
                add(
                    ScanOverviewField(
                        "Total Services",
                        systemContext.nodes.filterIsInstance<Service>().size.toString(),
                    )
                )

                val hasAesKeys = systemContext.nodeAesKeyVersions.isNotEmpty()
                val hasDesKeys = systemContext.nodeDesKeyVersions.isNotEmpty()
                val hasGenericKeys = systemContext.nodeKeyVersions.isNotEmpty()
                if (hasGenericKeys && !hasAesKeys && !hasDesKeys) {
                    add(
                        ScanOverviewField(
                            "Key Versions",
                            systemContext.nodeKeyVersions.size.toString(),
                        )
                    )
                } else {
                    if (hasAesKeys) {
                        add(
                            ScanOverviewField(
                                "AES Keys",
                                systemContext.nodeAesKeyVersions.size.toString(),
                            )
                        )
                    }
                    if (hasDesKeys) {
                        add(
                            ScanOverviewField(
                                "DES Keys",
                                systemContext.nodeDesKeyVersions.size.toString(),
                            )
                        )
                    }
                }
            }

            systemContext.nodeValueLimitedPurseProperties[node]?.let { purseProperty ->
                if (purseProperty.enabled) {
                    add(ScanOverviewField("VLPS Upper Limit", purseProperty.upperLimit.toString()))
                    add(ScanOverviewField("VLPS Lower Limit", purseProperty.lowerLimit.toString()))
                }
            }

            systemContext.serviceBlockData[node]?.let { blockData ->
                if (blockData.isNotEmpty()) {
                    val blockCount = blockData.size
                    val totalBytes = blockCount * 16
                    val regularBlocks = blockData.keys.count { it < 0x80 }
                    val extraBlocks = blockData.keys.count { it >= 0x80 }
                    val summary =
                        if (extraBlocks > 0) {
                            "$blockCount blocks ($regularBlocks regular, $extraBlocks extra) ($totalBytes bytes)"
                        } else {
                            "$blockCount blocks ($totalBytes bytes)"
                        }
                    add(ScanOverviewField("Block Data", summary))
                    blockData.entries
                        .sortedBy { it.key }
                        .forEach { (blockNumber, bytes) ->
                            val blockNumberHex = formatBlockNumberHex(blockNumber)
                            add(ScanOverviewField(blockNumberHex, bytes.toHexString().uppercase()))
                        }
                }
            }
        }
    }

    private fun buildNodeTree(
        systemCode: String,
        systemContext: SystemScanContext,
    ): List<ScanOverviewNodeTree> {
        val nodesByParent = mutableMapOf<String, MutableList<Node>>()
        val systemNode = systemContext.nodes.filterIsInstance<System>().firstOrNull() ?: System
        val areas = systemContext.nodes.filterIsInstance<Area>().distinct()
        val services = systemContext.nodes.filterIsInstance<Service>().distinct()

        areas.forEach { area ->
            val parent = findParentArea(area, systemContext)
            nodesByParent
                .getOrPut(parent?.let { nodeTreeKey(it) } ?: SYSTEM_TREE_KEY) { mutableListOf() }
                .add(area)
        }
        services.forEach { service ->
            val parent = findContainingArea(service, systemContext)
            nodesByParent
                .getOrPut(parent?.let { nodeTreeKey(it) } ?: SYSTEM_TREE_KEY) { mutableListOf() }
                .add(service)
        }

        fun buildTree(node: Node): ScanOverviewNodeTree {
            val childNodes = nodesByParent[nodeTreeKey(node)].orEmpty().sortedWith(nodeTreeSorter)
            return ScanOverviewNodeTree(
                node = buildNode(systemCode, systemContext, node),
                children = childNodes.map { buildTree(it) },
            )
        }

        return listOf(buildTree(systemNode))
    }

    private fun buildAreaHeader(
        systemCode: String,
        headerGroup: AreaHeaderGroup,
    ): ScanOverviewAreaHeader {
        val parentCode = headerGroup.parentArea?.fullCode?.toHexString()
        val areaCodesText =
            headerGroup.areas.joinToString(separator = " / ") {
                it.fullCode.toHexString().uppercase()
            }
        val baseTitle =
            if (headerGroup.areas.size > 1) {
                "Area Group ${headerGroup.number}-${headerGroup.endNumber}"
            } else {
                "Area ${headerGroup.number}-${headerGroup.endNumber}"
            }
        val areaName =
            resolveInAreaGroup(headerGroup) { area ->
                NodeRegistry.getNodeName(
                    systemCode,
                    area.fullCode.toHexString().uppercase(),
                    parentCode,
                    NodeDefinitionType.AREA,
                )
            }
        val providerIcons =
            resolveInAreaGroup(headerGroup) { area ->
                providerIconsFor(
                        nodeProviderNames(
                            systemCode,
                            area.fullCode.toHexString().uppercase(),
                            parentCode,
                            NodeDefinitionType.AREA,
                        )
                    )
                    .takeIf { it.isNotEmpty() }
            } ?: emptyList()

        return ScanOverviewAreaHeader(
            id = areaCodesText,
            title = if (areaName != null) "$baseTitle - $areaName" else baseTitle,
            subtitle = areaCodesText,
            providerIcons = providerIcons,
            chips =
                buildList {
                    if (headerGroup.isRoot) {
                        add(ScanOverviewChip("ROOT", ScanOverviewChipRole.HIGHLIGHT))
                    }
                    if (headerGroup.hasUnknownAttribute) {
                        add(ScanOverviewChip("SA?", ScanOverviewChipRole.WARNING))
                    }
                },
        )
    }

    private fun buildAreaHeaderGroups(context: SystemScanContext): Map<Area, AreaHeaderGroup> {
        val areas = context.nodes.filterIsInstance<Area>()
        if (areas.isEmpty()) {
            return emptyMap()
        }

        val parentByArea = areas.associateWith { area -> findImmediateParentArea(area, areas) }
        val topLevelAreaByArea = areas.associateWith { area ->
            findTopLevelAreaUnderRoot(area, parentByArea)
        }

        val headersByTopLevelArea =
            topLevelAreaByArea.values
                .filterNotNull()
                .distinct()
                .groupBy { topLevelArea ->
                    AreaHeaderGroupKey(
                        number = topLevelArea.number,
                        endNumber = topLevelArea.endNumber,
                    )
                }
                .values
                .flatMap { sameRangeTopLevelAreas ->
                    val sortedAreas =
                        sameRangeTopLevelAreas.sortedWith(
                            compareByDescending<Area> { it.isRoot }
                                .thenByDescending { it.attribute.canCreateSubArea }
                                .thenBy { it.attribute.value }
                                .thenBy { it.fullCode.toHexString() }
                        )
                    val headerGroup =
                        AreaHeaderGroup(
                            areas = sortedAreas,
                            parentArea = parentByArea[sortedAreas.first()],
                        )
                    sortedAreas.map { topLevelArea -> topLevelArea to headerGroup }
                }
                .toMap()

        return topLevelAreaByArea
            .mapNotNull { (area, topLevelArea) ->
                topLevelArea?.let { top ->
                    headersByTopLevelArea[top]?.let { header -> area to header }
                }
            }
            .toMap()
    }

    private fun findTopLevelAreaUnderRoot(area: Area, parentByArea: Map<Area, Area?>): Area? {
        var current = area
        var parent = parentByArea[current]

        while (parent != null && !parent.isRoot) {
            current = parent
            parent = parentByArea[current]
        }

        return if (parent?.isRoot == true) current else null
    }

    private fun findImmediateParentArea(area: Area, areas: List<Area>): Area? {
        return areas
            .filter { candidate ->
                candidate != area &&
                    area.number >= candidate.number &&
                    area.endNumber <= candidate.endNumber &&
                    (candidate.number < area.number || candidate.endNumber > area.endNumber)
            }
            .minWithOrNull(compareBy<Area>({ it.endNumber - it.number }, { it.number }))
    }

    private fun <T> resolveInAreaGroup(headerGroup: AreaHeaderGroup, resolver: (Area) -> T?): T? {
        resolver(headerGroup.preferredArea)?.let {
            return it
        }
        for (area in headerGroup.areas) {
            if (area == headerGroup.preferredArea) {
                continue
            }
            resolver(area)?.let {
                return it
            }
        }
        return null
    }

    private fun buildCommandSupport(
        scanContext: CardScanContext
    ): List<ScanOverviewCommandSection> {
        fun commandSupport(
            title: String,
            status: CommandSupport,
            trailingDataSupport: CommandSupport = CommandSupport.UNKNOWN,
        ) =
            ScanOverviewCommandSupport(
                title = title,
                status = status,
                trailingDataSupport = trailingDataSupport,
            )

        return listOf(
            ScanOverviewCommandSection(
                title = "Basic Commands",
                commands =
                    listOf(
                        commandSupport(
                            "Polling",
                            scanContext.commands.polling.supported,
                            scanContext.commands.polling.trailingDataSupported,
                        ),
                        commandSupport(
                            "Polling (System Code)",
                            scanContext.commands.polling.systemCodeSupported,
                        ),
                        commandSupport(
                            "Polling (Communication Performance)",
                            scanContext.commands.polling.communicationPerformanceSupported,
                        ),
                        commandSupport(
                            "Request Response",
                            scanContext.commands.requestResponse.supported,
                            scanContext.commands.requestResponse.trailingDataSupported,
                        ),
                        commandSupport(
                            "Request System Code",
                            scanContext.commands.requestSystemCode.supported,
                            scanContext.commands.requestSystemCode.trailingDataSupported,
                        ),
                        commandSupport(
                            "Request Specification",
                            scanContext.commands.requestSpecificationVersion.supported,
                            scanContext.commands.requestSpecificationVersion.trailingDataSupported,
                        ),
                        commandSupport(
                            "Request Product Info",
                            scanContext.commands.requestProductInformation.supported,
                            scanContext.commands.requestProductInformation.trailingDataSupported,
                        ),
                    ),
            ),
            ScanOverviewCommandSection(
                title = "Node Commands",
                commands =
                    listOf(
                        commandSupport(
                            "Search Service Code",
                            scanContext.commands.searchServiceCode.supported,
                            scanContext.commands.searchServiceCode.trailingDataSupported,
                        ),
                        commandSupport(
                            "Request Code List",
                            scanContext.commands.requestCodeList.supported,
                            scanContext.commands.requestCodeList.trailingDataSupported,
                        ),
                        commandSupport(
                            "Request Service",
                            scanContext.commands.requestService.supported,
                            scanContext.commands.requestService.trailingDataSupported,
                        ),
                        commandSupport(
                            "Request Service V2",
                            scanContext.commands.requestServiceV2.supported,
                            scanContext.commands.requestServiceV2.trailingDataSupported,
                        ),
                        commandSupport(
                            "Request Block Info",
                            scanContext.commands.requestBlockInformation.supported,
                            scanContext.commands.requestBlockInformation.trailingDataSupported,
                        ),
                        commandSupport(
                            "Request Block Info Ex",
                            scanContext.commands.requestBlockInformationEx.supported,
                            scanContext.commands.requestBlockInformationEx.trailingDataSupported,
                        ),
                        commandSupport(
                            "Get Node Property",
                            scanContext.commands.getNodeProperty.supported,
                            scanContext.commands.getNodeProperty.trailingDataSupported,
                        ),
                        commandSupport(
                            "Get Node Property (MAC Communication)",
                            scanContext.commands.getNodeProperty.macCommunicationSupported,
                        ),
                        commandSupport(
                            "Get Node Property (Value Limited Service)",
                            scanContext.commands.getNodeProperty.valueLimitedServiceSupported,
                        ),
                    ),
            ),
            ScanOverviewCommandSection(
                title = "Block and Auth Commands",
                commands =
                    listOf(
                        commandSupport(
                            "Read Without Encryption",
                            scanContext.commands.readWithoutEncryption.supported,
                            scanContext.commands.readWithoutEncryption.trailingDataSupported,
                        ),
                        commandSupport(
                            "Write Without Encryption",
                            scanContext.commands.writeWithoutEncryption.supported,
                            scanContext.commands.writeWithoutEncryption.trailingDataSupported,
                        ),
                        commandSupport(
                            "Reset Mode",
                            scanContext.commands.resetMode.supported,
                            scanContext.commands.resetMode.trailingDataSupported,
                        ),
                        commandSupport(
                            "Authenticate1 DES",
                            scanContext.commands.authentication1Des.supported,
                            scanContext.commands.authentication1Des.trailingDataSupported,
                        ),
                        commandSupport(
                            "Authenticate1 AES",
                            scanContext.commands.authentication1Aes.supported,
                            scanContext.commands.authentication1Aes.trailingDataSupported,
                        ),
                        commandSupport(
                            "Internal Authenticate and Read",
                            scanContext.commands.internalAuthenticateAndRead.supported,
                            scanContext.commands.internalAuthenticateAndRead.trailingDataSupported,
                        ),
                    ),
            ),
        )
    }

    private fun serviceModeToken(modeName: String): String {
        return when (modeName) {
            "READ_WRITE" -> "RW"
            "READ_ONLY" -> "RO"
            "CASHBACK" -> "CB"
            "DECREMENT" -> "DEC"
            else -> modeName.take(3)
        }
    }

    private fun findContainingArea(node: Node, context: SystemScanContext): Area? {
        return context.nodes
            .filterIsInstance<Area>()
            .filter { candidate -> node.belongsTo(candidate) }
            .minByOrNull { it.endNumber - it.number }
    }

    private fun findParentArea(area: Area, context: SystemScanContext): Area? {
        val parentAreas =
            context.nodes.filterIsInstance<Area>().filter { other ->
                other != area && area.belongsTo(other)
            }
        return parentAreas.minByOrNull { it.endNumber - it.number }
    }

    private fun systemProviderNames(systemCode: String): Set<String> {
        return if (systemCode == "Unknown") emptySet()
        else NodeRegistry.getSystemProviders(systemCode)
    }

    private fun nodeProviderNames(
        systemCode: String,
        nodeCode: String,
        parentCode: String?,
        type: NodeDefinitionType,
        blockData: Map<Int, ByteArray>? = null,
    ): Set<String> {
        return if (systemCode == "Unknown") {
            emptySet()
        } else {
            NodeRegistry.getProvidersForNode(systemCode, nodeCode, parentCode, type, blockData)
        }
    }

    private fun providerIconsFor(providerNames: Iterable<String>): List<ScanOverviewProviderIcon> {
        return providerNames.sorted().map { providerName ->
            ScanOverviewProviderIcon(
                name = providerName,
                iconName = ServiceIconCatalog.iconNameFor(providerName),
            )
        }
    }

    private fun nodeTreeKey(node: Node): String {
        return if (node is System) SYSTEM_TREE_KEY else node.fullCode.toHexString().uppercase()
    }

    private val nodeTreeSorter =
        compareBy<Node>(
            {
                when (it) {
                    is System -> 0
                    is Area -> 1
                    else -> 2
                }
            },
            { it.number },
            { it.fullCode.toHexString() },
        )

    private const val SYSTEM_TREE_KEY = "system"

    private fun printableOrHex(bytes: ByteArray): String {
        val printableBytes = bytes.filter { (it.toInt() and 0xFF) in 32..126 }
        return if (printableBytes.size >= 3) {
            printableBytes.map { (it.toInt() and 0xFF).toChar() }.joinToString("")
        } else {
            bytes.toHexString().uppercase()
        }
    }
}
