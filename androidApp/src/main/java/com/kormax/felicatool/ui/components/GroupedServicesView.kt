package com.kormax.felicatool.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.AreaAttribute
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.felica.ServiceAttribute
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.util.NodeDefinitionType
import com.kormax.felicatool.util.NodeRegistry
import com.kormax.felicatool.util.ServiceGrouper
import com.kormax.felicatool.util.ServiceIconMapper

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

/**
 * Displays services grouped by their service number. Services with the same number share the same
 * block data and are shown together. Groups are organized by their containing area.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupedServicesSection(
    context: SystemScanContext,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    // Use context-aware grouping that considers parent areas
    val groups = remember(context) { ServiceGrouper.groupServices(context) }
    val areaHeaderGroupsByArea = remember(context) { buildAreaHeaderGroups(context) }

    Column(modifier = modifier.fillMaxWidth().padding(start = 8.dp)) {
        if (groups.isEmpty()) {
            Text(
                text = "No services found (${context.nodes.size} nodes total)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            var previousHeader: AreaHeaderGroup? = null
            groups.forEach { group ->
                val headerGroup = group.parentArea?.let { areaHeaderGroupsByArea[it] }

                if (headerGroup != null && headerGroup != previousHeader) {
                    AreaGroupHeader(headerGroup = headerGroup, context = context, depth = depth)
                }

                ServiceGroupCard(
                    group = group,
                    context = context,
                    depth = if (headerGroup != null) depth + 1 else depth,
                )

                previousHeader = headerGroup
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaGroupHeader(
    headerGroup: AreaHeaderGroup,
    context: SystemScanContext,
    depth: Int,
    modifier: Modifier = Modifier,
) {
    val indentWidth = (depth * 6).dp
    val providerIconResIds =
        remember(context.systemCode, headerGroup) {
            resolveAreaGroupProviderIcons(headerGroup, context)
        }
    val areaName =
        remember(context.systemCode, headerGroup) { getAreaGroupName(headerGroup, context) }
    val areaCodesText =
        remember(headerGroup) {
            headerGroup.areas.joinToString(separator = " / ") {
                it.fullCode.toHexString().uppercase()
            }
        }
    val baseTitle =
        if (headerGroup.areas.size > 1) {
            "Area Group ${headerGroup.number}-${headerGroup.endNumber}"
        } else {
            "Area ${headerGroup.number}-${headerGroup.endNumber}"
        }
    val title = if (areaName != null) "$baseTitle - $areaName" else baseTitle

    Row(
        modifier = modifier.fillMaxWidth().padding(start = indentWidth).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (providerIconResIds.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(modifier = Modifier.height(20.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        painter = painterResource(id = providerIconResIds.first()),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.Unspecified,
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Default.AccountBox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )

                if (headerGroup.isRoot) {
                    ServiceGroupChip(text = "ROOT", isHighlight = true)
                }
                if (headerGroup.hasUnknownAttribute) {
                    ServiceGroupChip(text = "SA?", isWarning = true)
                }
            }

            Text(
                text = areaCodesText,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** Card displaying a group of services that share the same service number. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceGroupCard(
    group: ServiceGrouper.ServiceGroup,
    context: SystemScanContext,
    modifier: Modifier = Modifier,
    depth: Int = 0,
) {
    var isExpanded by remember { mutableStateOf(false) }
    val rotationAngle by
        animateFloatAsState(targetValue = if (isExpanded) 0f else 180f, label = "groupRotation")

    val indentWidth = (depth * 6).dp

    // Use the group's own parentArea for provider resolution
    val parentArea = group.parentArea

    // Get provider icons for the primary service
    val providerIconResIds =
        remember(group.primaryService, context.systemCode, parentArea) {
            resolveServiceGroupProviderIcons(group, context, parentArea)
        }

    // Get service name from registry (using primary service and parent area)
    val serviceName =
        remember(group.primaryService, context.systemCode, parentArea) {
            getServiceGroupName(group, context)
        }

    // Determine if any service is hidden
    val hasHiddenServices = group.services.any { context.hiddenNodes.contains(it) }

    Column(modifier = modifier.fillMaxWidth().animateContentSize()) {
        // Group header
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(start = indentWidth)
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Icon based on provider or default
            if (providerIconResIds.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(modifier = Modifier.height(20.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            painter = painterResource(id = providerIconResIds.first()),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.Unspecified,
                        )
                    }
                }
            } else {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(16.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Primary information with service group header
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // Display service group title
                    val titleText =
                        if (serviceName != null) {
                            "Service #${group.number} - $serviceName"
                        } else {
                            "Service #${group.number}"
                        }

                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary,
                    )

                    // Show service type (same for all services in group)
                    ServiceGroupChip(text = group.type.name, isInfo = true)

                    // Show HIDDEN if any service is hidden
                    if (hasHiddenServices) {
                        ServiceGroupChip(text = "HIDDEN", isWarning = true)
                    }
                }

                // Service variants summary row
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    group.services.forEach { service ->
                        ServiceVariantChip(service = service, context = context)
                    }
                }
            }

            // Expand/collapse arrow - always show since expanded view has service details
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier.size(16.dp).rotate(rotationAngle),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }

        // Expanded content
        if (isExpanded) {
            Card(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(start = indentWidth + 24.dp)
                        .padding(bottom = 4.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Show detailed service codes
                    Text(
                        text = "Service Codes",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    group.services.forEach { service ->
                        ServiceDetailRow(service = service, context = context)
                    }

                    // Show shared block data (from any service that has it)
                    val blockDataService =
                        group.services.firstOrNull {
                            context.serviceBlockData[it]?.isNotEmpty() == true
                        }
                    blockDataService?.let { service ->
                        context.serviceBlockData[service]?.let { blockDataMap ->
                            if (blockDataMap.isNotEmpty()) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                Text(
                                    text = "Shared Block Data",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                val blockCount = blockDataMap.size
                                val totalBytes = blockCount * 16
                                Text(
                                    text = "$blockCount blocks ($totalBytes bytes)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                // Look up extra block names
                                val systemCodeHex = context.systemCode?.toHexString()?.uppercase()
                                val nodeCodeHex = service.code.toHexString().uppercase()
                                val extraBlockNames =
                                    if (systemCodeHex != null) {
                                        NodeRegistry.getExtraBlocks(systemCodeHex, nodeCodeHex)
                                    } else {
                                        emptyMap()
                                    }

                                // Display each block
                                for ((blockNumber, blockBytes) in
                                    blockDataMap.entries.sortedBy { it.key }) {
                                    val blockNumberHex =
                                        blockNumber.toString(16).uppercase().padStart(4, '0')
                                    val blockName = extraBlockNames[blockNumber]

                                    if (blockName != null) {
                                        Text(
                                            text = blockName,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 4.dp),
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = "$blockNumberHex:",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                }
            }
        }
    }
}

/** Compact chip showing a service variant (access mode and type). */
@Composable
private fun ServiceVariantChip(
    service: Service,
    context: SystemScanContext,
    modifier: Modifier = Modifier,
) {
    val isHidden = context.hiddenNodes.contains(service)
    val attribute = service.attribute

    val modeText =
        when (attribute.mode.name) {
            "READ_WRITE" -> "RW"
            "READ_ONLY" -> "RO"
            "CASHBACK" -> "CB"
            "DECREMENT" -> "DEC"
            else -> attribute.mode.name.take(3)
        }

    val backgroundColor =
        when {
            attribute is ServiceAttribute.Unknown ->
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            isHidden -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            attribute.authenticationRequired -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        }

    val contentColor =
        when {
            attribute is ServiceAttribute.Unknown -> MaterialTheme.colorScheme.onErrorContainer
            isHidden -> MaterialTheme.colorScheme.onErrorContainer
            attribute.authenticationRequired -> MaterialTheme.colorScheme.onSurfaceVariant
            else -> MaterialTheme.colorScheme.onPrimaryContainer
        }

    Surface(shape = RoundedCornerShape(3.dp), color = backgroundColor, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
        ) {
            // Service code in hex
            Text(
                text = service.code.toHexString().uppercase(),
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                    ),
                fontFamily = FontFamily.Monospace,
                color = contentColor,
            )

            Spacer(modifier = Modifier.width(2.dp))

            // Auth icon - Lock for AUTH, Check for FREE
            Icon(
                imageVector =
                    if (attribute.authenticationRequired) Icons.Default.Lock
                    else Icons.Default.Check,
                contentDescription =
                    if (attribute.authenticationRequired) "Auth required" else "No auth",
                modifier = Modifier.size(10.dp),
                tint = contentColor,
            )

            // PIN icon - Star when PIN is required
            if (attribute.pinRequired) {
                Spacer(modifier = Modifier.width(1.dp))
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "PIN required",
                    modifier = Modifier.size(10.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(modifier = Modifier.width(2.dp))

            // Mode text
            Text(
                text = modeText,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                    ),
                fontFamily = FontFamily.Monospace,
                color = contentColor,
            )
        }
    }
}

