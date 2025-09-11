package com.kormax.felicatool.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.util.IcTypeMapping
import com.kormax.felicatool.util.NodeNaming

/** Data class representing a node in the hierarchical tree structure */
data class NodeInformation(val node: Node, val children: List<NodeInformation> = emptyList()) {
    val hasChildren: Boolean = children.isNotEmpty()

    val areaCount: Int by lazy { (if (node is Area) 1 else 0) + children.sumOf { it.areaCount } }

    val serviceCount: Int by lazy {
        (if (node is Service) 1 else 0) + children.sumOf { it.serviceCount }
    }

    val immediateAreaCount: Int by lazy { children.count { it.node is Area } }

    val immediateServiceCount: Int by lazy { children.count { it.node is Service } }
}

/** Recursively builds a tree of NodeInformation from the context using range-based logic */
fun buildNodeTree(context: SystemScanContext): List<NodeInformation> {
    val (_, rootChildren) =
        createTree(
            0,
            0xFFFF,
            context.nodes
                .sortedWith(compareBy<Node> { it !is System }.thenBy { it.number })
                .toMutableList(),
        )
    return rootChildren
}

/** Helper function to create tree based on begin/end ranges */
private fun createTree(
    begin: Int,
    end: Int,
    nodes: MutableList<Node>,
): Pair<MutableList<Node>, MutableList<NodeInformation>> {
    val result = mutableListOf<NodeInformation>()
    val leftover = mutableListOf<Node>()

    while (nodes.isNotEmpty()) {
        val node = nodes.removeAt(0)
        var added = false

        when (node) {
            is Service -> {
                if (begin <= node.number && node.number <= end) {
                    result.add(NodeInformation(node))
                    added = true
                }
            }
            is Area -> {
                if (node.number >= begin && end >= node.endNumber) {
                    val (leftoverSub, children) = createTree(node.number, node.endNumber, nodes)
                    nodes.clear()
                    nodes.addAll(leftoverSub)
                    result.add(NodeInformation(node, children))
                    added = true
                }
            }
            is System -> {
                if (begin == 0 && end == 0xFFFF) {
                    val (leftoverSub, children) = createTree(0, 0xFFFF, nodes)
                    nodes.clear()
                    nodes.addAll(leftoverSub)
                    result.add(NodeInformation(node, children))
                    added = true
                }
            }
        }

        if (!added) {
            leftover.add(node)
        }
    }

    return leftover to result
}

