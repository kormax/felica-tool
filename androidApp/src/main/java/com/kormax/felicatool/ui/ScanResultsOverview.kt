package com.kormax.felicatool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.service.CardScanRunner
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.ui.components.CardInformationSection
import com.kormax.felicatool.ui.components.GroupedServicesSection
import com.kormax.felicatool.ui.components.TreeNodeCard
import com.kormax.felicatool.ui.components.buildNodeTree
import com.kormax.felicatool.util.ExportUtils
import com.kormax.felicatool.util.NodeDefinitionType
import com.kormax.felicatool.util.NodeRegistry
import com.kormax.felicatool.util.ServiceIconMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultsOverview(
    cardScanRunner: CardScanRunner,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showExportMenu by remember { mutableStateOf(false) }
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
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showExportMenu = true }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Export")
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = privacyMode, onCheckedChange = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Privacy mode")
                                }
                            },
                            onClick = { privacyMode = !privacyMode },
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Export scan results as JSON") },
                            onClick = {
                                showExportMenu = false
                                ExportUtils.exportFlatList(
                                    context,
                                    cardScanRunner.getScanContext(),
                                    privacyMode,
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Export communication log as JSON") },
                            onClick = {
                                showExportMenu = false
                                ExportUtils.exportCommunicationLog(
                                    context,
                                    cardScanRunner.getScanContext().communicationLog,
                                    privacyMode,
                                )
                            },
                        )
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
        val scanContext = cardScanRunner.getScanContext()
        val contentModifier = Modifier.padding(innerPadding)

        LazyColumn(
            modifier = contentModifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Card Information Section
            item { CardInformationSection(context = scanContext) }

            // View mode tabs
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !useGroupedView,
                        onClick = { useGroupedView = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    ) {
                        Text("Hierarchy")
                    }
                    SegmentedButton(
                        selected = useGroupedView,
                        onClick = { useGroupedView = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
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
                            SystemHeader(systemScanContext = systemScanContext)

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
private fun SystemHeader(systemScanContext: SystemScanContext, modifier: Modifier = Modifier) {
    val systemCodeHex =
        systemScanContext.systemCode
            ?.let { it.joinToString("") { byte -> "%02X".format(byte) } }
            ?.uppercase() ?: "Unknown"

    // Get system name from registry
    val systemName =
        remember(systemCodeHex) {
            NodeRegistry.getNodeName(systemCodeHex, systemCodeHex, NodeDefinitionType.SYSTEM)
        }

    // Get provider icons
    val providerIconResIds =
        remember(systemCodeHex) {
            NodeRegistry.getSystemProviders(systemCodeHex).mapNotNull {
                ServiceIconMapper.iconFor(it)
            }
        }

    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = 8.dp),
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
                "System $systemCodeHex"
            }

        Text(
            text = displayText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
