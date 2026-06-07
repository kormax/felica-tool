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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.kormax.felicatool.felica.FeliCaTarget
import com.kormax.felicatool.felica.PollingCommand
import com.kormax.felicatool.felica.RequestCode
import com.kormax.felicatool.felica.TimeSlot
import com.kormax.felicatool.nfc.ActivitySuspendedException
import com.kormax.felicatool.nfc.AndroidNfcReader
import com.kormax.felicatool.nfc.NfcReader
import com.kormax.felicatool.nfc.NfcReaderSession
import com.kormax.felicatool.service.CardScanResult
import com.kormax.felicatool.service.CardScanService
import com.kormax.felicatool.service.ScanSettings
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.ScanResultsOverview
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.ui.components.StepsList
import com.kormax.felicatool.ui.theme.FeliCaToolTheme
import com.kormax.felicatool.util.IcTypeRegistry
import com.kormax.felicatool.util.NodeRegistry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

private const val MANUAL_READER_SESSION_TIMEOUT_SECONDS = 60
private const val AUTOMATIC_READER_RETRY_DELAY_SECONDS = 2
private const val AUTOMATIC_READER_START_DELAY_SECONDS = 2
private const val TAG_REMOVAL_DISCOVERY_TIMEOUT_MILLIS = 500
private const val TAG_REMOVAL_PRESENCE_CHECK_DELAY_MILLIS = 500L
private const val READER_PREFERENCES_NAME = "reader_settings"
private const val KEY_BACKGROUND_READING = "background_reading"
private const val TAG = "MainActivity"
private val ReaderActionButtonMinHeight = 48.dp
private val ScanOverviewButtonBottomInset = 16.dp
private val ScanOverviewButtonListGap = 8.dp
private val ScanOverviewButtonReservedPadding =
    ReaderActionButtonMinHeight + ScanOverviewButtonBottomInset + ScanOverviewButtonListGap

private data class ReaderControlState(
    val isAutomaticReadingEnabled: Boolean = false,
    val isAutomaticReadingAvailable: Boolean = true,
    val isReaderActive: Boolean = false,
    val isReaderDiscovering: Boolean = false,
    val isWaitingForRemoval: Boolean = false,
    val isScanAvailable: Boolean = true,
    val issueMessage: String? = null,
    val manualSessionSecondsRemaining: Int? = null,
    val automaticRestartSecondsRemaining: Int? = null,
)

class MainActivity : ComponentActivity() {
    private var nfcReader: NfcReader? = null
    private var activeReaderSession: NfcReaderSession? = null
    private var readerJob: Job? = null
    private var automaticReaderStartNowSignal: CompletableDeferred<Unit>? = null
    private val cardScanService = CardScanService(NodeRegistry)

