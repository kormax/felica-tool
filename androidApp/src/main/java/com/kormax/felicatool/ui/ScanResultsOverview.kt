package com.kormax.felicatool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.AppBarMenu
import com.kormax.felicatool.AppBarMenuGroup
import com.kormax.felicatool.CheckableMenuOption
import com.kormax.felicatool.R
import com.kormax.felicatool.service.CardScanService
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.ui.components.CardInformationSection
import com.kormax.felicatool.ui.components.GroupedServicesSection
import com.kormax.felicatool.ui.components.TreeNodeCard
import com.kormax.felicatool.ui.components.buildNodeTree
import com.kormax.felicatool.util.ExportUtils
import com.kormax.felicatool.util.NodeDefinitionType
import com.kormax.felicatool.util.NodeRegistry
import com.kormax.felicatool.util.ServiceIconMapper
import com.kormax.felicatool.util.maskIdi
import com.kormax.felicatool.util.maskIdm
import com.kormax.felicatool.util.maskPmi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultsOverview(
    cardScanService: CardScanService,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var privacyMode by remember { mutableStateOf(false) }
    var useGroupedView by remember { mutableStateOf(false) }

    // Handle system back button
    BackHandler { onBackPressed() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Scan overview") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    AppBarMenu(icon = Icons.Default.Share, label = "Export") { dismiss ->
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                            CheckableMenuOption(
                                label = "Privacy mode",
                                icon = Icons.Filled.Lock,
                                checked = privacyMode,
                                onCheckedChange = { privacyMode = it },
                                // This single row follows the outline of the first of two groups.
                                shapes =
                                    MenuDefaults.itemShapes(
                                        shape = MenuDefaults.leadingItemShape,
                                        selectedShape = MenuDefaults.leadingItemShape,
                                    ),
                            )
                        }
                        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                            DropdownMenuItem(
                                text = { Text("Export scan results as JSON") },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_file_export),
                                        contentDescription = null,
                                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                    )
                                },
                                shape = MenuDefaults.leadingItemShape,
                                onClick = {
                                    dismiss()
                                    ExportUtils.exportFlatList(
                                        context,
                                        cardScanService.getScanContext(),
                                        privacyMode,
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export communication log as JSON") },
                                leadingIcon = {
                                    Icon(
                                        painterResource(R.drawable.ic_file_export),
                                        contentDescription = null,
                                        modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                    )
                                },
                                shape = MenuDefaults.trailingItemShape,
                                onClick = {
                                    dismiss()
                                    ExportUtils.exportCommunicationLog(
                                        context,
                                        cardScanService.getScanContext().communicationLog,
                                        privacyMode,
                                    )
                                },
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
    ) { innerPadding ->
        // Node data display
        val scanContext = cardScanService.getScanContext()
        val contentModifier = Modifier.padding(innerPadding)

        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Card Information Section
            item { CardInformationSection(context = scanContext, privacyMode = privacyMode) }

            // View mode tabs
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement =
                        Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                ) {
                    ToggleButton(
                        checked = !useGroupedView,
                        onCheckedChange = { useGroupedView = false },
                        modifier =
                            Modifier.weight(1f).semantics {
                                role = Role.RadioButton
                                selected = !useGroupedView
                            },
                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    ) {
                        Text("Hierarchy")
                    }
                    ToggleButton(
                        checked = useGroupedView,
                        onCheckedChange = { useGroupedView = true },
                        modifier =
                            Modifier.weight(1f).semantics {
                                role = Role.RadioButton
                                selected = useGroupedView
                            },
                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    ) {
                        Text("Grouped")
                    }
                }
            }

            item(key = "nodes_$useGroupedView") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (useGroupedView) {
                        // Grouped Services View - services grouped by service number, per system
                        scanContext.systemScanContexts.forEachIndexed { index, systemScanContext ->
                            if (index > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                            }

                            // System header with icon and name
                            SystemHeader(
                                systemScanContext = systemScanContext,
                                privacyMode = privacyMode,
                            )

                            GroupedServicesSection(context = systemScanContext)
                        }
                    } else {
                        // Hierarchical Nodes Section - Tree-like structure
                        // Display the tree recursively for all systems
                        scanContext.systemScanContexts.forEach { systemScanContext ->
                            val nodeTree =
                                remember(scanContext) { buildNodeTree(systemScanContext) }
                            nodeTree.forEach { nodeInfo ->
                                TreeNodeCard(
                                    nodeInfo = nodeInfo,
                                    context = systemScanContext,
                                    depth = 0,
                                    privacyMode = privacyMode,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** System header component for grouped view, showing system code, name, and provider icons. */
@Composable
private fun SystemHeader(
    systemScanContext: SystemScanContext,
    privacyMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val systemCodeHex =
        systemScanContext.systemCode
            ?.let { it.joinToString("") { byte -> "%02X".format(byte) } }
            ?.uppercase()

    // Get system name from registry
    val systemName =
        remember(systemCodeHex) {
            systemCodeHex?.let {
                NodeRegistry.getNodeName(it, it, NodeDefinitionType.SYSTEM)
            }
        }

    // Get provider icons
    val providerIconResIds =
        remember(systemCodeHex) {
            systemCodeHex
                ?.let { NodeRegistry.getSystemProviders(it) }
                .orEmpty()
                .mapNotNull { ServiceIconMapper.iconFor(it) }
        }

    Column(modifier = modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Provider icons
            if (providerIconResIds.isNotEmpty()) {
                providerIconResIds.forEach { iconRes ->
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = Color.Unspecified,
                        )
                    }
                }
            }

            // System text
            val displayText =
                if (systemName != null) {
                    "System $systemCodeHex - $systemName"
                } else {
                    "System ${systemCodeHex ?: "Unknown"}"
                }

            Text(
                text = displayText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        GroupedSystemInformationCard(systemScanContext, privacyMode)
    }
}

@Composable
private fun GroupedSystemInformationCard(
    systemScanContext: SystemScanContext,
    privacyMode: Boolean,
) {
    if (
        systemScanContext.idm == null &&
            systemScanContext.idi == null &&
            systemScanContext.pmi == null
    ) {
        return
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            systemScanContext.idm?.let { idm ->
                GroupedSystemInfoRow(
                    label = "IDm",
                    value = if (privacyMode) maskIdm(idm) else idm.toHexString().uppercase(),
                )
            }
            systemScanContext.idi?.let { idi ->
                GroupedSystemInfoRow(
                    label = "IDi",
                    value =
                        if (privacyMode) maskIdi(idi.toHexString().uppercase())
                        else idi.toHexString().uppercase(),
                )
            }
            systemScanContext.pmi?.let { pmi ->
                GroupedSystemInfoRow(
                    label = "PMi",
                    value =
                        if (privacyMode) maskPmi(pmi.toHexString().uppercase())
                        else pmi.toHexString().uppercase(),
                )
            }
        }
    }
}

@Composable
private fun GroupedSystemInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}