/** Detailed row showing a single service with its full code and properties. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceDetailRow(
    service: Service,
    context: SystemScanContext,
    modifier: Modifier = Modifier,
) {
    val isHidden = context.hiddenNodes.contains(service)
    val attribute = service.attribute

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Service code
        Text(
            text = service.code.toHexString().uppercase(),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            color =
                if (isHidden) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )

        // Attribute chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (isHidden) {
                ServiceGroupChip(text = "HIDDEN", isWarning = true, isCompact = true)
            }

            if (attribute is ServiceAttribute.Unknown) {
                ServiceGroupChip(text = "UNKNOWN", isWarning = true, isCompact = true)
            } else {
                // Auth status as text in detailed view
                if (attribute.authenticationRequired) {
                    ServiceGroupChip(text = "AUTH", isWarning = true, isCompact = true)
                } else {
                    ServiceGroupChip(text = "FREE", isHighlight = true, isCompact = true)
                }
                if (attribute.pinRequired) {
                    ServiceGroupChip(text = "PIN", isWarning = true, isCompact = true)
                }
                // Service type is omitted here since it's the same for all services in the group
                when (attribute.mode.name) {
                    "READ_WRITE" -> ServiceGroupChip(text = "RW", isCompact = true)
                    "READ_ONLY" -> ServiceGroupChip(text = "RO", isCompact = true)
                    else -> ServiceGroupChip(text = attribute.mode.name, isCompact = true)
                }
            }

            // Key versions
            context.nodeKeyVersions[service]?.let {
                ServiceGroupChip(text = "KEY v${it.toInt()}", isInfo = true, isCompact = true)
            }
            context.nodeAesKeyVersions[service]?.let {
                ServiceGroupChip(text = "AES v${it.toInt()}", isInfo = true, isCompact = true)
            }
            context.nodeDesKeyVersions[service]?.let {
                ServiceGroupChip(text = "DES v${it.toInt()}", isInfo = true, isCompact = true)
            }

            // VLPS chip if enabled
            context.nodeValueLimitedPurseProperties[service]?.let { purseProperty ->
                if (purseProperty.enabled) {
                    ServiceGroupChip(text = "VLPS", isInfo = true, isCompact = true)
                }
            }

            // MAC chip if enabled
            context.nodeMacCommunicationProperties[service]?.let { macProperty ->
                if (macProperty.enabled) {
                    ServiceGroupChip(text = "MAC", isInfo = true, isCompact = true)
                }
            }

            // Block count chip
            val blockCount = context.nodeBlockCounts[service]?.takeUnless { it.isInvalid }?.toInt()
            val assignedCount =
                context.nodeAssignedBlockCounts[service]?.takeUnless { it.isInvalid }?.toInt()
            val freeCount =
                context.nodeFreeBlockCounts[service]?.takeUnless { it.isInvalid }?.toInt()
            if (blockCount != null || assignedCount != null || freeCount != null) {
                val blockText =
                    if (assignedCount != null && freeCount != null) {
                        "Blocks: $freeCount/$assignedCount"
                    } else {
                        "Blocks: ${blockCount ?: 0}"
                    }
                ServiceGroupChip(text = blockText, isInfo = true, isCompact = true)
            }
        }
    }
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
                AreaHeaderGroupKey(number = topLevelArea.number, endNumber = topLevelArea.endNumber)
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

private fun resolveAreaGroupProviderIcons(
    headerGroup: AreaHeaderGroup,
    context: SystemScanContext,
): List<Int> {
    val systemCode = context.systemCode?.toHexString()?.uppercase() ?: return emptyList()
    val parentCode = headerGroup.parentArea?.fullCode?.toHexString()?.uppercase()

    fun resolveIcons(area: Area): List<Int> {
        val areaCode = area.fullCode.toHexString().uppercase()
        val providers =
            NodeRegistry.getProvidersForNode(
                systemCode,
                areaCode,
                parentCode,
                NodeDefinitionType.AREA,
            )
        return providers.mapNotNull { ServiceIconMapper.iconFor(it) }
    }

    return resolveInAreaGroup(headerGroup) { area -> resolveIcons(area).takeIf { it.isNotEmpty() } }
        ?: emptyList()
}

private fun getAreaGroupName(headerGroup: AreaHeaderGroup, context: SystemScanContext): String? {
    val systemCode = context.systemCode?.toHexString()?.uppercase() ?: return null
    val parentCode = headerGroup.parentArea?.fullCode?.toHexString()?.uppercase()

    fun resolveName(area: Area): String? {
        val areaCode = area.fullCode.toHexString().uppercase()
        return NodeRegistry.getNodeName(systemCode, areaCode, parentCode, NodeDefinitionType.AREA)
    }

    return resolveInAreaGroup(headerGroup, ::resolveName)
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

private fun findImmediateParentArea(area: Area, areas: List<Area>): Area? {
    return areas
        .filter { candidate ->
            candidate != area &&
                area.number >= candidate.number &&
                area.endNumber <= candidate.endNumber &&
                // Strict containment avoids same-range variants becoming parent/child.
                (candidate.number < area.number || candidate.endNumber > area.endNumber)
        }
        .minWithOrNull(compareBy<Area>({ it.endNumber - it.number }, { it.number }))
}

@Composable
private fun ServiceGroupChip(
    text: String,
    modifier: Modifier = Modifier,
    isHighlight: Boolean = false,
    isWarning: Boolean = false,
    isInfo: Boolean = false,
    isCompact: Boolean = false,
) {
    val backgroundColor =
        when {
            isWarning -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            isHighlight -> MaterialTheme.colorScheme.primaryContainer
            isInfo -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        }

    val contentColor =
        when {
            isWarning -> MaterialTheme.colorScheme.onErrorContainer
            isHighlight -> MaterialTheme.colorScheme.onPrimaryContainer
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
                if (isCompact) {
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.85f
                    )
                } else {
                    MaterialTheme.typography.labelSmall
                },
            color = contentColor,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
        )
    }
}

private fun resolveServiceGroupProviderIcons(
    group: ServiceGrouper.ServiceGroup,
    context: SystemScanContext,
    parentArea: Area?,
): List<Int> {
    val systemCode = context.systemCode?.toHexString()?.uppercase() ?: return emptyList()

    // Try to get provider from any service in the group
    for (service in group.services) {
        val serviceCode = service.fullCode.toHexString().uppercase()
        val parentCode = parentArea?.fullCode?.toHexString()?.uppercase()

        val providers =
            NodeRegistry.getProvidersForNode(
                systemCode,
                serviceCode,
                parentCode,
                NodeDefinitionType.SERVICE,
                blockData = context.serviceBlockData[service],
            )

        val icons = providers.mapNotNull { ServiceIconMapper.iconFor(it) }
        if (icons.isNotEmpty()) {
            return icons
        }
    }

    return emptyList()
}

private fun getServiceGroupName(
    group: ServiceGrouper.ServiceGroup,
    context: SystemScanContext,
): String? {
    val systemCode = context.systemCode?.toHexString()?.uppercase() ?: return null
    val parentCode = group.parentArea?.fullCode?.toHexString()?.uppercase()

    // Try to get name from any service in the group, using the group's parent area
    for (service in group.services) {
        val serviceCode = service.code.toHexString().uppercase()
        val name =
            NodeRegistry.getNodeName(
                systemCode,
                serviceCode,
                parentCode,
                NodeDefinitionType.SERVICE,
                blockData = context.serviceBlockData[service],
            )
        if (name != null) {
            return name
        }
    }

    return null
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = "") { byte -> "%02X".format(byte) }
}