    private var steps by mutableStateOf(emptyList<CardScanStep>())
    private var isCardPresent by mutableStateOf(false)
    private var isReaderActive by mutableStateOf(false)
    private var isReaderDiscovering by mutableStateOf(false)
    private var isWaitingForRemoval by mutableStateOf(false)
    private var isNfcReady by mutableStateOf(false)
    private var statusMessage by mutableStateOf("Ready")
    private var scanDuration by mutableStateOf<Duration?>(null)
    private var readerIssueMessage by mutableStateOf<String?>(null)
    private var manualReaderSessionSecondsRemaining by mutableStateOf<Int?>(null)
    private var automaticReaderRestartSecondsRemaining by mutableStateOf<Int?>(null)
    private var showComprehensiveData by mutableStateOf(false)
    private var isBackgroundReadingEnabled by mutableStateOf(false)
    private var scanSettings by mutableStateOf(ScanSettings())
    private var isActivityResumed = false
    private val readerPreferences by lazy {
        applicationContext.getSharedPreferences(READER_PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    private val nfcForegroundDispatchIntent by lazy {
        val flags =
            PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags,
        )
    }

    private fun toggleStepCollapse(stepId: String) {
        if (stepId == "scan_overview") {
            showComprehensiveData = true
            statusMessage = "Card scanning completed!"
            if (isBackgroundReadingEnabled) {
                stopReaderSession(status = "Card scanning completed!")
            }
            return
        }

        steps = steps.map { step ->
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

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                    NodeRegistry.ensureReady()
                    IcTypeRegistry.ensureReady()
                }
                .onFailure { Log.w(TAG, "Failed to preload shared metadata", it) }
        }

        isBackgroundReadingEnabled = readerPreferences.getBoolean(KEY_BACKGROUND_READING, false)
        val reader = AndroidNfcReader(this)
        reader.disableBackgroundDiscovery()
        nfcReader = reader
        refreshNfcStatus()
        if (isBackgroundReadingEnabled && !isBackgroundReadingAvailable()) {
            updateBackgroundReadingEnabled(false)
        }

        setContent {
            FeliCaToolTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main screen - always present
                    MainScreen(
                        steps = steps,
                        isCardPresent = isCardPresent,
                        statusMessage = statusMessage,
                        scanDuration = scanDuration,
                        onToggleCollapse = ::toggleStepCollapse,
                        readerControlState =
                            ReaderControlState(
                                isAutomaticReadingEnabled = isBackgroundReadingEnabled,
                                isAutomaticReadingAvailable = isBackgroundReadingAvailable(),
                                isReaderActive = isReaderActive,
                                isReaderDiscovering = isReaderDiscovering,
                                isWaitingForRemoval = isWaitingForRemoval,
                                isScanAvailable = isNfcReady,
                                issueMessage = readerIssueMessage,
                                manualSessionSecondsRemaining = manualReaderSessionSecondsRemaining,
                                automaticRestartSecondsRemaining =
                                    automaticReaderRestartSecondsRemaining,
                            ),
                        isBackgroundReadingEnabled = isBackgroundReadingEnabled,
                        onBackgroundReadingChange = ::updateBackgroundReadingEnabled,
                        scanSettings = scanSettings,
                        onScanSettingsChange = { scanSettings = it },
                        onStartScan = ::startManualReaderSession,
                        onStopScan = { stopReaderSession(status = "Ready") },
                        onStartAutomaticReaderNow = {
                            automaticReaderStartNowSignal?.complete(Unit)
                        },
                    )

                    // Scan results overview - overlays on top when visible
                    if (showComprehensiveData) {
                        ScanResultsOverview(
                            cardScanService = cardScanService,
                            onBackPressed = {
                                showComprehensiveData = false
                                if (isBackgroundReadingEnabled) {
                                    stopReaderSession {
                                        startAutomaticReaderAfterDelay(
                                            AUTOMATIC_READER_START_DELAY_SECONDS
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED -> {
                Log.i(TAG, "Dropping NFC foreground dispatch intent: ${intent.action}")
                return
            }
        }

        setIntent(intent)
    }

    private fun refreshNfcStatus(): Boolean {
        val reader = nfcReader ?: AndroidNfcReader(this).also { nfcReader = it }

        if (!reader.isAvailable) {
            isNfcReady = false
            statusMessage = "NFC is not supported on this device"
            return false
        }

        if (!reader.isEnabled) {
            isNfcReady = false
            statusMessage = "NFC is disabled. Please enable it in Settings"
            return false
        }

        isNfcReady = true
        if (
            readerIssueMessage == null &&
                !isReaderActive &&
                readerJob?.isActive != true &&
                steps.isEmpty()
        ) {
            statusMessage = "Ready"
        }
        return true
    }

    private fun isBackgroundReadingAvailable(): Boolean {
        val capabilities = nfcReader?.capabilities ?: return false
        return capabilities.maximumReaderSessionDuration.isInfinite() &&
            !capabilities.activeSessionDisplaysSystemModel
    }

    private fun shouldRunAutomaticReader(): Boolean =
        isBackgroundReadingEnabled &&
            isBackgroundReadingAvailable() &&
            isActivityResumed &&
            !showComprehensiveData

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        if (!isReaderActive) {
            (nfcReader as? AndroidNfcReader)?.disableBackgroundDiscovery()
        }
        refreshNfcStatus()
        setNfcForegroundDispatchEnabled(true)

        if (shouldRunAutomaticReader() && readerJob?.isActive != true) {
            startAutomaticReaderAfterDelay(AUTOMATIC_READER_START_DELAY_SECONDS)
        }
    }

    override fun onPause() {
        setNfcForegroundDispatchEnabled(false)
        isActivityResumed = false
        super.onPause()
    }

    private fun setNfcForegroundDispatchEnabled(enabled: Boolean) {
        val adapter = NfcAdapter.getDefaultAdapter(this) ?: return

        if (enabled) {
            if (!isActivityResumed || !adapter.isEnabled) {
                return
            }

            runCatching {
                    adapter.enableForegroundDispatch(this, nfcForegroundDispatchIntent, null, null)
                }
                .onFailure { Log.w(TAG, "Unable to enable NFC foreground dispatch", it) }
        } else {
            runCatching { adapter.disableForegroundDispatch(this) }
                .onFailure { Log.w(TAG, "Unable to disable NFC foreground dispatch", it) }
        }
    }

    private fun updateBackgroundReadingEnabled(enabled: Boolean) {
        val normalizedEnabled = enabled && isBackgroundReadingAvailable()
        val backgroundReadingChanged = normalizedEnabled != isBackgroundReadingEnabled

        isBackgroundReadingEnabled = normalizedEnabled
        readerPreferences.edit().putBoolean(KEY_BACKGROUND_READING, normalizedEnabled).apply()

        if (!backgroundReadingChanged) {
            return
        }

        if (normalizedEnabled) {
            readerIssueMessage = null
            stopReaderSession {
                startAutomaticReaderAfterDelay(AUTOMATIC_READER_START_DELAY_SECONDS)
            }
        } else {
            stopReaderSession(status = "Ready")
        }
    }

    private fun startManualReaderSession() {
        if (readerJob?.isActive == true) {
            return
        }

        steps = emptyList()
        scanDuration = null
        isCardPresent = false

        readerJob = lifecycleScope.launch {
            try {
                runManualReaderSession()
            } finally {
                activeReaderSession = null
                isReaderActive = false
                isReaderDiscovering = false
                isWaitingForRemoval = false
                manualReaderSessionSecondsRemaining = null
                automaticReaderRestartSecondsRemaining = null
                readerJob = null
            }
        }
    }

    private fun startAutomaticReaderAfterDelay(delaySeconds: Int) {
        if (readerJob?.isActive == true || !shouldRunAutomaticReader()) {
            return
        }

        readerJob = lifecycleScope.launch {
            try {
                delayBeforeAutomaticRestart(delaySeconds)
                if (shouldRunAutomaticReader()) {
                    runAutomaticReaderLoop()
                }
            } finally {
                activeReaderSession = null
                isReaderActive = false
                isReaderDiscovering = false
                isWaitingForRemoval = false
                manualReaderSessionSecondsRemaining = null
                automaticReaderRestartSecondsRemaining = null
                readerJob = null
            }
        }
    }

    private fun stopReaderSession(status: String? = null, afterStopped: (() -> Unit)? = null) {
        readerIssueMessage = null
        isReaderDiscovering = false
        isWaitingForRemoval = false
        manualReaderSessionSecondsRemaining = null
        automaticReaderRestartSecondsRemaining = null
        activeReaderSession?.close()
        activeReaderSession = null

        val job = readerJob
        if (job != null) {
            lifecycleScope.launch {
                job.cancelAndJoin()
                if (readerJob === job) {
                    readerJob = null
                }
                isReaderActive = false
                status?.let { statusMessage = it }
                afterStopped?.invoke()
            }
        } else {
            isReaderActive = false
            status?.let { statusMessage = it }
            afterStopped?.invoke()
        }
    }

    private suspend fun runManualReaderSession() {
        readerIssueMessage = null
        automaticReaderRestartSecondsRemaining = null

        if (!refreshNfcStatus()) {
            readerIssueMessage = statusMessage
            return
        }

        isReaderActive = true
        statusMessage = "Hold a FeliCa card near the device"

        val session =
            try {
                nfcReader!!.startReaderSession()
            } catch (e: Exception) {
                setReaderIssue(e.message ?: "Unable to start NFC reader")
                isReaderActive = false
                return
            }

        activeReaderSession = session
        val countdownJob = lifecycleScope.launch {
            for (remaining in MANUAL_READER_SESSION_TIMEOUT_SECONDS downTo 0) {
                manualReaderSessionSecondsRemaining = remaining
                delay(1.seconds)
            }
        }

        try {
            withTimeout(MANUAL_READER_SESSION_TIMEOUT_SECONDS.seconds) {
                val target = discoverFeliCaTarget(session = session, timeout = Duration.INFINITE)
                scanFeliCaTarget(target)
            }
        } catch (_: TimeoutCancellationException) {
            setReaderIssue("Reader session timed out")
        } catch (e: ActivitySuspendedException) {
            setReaderIssue(e.message ?: "Reader session suspended")
        } catch (_: CancellationException) {
            // Session was stopped by the app.
        } catch (e: Exception) {
            Log.e("FeliCa", "Error reading FeliCa card", e)
            setReaderIssue("Error reading FeliCa card: ${e.message}")
        } finally {
            countdownJob.cancel()
            manualReaderSessionSecondsRemaining = null
            if (activeReaderSession === session) {
                activeReaderSession = null
            }
            session.close()
            isReaderActive = false
        }
    }

    private suspend fun runAutomaticReaderLoop() {
        while (shouldRunAutomaticReader()) {
            readerIssueMessage = null
            automaticReaderRestartSecondsRemaining = null

            if (!refreshNfcStatus()) {
                readerIssueMessage = statusMessage
                delayBeforeAutomaticRestart(AUTOMATIC_READER_RETRY_DELAY_SECONDS)
                continue
            }

            isReaderActive = true
            statusMessage = "Background reading active"

            val session =
                try {
                    nfcReader!!.startReaderSession()
                } catch (e: Exception) {
                    setReaderIssue(e.message ?: "Unable to start NFC reader")
                    isReaderActive = false
                    delayBeforeAutomaticRestart(AUTOMATIC_READER_RETRY_DELAY_SECONDS)
                    continue
                }

            activeReaderSession = session

            try {
                val target = discoverFeliCaTarget(session = session, timeout = Duration.INFINITE)
                val scanResult = scanFeliCaTarget(target)
                if (scanResult.completed) {
                    waitForFeliCaTagRemoval(session)
                }
            } catch (e: ActivitySuspendedException) {
                setReaderIssue(e.message ?: "Reader session suspended")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FeliCa", "Error reading FeliCa card", e)
                setReaderIssue("Error reading FeliCa card: ${e.message}")
            } finally {
                if (activeReaderSession === session) {
                    activeReaderSession = null
                }
                session.close()
                isReaderActive = false
            }

            if (shouldRunAutomaticReader()) {
                delayBeforeAutomaticRestart(AUTOMATIC_READER_RETRY_DELAY_SECONDS)
            }
        }
    }

    private suspend fun discoverFeliCaTarget(
        session: NfcReaderSession,
        timeout: Duration,
    ): FeliCaTarget {
        isReaderDiscovering = true
        return try {
            session.discoverFeliCaTarget(timeout = timeout)
        } finally {
            isReaderDiscovering = false
        }
    }

    private suspend fun delayBeforeAutomaticRestart(delaySeconds: Int) {
        if (delaySeconds <= 0) {
            automaticReaderRestartSecondsRemaining = null
            return
        }

        val startNowSignal = CompletableDeferred<Unit>()
        automaticReaderStartNowSignal = startNowSignal

        try {
            for (remaining in delaySeconds downTo 1) {
                if (!shouldRunAutomaticReader()) {
                    break
                }
                automaticReaderRestartSecondsRemaining = remaining
                val shouldStartNow =
                    withTimeoutOrNull(1.seconds) {
                        startNowSignal.await()
                        true
                    } == true
                if (shouldStartNow) {
                    break
                }
            }
        } finally {
            if (automaticReaderStartNowSignal === startNowSignal) {
                automaticReaderStartNowSignal = null
            }
            automaticReaderRestartSecondsRemaining = null
        }
    }

    private fun setReaderIssue(message: String) {
        readerIssueMessage = message
        statusMessage = message
        isCardPresent = false
    }

    private suspend fun waitForFeliCaTagRemoval(readerSession: NfcReaderSession) {
        statusMessage = "Remove card to continue"
        isWaitingForRemoval = true

        try {
            var currentTarget =
                try {
                    discoverFeliCaTarget(
                        session = readerSession,
                        timeout = TAG_REMOVAL_DISCOVERY_TIMEOUT_MILLIS.milliseconds,
                    )
                } catch (_: TimeoutCancellationException) {
                    isCardPresent = false
                    return
                }

            while (shouldRunAutomaticReader()) {
                val activeTarget = currentTarget
                if (!activeTarget.isAvailable) {
                    currentTarget =
                        try {
                            discoverFeliCaTarget(
                                session = readerSession,
                                timeout = TAG_REMOVAL_DISCOVERY_TIMEOUT_MILLIS.milliseconds,
                            )
                        } catch (_: TimeoutCancellationException) {
                            isCardPresent = false
                            return
                        }
                    continue
                }

                try {
                    withContext(Dispatchers.IO) {
                        activeTarget.transceive(
                            PollingCommand(
                                systemCode =
                                    activeTarget.systemCode
                                        ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                                requestCode = RequestCode.NO_REQUEST,
                                timeSlot = TimeSlot.SLOT_1,
                            )
                        )
                    }
                    delay(TAG_REMOVAL_PRESENCE_CHECK_DELAY_MILLIS)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: ActivitySuspendedException) {
                    throw e
                } catch (_: Exception) {
                    withContext(Dispatchers.IO) { activeTarget.drop() }
                }
            }
        } finally {
            isWaitingForRemoval = false
        }
    }

    private suspend fun scanFeliCaTarget(target: FeliCaTarget): CardScanResult {
        val currentScanSettings = scanSettings
        statusMessage = "FeliCa card detected. Processing steps..."
        scanDuration = null
        isCardPresent = true

        val result =
            withContext(Dispatchers.IO) {
                cardScanService.scan(
                    target = target,
                    scanSettings = currentScanSettings,
                    onStepsChanged = { updatedSteps ->
                        lifecycleScope.launch(Dispatchers.Main) { steps = updatedSteps }
                    },
                )
            }

        withContext(Dispatchers.Main) {
            steps = result.steps
            scanDuration = result.duration
        }

        val terminalErrorMessage = result.terminalErrorMessage
        if (terminalErrorMessage != null) {
            readerIssueMessage = terminalErrorMessage
            statusMessage = terminalErrorMessage
            isCardPresent = false
        } else {
            statusMessage = "Card scanning completed!"
        }

        return result
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    steps: List<CardScanStep>,
    isCardPresent: Boolean,
    statusMessage: String,
    scanDuration: Duration? = null,
    onToggleCollapse: (String) -> Unit = {},
    readerControlState: ReaderControlState = ReaderControlState(),
    isBackgroundReadingEnabled: Boolean = false,
    onBackgroundReadingChange: (Boolean) -> Unit = {},
    scanSettings: ScanSettings = ScanSettings(),
    onScanSettingsChange: (ScanSettings) -> Unit = {},
    onStartScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onStartAutomaticReaderNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showReaderSettingsMenu by remember { mutableStateOf(false) }
    var showScanSettingsMenu by remember { mutableStateOf(false) }
    val isManualReaderActive =
        readerControlState.isReaderActive && !readerControlState.isAutomaticReadingEnabled
    val isBackgroundReadingToggleEnabled =
        !isManualReaderActive && readerControlState.isAutomaticReadingAvailable

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets =
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = { Text("FeliCa Tool") },
                actions = {
                    IconButton(
                        onClick = {
                            showReaderSettingsMenu = true
                            showScanSettingsMenu = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Reader settings",
                        )
                    }

                    DropdownMenu(
                        expanded = showReaderSettingsMenu,
                        onDismissRequest = { showReaderSettingsMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Background reading") },
                            enabled = isBackgroundReadingToggleEnabled,
                            onClick = { onBackgroundReadingChange(!isBackgroundReadingEnabled) },
                            trailingIcon = {
                                Checkbox(
                                    checked = isBackgroundReadingEnabled,
                                    enabled = isBackgroundReadingToggleEnabled,
                                    onCheckedChange = onBackgroundReadingChange,
                                )
                            },
                        )
                    }

                    IconButton(
                        onClick = {
                            showScanSettingsMenu = true
                            showReaderSettingsMenu = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Scan settings",
                        )
                    }

                    DropdownMenu(
                        expanded = showScanSettingsMenu,
                        onDismissRequest = { showScanSettingsMenu = false },
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
                        DropdownMenuItem(
                            text = { Text("Force discover all blocks") },
                            onClick = {
                                onScanSettingsChange(
                                    scanSettings.copy(
                                        forceDiscoverAllBlocks =
                                            !scanSettings.forceDiscoverAllBlocks
                                    )
                                )
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = scanSettings.forceDiscoverAllBlocks,
                                    onCheckedChange = {
                                        onScanSettingsChange(
                                            scanSettings.copy(forceDiscoverAllBlocks = it)
                                        )
                                    },
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Brute-force system codes") },
                            onClick = {
                                onScanSettingsChange(
                                    scanSettings.copy(
                                        bruteForceSystemCodePrefixes =
                                            !scanSettings.bruteForceSystemCodePrefixes
                                    )
                                )
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = scanSettings.bruteForceSystemCodePrefixes,
                                    onCheckedChange = {
                                        onScanSettingsChange(
                                            scanSettings.copy(bruteForceSystemCodePrefixes = it)
                                        )
                                    },
                                )
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Test write commands") },
                            onClick = {
                                onScanSettingsChange(
                                    scanSettings.copy(
                                        testWriteCommands = !scanSettings.testWriteCommands
                                    )
                                )
                            },
                            trailingIcon = {
                                Checkbox(
                                    checked = scanSettings.testWriteCommands,
                                    onCheckedChange = {
                                        onScanSettingsChange(
                                            scanSettings.copy(testWriteCommands = it)
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
            scanDuration = scanDuration,
            onToggleCollapse = onToggleCollapse,
            readerControlState = readerControlState,
            onStartScan = onStartScan,
            onStopScan = onStopScan,
            onStartAutomaticReaderNow = onStartAutomaticReaderNow,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun FeliCaReaderScreen(
    steps: List<CardScanStep>,
    isCardPresent: Boolean,
    statusMessage: String,
    scanDuration: Duration? = null,
    onToggleCollapse: (String) -> Unit = {},
    readerControlState: ReaderControlState = ReaderControlState(),
    onStartScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onStartAutomaticReaderNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Check if scan is completed by looking for the scan_overview step
    val isScanCompleted = steps.any { step ->
        step.id == "scan_overview" && step.status == StepStatus.COMPLETED
    }
    val isStepScanActive =
        !isScanCompleted &&
            steps.isNotEmpty() &&
            (readerControlState.isReaderActive ||
                steps.any { step -> step.status == StepStatus.IN_PROGRESS })

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (steps.isNotEmpty()) {
                    StepsList(
                        steps = steps,
                        onToggleCollapse = onToggleCollapse,
                        modifier = Modifier.fillMaxSize(),
                        isScrollLocked = isStepScanActive,
                        isScanCompleted = isScanCompleted,
                        contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                top = 16.dp,
                                end = 16.dp,
                                bottom =
                                    if (isScanCompleted) {
                                        ScanOverviewButtonReservedPadding
                                    } else {
                                        16.dp
                                    },
                            ),
                    )
                }

                if (isScanCompleted) {
                    Button(
                        onClick = { onToggleCollapse("scan_overview") },
                        modifier =
                            Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(ScanOverviewButtonBottomInset)
                                .heightIn(min = ReaderActionButtonMinHeight),
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

            ReaderBottomBar(
                state = readerControlState,
                statusMessage = statusMessage,
                scanDuration = if (isScanCompleted) scanDuration else null,
                isCardPresent = isCardPresent,
                onStartScan = onStartScan,
                onStopScan = onStopScan,
                onStartAutomaticReaderNow = onStartAutomaticReaderNow,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    state: ReaderControlState,
    statusMessage: String,
    scanDuration: Duration?,
    isCardPresent: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onStartAutomaticReaderNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isAutomatic = state.isAutomaticReadingEnabled
    val hasIssue = state.issueMessage != null
    val restartSeconds = state.automaticRestartSecondsRemaining
    val canStartAutomaticNow = isAutomatic && restartSeconds != null
    val buttonEnabled =
        canStartAutomaticNow ||
            (!isAutomatic && (state.isReaderActive || state.isScanAvailable || hasIssue))
    val showSpinner = state.isReaderActive || state.isReaderDiscovering
    val statusText =
        if (scanDuration != null) {
            "$statusMessage\nScan time: ${scanDuration.inWholeMilliseconds} ms"
        } else {
            statusMessage
        }
    val buttonText =
        when {
            isAutomatic && state.isReaderDiscovering -> "Looking for tags"
            isAutomatic && state.isWaitingForRemoval -> "Waiting for removal"
            isAutomatic && restartSeconds != null -> "Reading starting in $restartSeconds sec"
            isAutomatic && state.isReaderActive -> "Reading active"
            isAutomatic -> "Reading stopped"
            state.isReaderActive -> "Stop reading - ${state.manualSessionSecondsRemaining ?: 0}s"
            hasIssue -> "Restart reading"
            else -> "Start reading"
        }
    val onReaderButtonClick =
        when {
            canStartAutomaticNow -> onStartAutomaticReaderNow
            !isAutomatic && state.isReaderActive -> onStopScan
            else -> onStartScan
        }

    Surface(
        modifier = modifier,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                color =
                    when {
                        state.issueMessage != null ->
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                        isCardPresent ->
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    },
                shape = MaterialTheme.shapes.small,
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            when {
                                state.issueMessage != null ->
                                    MaterialTheme.colorScheme.onErrorContainer
                                isCardPresent -> MaterialTheme.colorScheme.onPrimaryContainer
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            Button(
                onClick = onReaderButtonClick,
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = ReaderActionButtonMinHeight),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showSpinner) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (!isAutomatic && !state.isReaderActive) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                    Text(text = buttonText)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FeliCaReaderPreview() {
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
            scanDuration = 1234.milliseconds,
        )
    }
}