@Composable fun NodeDataDisplay(context: CardScanContext, modifier: Modifier = Modifier) {}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardInformationSection(context: CardScanContext, modifier: Modifier = Modifier) {
    var isExpanded by remember { mutableStateOf(true) }
    val rotationAngle by
        animateFloatAsState(targetValue = if (isExpanded) 0f else 180f, label = "cardInfoRotation")

    val systemContext = context.systemScanContexts.firstOrNull()

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Card Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp).rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // IDM
                context.primaryIdm?.let { idm ->
                    CompactInfoRow(label = "Primary IDM", value = idm.toHexString())
                }

                // PMM
                context.pmm?.let { pmm ->
                    CompactInfoRow(label = "PMM", value = pmm.toHexString())
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        InfoChip(
                            label = "ROM",
                            value =
                                "0x${pmm.romType.toUByte().toString(16).uppercase().padStart(2, '0')}",
                        )
                        InfoChip(label = "IC", value = IcTypeMapping.getFormattedIcType(pmm.icType))
                    }
                }

                // System Code
                context.primarySystemCode?.let { systemCode ->
                    CompactInfoRow(label = "Primary System Code", value = systemCode.toHexString())
                }

                // Platform Info
                context.platformInformation?.let { secureElementInfo ->
                    if (
                        secureElementInfo.success &&
                            secureElementInfo.platformInformationData.isNotEmpty()
                    ) {
                        CompactInfoRow(
                            label = "Platform Information",
                            value = secureElementInfo.platformInformationData.toHexString(),
                        )
                    }
                }

                // Container Information Section (only if container commands responded)
                val hasContainerInfo =
                    context.containerIssueInformation != null ||
                        context.containerIdm != null ||
                        context.platformInformation != null

                if (hasContainerInfo) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Container Information",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    // Container Issue Information
                    context.containerIssueInformation?.let { containerInfo ->
                        CompactInfoRow(
                            label = "Format Version Carrier Info",
                            value = containerInfo.formatVersionCarrierInformation.toHexString(),
                        )

                        // Try to decode mobile phone model as printable string
                        val modelString =
                            try {
                                val printableBytes =
                                    containerInfo.mobilePhoneModelInformation.filter {
                                        it in 32..126
                                    }
                                if (printableBytes.size >= 3) { // At least 3 printable characters
                                    String(printableBytes.toByteArray(), Charsets.UTF_8)
                                } else {
                                    containerInfo.mobilePhoneModelInformation.toHexString()
                                }
                            } catch (e: Exception) {
                                containerInfo.mobilePhoneModelInformation.toHexString()
                            }

                        CompactInfoRow(label = "Mobile Phone Model", value = modelString)
                    }

                    // Container IDM
                    context.containerIdm?.let { containerIdm ->
                        CompactInfoRow(label = "Container IDM", value = containerIdm.toHexString())
                    }
                }

                // Communication Performance Section
                context.communicationPerformance?.let { commPerf ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "Communication Performance",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
                        if (commPerf.supports212kbps) {
                            InfoChip(label = "212 kbps", value = "✓", isSuccessful = true)
                        }
                        if (commPerf.supports424kbps) {
                            InfoChip(label = "424 kbps", value = "✓", isSuccessful = true)
                        }
                        if (commPerf.supports848kbps) {
                            InfoChip(label = "848 kbps", value = "✓", isSuccessful = true)
                        }
                        if (commPerf.supports1696kbps) {
                            InfoChip(label = "1696 kbps", value = "✓", isSuccessful = true)
                        }

                        if (commPerf.isAutomaticDetectionCompliant) {
                            InfoChip(
                                label = "automatic detection",
                                value = "✓",
                                isSuccessful = true,
                            )
                        }
                    }
                }

                // Feature Support (Option Versions) Section
                context.specificationVersion?.let { specVersion ->
                    if (specVersion.isStatusSuccessful) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = "Feature Support",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )

                        specVersion.formatVersion?.let { version ->
                            CompactInfoRow(
                                label = "Format Version",
                                value =
                                    "0x${version.toUByte().toString(16).uppercase().padStart(2, '0')}",
                            )
                        }
                        specVersion.basicVersion?.let { version ->
                            CompactInfoRow(
                                label = "Basic Version",
                                value = "${version.major}.${version.minor}",
                            )
                        }
                        specVersion.desOptionVersion?.let { version ->
                            CompactInfoRow(
                                label = "DES Option Version",
                                value = "${version.major}.${version.minor}",
                            )
                        }
                        specVersion.specialOptionVersion?.let { version ->
                            CompactInfoRow(
                                label = "Special Option Version",
                                value = "${version.major}.${version.minor}",
                            )
                        }
                        specVersion.extendedOverlapOptionVersion?.let { version ->
                            CompactInfoRow(
                                label = "Extended Overlap Option Version",
                                value = "${version.major}.${version.minor}",
                            )
                        }
                        specVersion.valueLimitedPurseServiceOptionVersion?.let { version ->
                            CompactInfoRow(
                                label = "Value-Limited Purse Service Option Version",
                                value = "${version.major}.${version.minor}",
                            )
                        }
                        specVersion.communicationWithMacOptionVersion?.let { version ->
                            CompactInfoRow(
                                label = "Communication with MAC Option Version",
                                value = "${version.major}.${version.minor}",
                            )
                        }
                    }
                }

                // Command Support Section
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                var commandSupportExpanded by remember { mutableStateOf(false) }
                val commandRotationAngle by
                    animateFloatAsState(
                        targetValue = if (commandSupportExpanded) 0f else 180f,
                        label = "commandSupportRotation",
                    )

                Row(
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            commandSupportExpanded = !commandSupportExpanded
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Command Support",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = if (commandSupportExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp).rotate(commandRotationAngle),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }

                if (commandSupportExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Basic Commands
                    Text(
                        text = "Basic Commands",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        CommandSupportChip("Polling", context.pollingSupport)
                        CommandSupportChip(
                            "Polling (System Code)",
                            context.pollingSystemCodeSupport,
                        )
                        CommandSupportChip(
                            "Polling (Communication Performance)",
                            context.pollingCommunicationPerformanceSupport,
                        )
                        CommandSupportChip("Request Response", context.requestResponseSupport)
                        CommandSupportChip("Request System Code", context.requestSystemCodeSupport)
                        CommandSupportChip(
                            "Request Specification",
                            context.requestSpecificationVersionSupport,
                        )
                        CommandSupportChip(
                            "Get Platform Info",
                            context.getPlatformInformationSupport,
                        )
                        CommandSupportChip("Reset Mode", context.resetModeSupport)
                    }

                    Text(
                        text = "Node enumeration commands",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        CommandSupportChip("Search Service Code", context.searchServiceCodeSupport)
                        CommandSupportChip("Request Code List", context.requestCodeListSupport)
                    }

                    Text(
                        text = "Node info commands",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        CommandSupportChip("Request Service", context.requestServiceSupport)
                        CommandSupportChip("Request Service V2", context.requestServiceV2Support)
                        CommandSupportChip(
                            "Request Block Info",
                            context.requestBlockInformationSupport,
                        )
                        CommandSupportChip(
                            "Request Block Info Ex",
                            context.requestBlockInformationExSupport,
                        )
                        CommandSupportChip(
                            "Get Node Property (MAC Communication)",
                            context.getNodePropertyMacCommunicationSupport,
                        )
                        CommandSupportChip(
                            "Get Node Property (Value Limited Service)",
                            context.getNodePropertyValueLimitedServiceSupport,
                        )
                    }

                    // Service Commands
                    Text(
                        text = "Block operation commands",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        CommandSupportChip(
                            "Read Without Encryption",
                            context.readBlocksWithoutEncryptionSupport,
                        )

                        context.maxBlocksPerRequest?.let { maxBlocks ->
                            InfoChip(label = "Max Blocks", value = maxBlocks.toString())
                        }
                        context.maxServicesPerRequest?.let { maxServices ->
                            InfoChip(label = "Max Services", value = maxServices.toString())
                        }
                        InfoChip(
                            label = "Error Mode",
                            value =
                                when (context.errorLocationIndication) {
                                    ErrorLocationIndication.FLAG -> "Flag"
                                    ErrorLocationIndication.INDEX -> "Index"
                                    ErrorLocationIndication.BITMASK -> "Bitmask"
                                },
                        )
                    }

                    // Container Commands
                    Text(
                        text = "Container Commands",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CommandSupportChip(
                            "Get Container Issue Info",
                            context.getContainerIssueInformationSupport,
                        )
                        CommandSupportChip("Get Container ID", context.getContainerIdSupport)
                    }

                    // Advanced Commands
                    Text(
                        text = "Advanced Commands",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        CommandSupportChip("Get System Status", context.getSystemStatusSupport)
                        CommandSupportChip("Set Parameter", context.setParameterSupport)
                        CommandSupportChip("Echo", context.echoSupport)
                    }
                }
            }
        }
    }
}

