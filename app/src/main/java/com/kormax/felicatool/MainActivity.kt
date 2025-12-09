/*
 * Copyright 2025 kormax
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kormax.felicatool

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kormax.felicatool.felica.AndroidFeliCaTarget
import com.kormax.felicatool.service.CardScanService
import com.kormax.felicatool.service.ScanSettings
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.ScanResultsOverview
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.ui.components.StepsList
import com.kormax.felicatool.ui.theme.FeliCaToolTheme
import com.kormax.felicatool.util.NodeRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var nfcAdapter: NfcAdapter? = null
    private val cardScanService = CardScanService()

    private var steps by mutableStateOf(emptyList<CardScanStep>())
    private var isCardPresent by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Place a FeliCa card near the device")
    private var showComprehensiveData by mutableStateOf(false)
    private var scanSettings by mutableStateOf(ScanSettings())

    private fun toggleStepCollapse(stepId: String) {
        if (stepId == "scan_overview") {
            showComprehensiveData = true
            return
        }

        steps =
            steps.map { step ->
                if (step.id == stepId) {
                    step.copy(isCollapsed = !step.isCollapsed)
                } else {
                    step
                }
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize NodeRegistry at startup
        lifecycleScope.launch(Dispatchers.IO) { NodeRegistry.ensureInitialized(applicationContext) }

        setupNFC()

        setContent {
            FeliCaToolTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main screen - always present
                    MainScreen(
                        steps = steps,
                        isCardPresent = isCardPresent,
                        statusMessage = statusMessage,
                        onToggleCollapse = ::toggleStepCollapse,
                        scanSettings = scanSettings,
                        onScanSettingsChange = { scanSettings = it },
                    )

                    // Scan results overview - overlays on top when visible
                    if (showComprehensiveData) {
                        ScanResultsOverview(
                            cardScanService = cardScanService,
                            onBackPressed = { showComprehensiveData = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    private fun setupNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        if (nfcAdapter == null) {
            statusMessage = "NFC is not supported on this device"
            return
        }

        if (!nfcAdapter!!.isEnabled) {
            statusMessage = "NFC is disabled. Please enable it in Settings"
            return
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                // Enable reader mode for FeliCa detection
                val flags =
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

                val options =
                    Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 3000) }
                adapter.enableReaderMode(this, { tag -> handleTag(tag) }, flags, options)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.let { adapter -> adapter.disableReaderMode(this) }
    }

    private fun handleTag(tag: Tag) {
        val nfcF = NfcF.get(tag)
        if (nfcF != null) {
            // Reset steps for new card scan with current settings
            steps = CardScanStep.createInitialSteps(scanSettings)
            statusMessage = "FeliCa Card Detected! Processing steps..."
            isCardPresent = true

            // Capture current scan settings for this scan session
            val currentScanSettings = scanSettings

            lifecycleScope.launch {
                try {
                    // Perform all NFC operations on IO dispatcher
                    withContext(Dispatchers.IO) {
                        nfcF.connect()
                        val idm = tag.id

                        // Create FeliCa target with PMM obtained via polling
                        val target =
                            cardScanService.wrapTargetForCommunicationLogging(
                                AndroidFeliCaTarget.create(nfcF, idm)
                            )

                        // Apply scan settings to the service
                        cardScanService.setScanSettings(currentScanSettings)

                        // Execute each step sequentially
                        for (i in steps.indices) {
                            val currentStep = steps[i]
                            val updatedStep =
                                cardScanService.executeStep(
                                    step = currentStep,
                                    target = target,
                                    onStepUpdate = { step ->
                                        // Update the step in the list on main thread
                                        launch(Dispatchers.Main) {
                                            steps = steps.toMutableList().apply { set(i, step) }
                                        }
                                    },
                                )

                            // Update with final result on main thread
                            withContext(Dispatchers.Main) {
                                steps = steps.toMutableList().apply { set(i, updatedStep) }
                            }
                        }

                        nfcF.close()
                    }

                    // Update UI on main thread
                    statusMessage = "Card scanning completed!"
                } catch (e: Exception) {
                    Log.e("FeliCa", "Error reading FeliCa card", e)
                    statusMessage = "Error reading FeliCa card: ${e.message}"
                    isCardPresent = false

                    // Close connection on IO thread
                    withContext(Dispatchers.IO) {
                        try {
                            nfcF.close()
                        } catch (closeException: Exception) {
                            Log.e("FeliCa", "Error closing NFC connection", closeException)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    steps: List<CardScanStep>,
    isCardPresent: Boolean,
    statusMessage: String,
    onToggleCollapse: (String) -> Unit = {},
    scanSettings: ScanSettings = ScanSettings(),
    onScanSettingsChange: (ScanSettings) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showSettingsMenu by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("FeliCa Tool") },
                actions = {
                    IconButton(onClick = { showSettingsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Scan settings",
                        )
                    }

                    DropdownMenu(
                        expanded = showSettingsMenu,
                        onDismissRequest = { showSettingsMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Force discover all nodes") },
                            onClick = {
                                onScanSettingsChange(
                                    scanSettings.copy(
                                        forceDiscoverAllNodes = !scanSettings.forceDiscoverAllNodes
                                    )
                                )
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = scanSettings.forceDiscoverAllNodes,
                                    onCheckedChange = {
                                        onScanSettingsChange(
                                            scanSettings.copy(forceDiscoverAllNodes = it)
                                        )
                                    },
                                )
                            },
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        },
    ) { innerPadding ->
        FeliCaReaderScreen(
            steps = steps,
            isCardPresent = isCardPresent,
            statusMessage = statusMessage,
            onToggleCollapse = onToggleCollapse,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
fun FeliCaReaderScreen(
    steps: List<CardScanStep>,
    isCardPresent: Boolean,
    statusMessage: String,
    onToggleCollapse: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Check if scan is completed by looking for the scan_overview step
    val isScanCompleted =
        steps.any { step -> step.id == "scan_overview" && step.status == StepStatus.COMPLETED }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Status message bar - slim and adhering to header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color =
                    if (isCardPresent) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    },
            ) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color =
                        if (isCardPresent) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Steps list
            if (steps.isNotEmpty()) {
                StepsList(
                    steps = steps,
                    onToggleCollapse = onToggleCollapse,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Floating scan overview button when scan is completed
        if (isScanCompleted) {
            Button(
                onClick = { onToggleCollapse("scan_overview") },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                elevation =
                    ButtonDefaults.buttonElevation(
                        defaultElevation = 8.dp,
                        pressedElevation = 12.dp,
                    ),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(text = "Scan Overview", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FeliCaReaderPreview() {
    FeliCaToolTheme {
        val sampleSteps =
            listOf(
                CardScanStep.createInitialSteps()[0].copy(
                    status = StepStatus.COMPLETED,
                    result = "IDM: 0123456789ABCDEF\nManufacturer: 2\nNFC System Code: 8008",
                ),
                CardScanStep.createInitialSteps()[1].copy(
                    status = StepStatus.COMPLETED,
                    result = "System Code: 8008",
                ),
                CardScanStep.createInitialSteps()[2].copy(status = StepStatus.IN_PROGRESS),
                CardScanStep.createInitialSteps()[3],
            )
        MainScreen(
            steps = sampleSteps,
            isCardPresent = true,
            statusMessage = "Processing card data...",
        )
    }
}
