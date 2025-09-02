package com.kormax.felicatool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.service.CardScanService
import com.kormax.felicatool.ui.components.CardInformationSection
import com.kormax.felicatool.ui.components.TreeNodeCard
import com.kormax.felicatool.ui.components.buildNodeTree

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanResultsOverview(
    cardScanService: CardScanService,
    onBackPressed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Handle system back button
    BackHandler { onBackPressed() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Scan overview") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
    ) { innerPadding ->
        // Node data display
        val context = cardScanService.getScanContext()
        val modifier = Modifier.padding(innerPadding)

        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Card Information Section
            item { CardInformationSection(context = context) }

            item {
                // Hierarchical Nodes Section - Tree-like structure
                // Display the tree recursively for all systems
                context.systemScanContexts.forEach { systemScanContext ->
                    val nodeTree = remember(context) { buildNodeTree(systemScanContext) }
                    nodeTree.forEach { nodeInfo ->
                        TreeNodeCard(nodeInfo = nodeInfo, context = systemScanContext, depth = 0)
                    }
                }
            }
        }
    }
}