@Composable
private fun CommandSupportChip(
    label: String,
    support: CommandSupport,
    modifier: Modifier = Modifier,
) {
    val (displayText, isSuccessful, isWarning) =
        when (support) {
            CommandSupport.SUPPORTED -> Triple("✓ $label", true, false)
            CommandSupport.UNSUPPORTED -> Triple("✗ $label", false, true)
            CommandSupport.UNKNOWN -> Triple("? $label", false, false)
        }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color =
            when {
                isSuccessful -> MaterialTheme.colorScheme.secondaryContainer
                isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        modifier = modifier,
    ) {
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelSmall,
            color =
                when {
                    isSuccessful -> MaterialTheme.colorScheme.onSecondaryContainer
                    isWarning -> MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreeNodeCard(
    nodeInfo: NodeInformation,
    context: SystemScanContext,
    depth: Int,
    modifier: Modifier = Modifier,
) {
    val node = nodeInfo.node

    var isExpanded by remember { mutableStateOf(depth == 0) }
    val rotationAngle by
        animateFloatAsState(targetValue = if (isExpanded) 0f else 180f, label = "nodeRotation")

    val indentWidth = (depth * 8).dp
    val hasChildren = nodeInfo.hasChildren

    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        // Node header
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(start = indentWidth)
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Icon based on node type
            Icon(
                imageVector =
                    when (node) {
                        is System -> Icons.Default.Settings
                        is Area -> Icons.Default.AccountBox
                        is Service -> Icons.Default.Settings
                        else -> Icons.Default.Settings // fallback
                    },
                contentDescription = null,
                tint =
                    when (node) {
                        is System -> MaterialTheme.colorScheme.tertiary
                        is Area -> MaterialTheme.colorScheme.primary
                        is Service -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.onSurface // fallback
                    },
                modifier = Modifier.size(16.dp),
            )

            Column(modifier = Modifier.weight(1f)) {
                // Primary node information with inline attributes for services
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = getNodeDisplayText(node, context),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color =
                            when (node) {
                                is System -> MaterialTheme.colorScheme.tertiary
                                is Area -> MaterialTheme.colorScheme.primary
                                is Service -> MaterialTheme.colorScheme.secondary
                                else -> MaterialTheme.colorScheme.onSurface // fallback
                            },
                    )

                    // For services, show key attributes inline
                    if (node is Service) {
                        val service = node

                        // Authentication requirements - show AUTH/FREE first
                        if (service.attribute.authenticationRequired) {
                            AttributeChip("AUTH", isWarning = true, isCompact = true)
                        } else {
                            AttributeChip("FREE", isHighlight = true, isCompact = true)
                        }
                        if (service.attribute.pinRequired) {
                            AttributeChip("PIN", isWarning = true, isCompact = true)
                        }

                        // Service type and mode - handle READ_WRITE as special case
                        AttributeChip(service.attribute.type.name, isCompact = true)

                        // Split READ_WRITE into separate chips, simplify READ_ONLY to READ
                        when (service.attribute.mode.name) {
                            "READ_WRITE" -> {
                                AttributeChip("RW", isCompact = true)
                            }
                            "READ_ONLY" -> {
                                AttributeChip("RO", isCompact = true)
                            }
                            else -> {
                                AttributeChip(service.attribute.mode.name, isCompact = true)
                            }
                        }
                    }

                    // For areas, show CAN CREATE SUBAREA inline
                    if (node is Area) {
                        val area = node
                        if (area.endAttribute == AreaAttribute.END_ROOT_AREA) {
                            AttributeChip("ROOT", isHighlight = true, isCompact = true)
                        }
                        if (area.attribute.canCreateSubArea) {
                            AttributeChip("SUBAREA ALLOWED", isHighlight = true, isCompact = true)
                        }
                    }
                }

                // Key versions for services (displayed under the main header)
                val hasKeyInfo =
                    if (context.encryptionIdentifier != null) {
                        context.nodeAesKeyVersions[node] != null ||
                            context.nodeDesKeyVersions[node] != null
                    } else {
                        context.nodeKeyVersions[node] != null ||
                            context.nodeAesKeyVersions[node] != null ||
                            context.nodeDesKeyVersions[node] != null
                    }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    if (hasKeyInfo) {
                        if (context.encryptionIdentifier != null) {
                            // Display only AES and DES with "KEYTYPE: VERSION" format
                            context.nodeAesKeyVersions[node]?.let {
                                AttributeChip(
                                    "${context.encryptionIdentifier.aesKeyType.name}: v${it.toInt()}",
                                    isInfo = true,
                                )
                            }
                            context.nodeDesKeyVersions[node]?.let {
                                AttributeChip(
                                    "${context.encryptionIdentifier.desKeyType.name}: v${it.toInt()}",
                                    isInfo = true,
                                )
                            }
                        } else {
                            // Original display logic
                            context.nodeKeyVersions[node]?.let {
                                AttributeChip("KEY v${it.toInt()}", isInfo = true)
                            }
                            context.nodeAesKeyVersions[node]?.let {
                                AttributeChip("AES v${it.toInt()}", isInfo = true)
                            }
                            context.nodeDesKeyVersions[node]?.let {
                                AttributeChip("DES v${it.toInt()}", isInfo = true)
                            }
                        }
                    }

                    // Area and System attribute chips (below the main text) - only remaining
                    // attributes
                    if (node !is Service) {
                        AttributeChip("Areas: ${nodeInfo.immediateAreaCount}", isInfo = true)
                        AttributeChip("Services: ${nodeInfo.immediateServiceCount}", isInfo = true)
                    }
                }
            }

            // Expand/collapse arrow
            if (hasChildren || hasNodeDetails(node, context)) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(16.dp).rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }

        // Expanded content
        if (isExpanded) {
            // Node details
            if (hasNodeDetails(node, context)) {
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(start = indentWidth + 24.dp)
                            .padding(bottom = 4.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        NodeDetailsContent(nodeInfo, context)
                    }
                }
            }

            // Children
            nodeInfo.children.forEach { childInfo ->
                TreeNodeCard(nodeInfo = childInfo, context = context, depth = depth + 1)
            }
        }
    }
}

@Composable
private fun NodeDetailsContent(nodeInfo: NodeInformation, context: SystemScanContext) {
    val node = nodeInfo.node
    // System-specific information
    if (node is System) {
        context.idm?.let { idm -> CompactInfoRow(label = "IDM", value = idm.toHexString()) }

        context.systemStatus?.let { systemStatus ->
            CompactInfoRow(label = "System Status", value = systemStatus.toHexString())
        }

        // Node statistics - Areas and Services side by side
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total Areas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = context.nodes.filterIsInstance<Area>().size.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total Services",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = context.nodes.filterIsInstance<Service>().size.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        // Key information - display "Key Versions" only if AES and DES are empty
        val hasAesKeys = context.nodeAesKeyVersions.isNotEmpty()
        val hasDesKeys = context.nodeDesKeyVersions.isNotEmpty()
        val hasGenericKeys = context.nodeKeyVersions.isNotEmpty()

        if (hasGenericKeys && !hasAesKeys && !hasDesKeys) {
            CompactInfoRow(label = "Key Versions", value = context.nodeKeyVersions.size.toString())
        } else if (hasAesKeys || hasDesKeys) {
            // Display AES and DES side by side if both are available
            if (hasAesKeys && hasDesKeys) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total AES Keys",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = context.nodeAesKeyVersions.size.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total DES Keys",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = context.nodeDesKeyVersions.size.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            } else {
                // Display individually if only one type is available
                if (hasAesKeys) {
                    CompactInfoRow(
                        label = "AES Keys",
                        value = context.nodeAesKeyVersions.size.toString(),
                    )
                }
                if (hasDesKeys) {
                    CompactInfoRow(
                        label = "DES Keys",
                        value = context.nodeDesKeyVersions.size.toString(),
                    )
                }
            }
        }
    }

    // Block Information
    context.nodeBlockCounts[node]?.let { blockCount ->
        if (!blockCount.isInvalid) {
            CompactInfoRow(label = "Block Count", value = blockCount.toInt().toString())
        }
    }

    context.nodeAssignedBlockCounts[node]?.let { assignedCount ->
        if (!assignedCount.isInvalid) {
            CompactInfoRow(label = "Assigned Blocks", value = assignedCount.toInt().toString())
        }
    }

    context.nodeFreeBlockCounts[node]?.let { freeCount ->
        if (!freeCount.isInvalid) {
            CompactInfoRow(label = "Free Blocks", value = freeCount.toInt().toString())
        }
    }

    // Properties
    context.nodeValueLimitedPurseProperties[node]?.let { purseProperty ->
        CompactInfoRow(
            label = "Value-Limited Purse",
            value = if (purseProperty.enabled) "Enabled" else "Disabled",
        )
        if (purseProperty.enabled) {
            CompactInfoRow(label = "Upper Limit", value = purseProperty.upperLimit.toString())
            CompactInfoRow(label = "Lower Limit", value = purseProperty.lowerLimit.toString())
        }
    }

    context.nodeMacCommunicationProperties[node]?.let { macProperty ->
        CompactInfoRow(
            label = "MAC Communication",
            value = if (macProperty.enabled) "Enabled" else "Disabled",
        )
    }

    // Block Data
    context.serviceBlockData[node]?.let { blockData ->
        if (blockData.isNotEmpty()) {
            val blockCount = blockData.size / 16
            CompactInfoRow(
                label = "Block Data",
                value = "$blockCount blocks (${blockData.size} bytes)",
            )

            // Display each block (16 bytes) on a separate line
            for (blockIndex in 0 until blockCount) {
                val blockStart = blockIndex * 16
                val blockEnd = minOf(blockStart + 16, blockData.size)
                val blockBytes = blockData.sliceArray(blockStart until blockEnd)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${blockIndex.toString().padStart(3, '0')}:",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(30.dp),
                    )
                    Text(
                        text = blockBytes.toHexString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactInfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    isSuccessful: Boolean = false,
    isHighlight: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        when {
            isSuccessful -> MaterialTheme.colorScheme.primaryContainer
            isHighlight -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

    val contentColor =
        when {
            isSuccessful -> MaterialTheme.colorScheme.onPrimaryContainer
            isHighlight -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(shape = RoundedCornerShape(6.dp), color = backgroundColor, modifier = modifier) {
        Text(
            text = "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun AttributeChip(
    text: String,
    isHighlight: Boolean = false,
    isWarning: Boolean = false,
    isInfo: Boolean = false,
    isSuccessful: Boolean = false,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val backgroundColor =
        when {
            isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            isHighlight -> MaterialTheme.colorScheme.primaryContainer
            isSuccessful -> MaterialTheme.colorScheme.secondaryContainer
            isInfo -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

    val contentColor =
        when {
            isWarning -> MaterialTheme.colorScheme.onErrorContainer
            isHighlight -> MaterialTheme.colorScheme.onPrimaryContainer
            isSuccessful -> MaterialTheme.colorScheme.onSecondaryContainer
            isInfo -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }

    val cornerRadius = if (isCompact) 3.dp else 4.dp
    val horizontalPadding = if (isCompact) 3.dp else 4.dp
    val verticalPadding = if (isCompact) 0.5.dp else 1.dp

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor,
        modifier = modifier,
    ) {
        Text(
            text = text,
            style =
                if (isCompact)
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                    )
                else MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
        )
    }
}

private fun getNodeDisplayText(node: Node, context: SystemScanContext): String {
    return when (node) {
        is Area -> {
            val area = node
            val baseText = "Area ${area.fullCode.toHexString()} (${area.number}-${area.endNumber})"
            val areaName = NodeNaming.getAreaName(area, context)
            if (areaName != null) {
                "$baseText - $areaName"
            } else {
                baseText
            }
        }
        is Service -> {
            val service = node
            "Service ${service.fullCode.toHexString()} (#${service.number})"
        }
        is System -> {
            // Display system code from context instead of node code
            val systemCode = context.systemCode?.toHexString()
            val baseText =
                if (systemCode != null) {
                    "System $systemCode"
                } else {
                    "System (No Code)"
                }
            val systemName = NodeNaming.getSystemName(node, context)
            if (systemName != null) {
                "$baseText - $systemName"
            } else {
                baseText
            }
        }
        else -> "Unknown Node"
    }
}

private fun hasNodeDetails(node: Node, context: SystemScanContext): Boolean {
    return context.nodeBlockCounts[node] != null ||
        context.nodeAssignedBlockCounts[node] != null ||
        context.nodeFreeBlockCounts[node] != null ||
        context.nodeValueLimitedPurseProperties[node] != null ||
        context.nodeMacCommunicationProperties[node] != null ||
        context.serviceBlockData[node] != null ||
        (node is System && (context.idm != null || context.systemStatus != null))
}
