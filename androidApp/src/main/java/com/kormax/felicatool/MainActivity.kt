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

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.lifecycle.lifecycleScope
import com.kormax.felicatool.authentication.AndroidAuthenticationProviders
import com.kormax.felicatool.authentication.AndroidKeyImportIdentities
import com.kormax.felicatool.device.KeystoreCapabilities
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
import com.kormax.felicatool.ui.AboutScreen
import com.kormax.felicatool.ui.AuthenticationProviderEditorScreen
import com.kormax.felicatool.ui.AuthenticationProvidersScreen
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.KeyImportIdentitiesScreen
import com.kormax.felicatool.ui.ScanResultsOverview
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.ui.components.StepsList
import com.kormax.felicatool.ui.theme.FeliCaToolTheme
import com.kormax.felicatool.util.IcTypeRegistry
import com.kormax.felicatool.util.IssuerRegistry
import com.kormax.felicatool.util.MobileDeviceRegistry
import com.kormax.felicatool.util.NodeRegistry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
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
private const val STATE_SHOW_ABOUT = "show_about"
private const val STATE_SHOW_AUTHENTICATION_PROVIDERS = "show_authentication_providers"
private const val STATE_SELECTED_AUTHENTICATION_PROVIDER_ID = "selected_authentication_provider_id"
private const val STATE_SHOW_KEY_IMPORT_IDENTITIES = "show_key_import_identities"
private const val KEY_TEST_TRAILING_DATA_COMMANDS = "test_trailing_data_commands"
private const val KEY_TEST_WRITE_COMMANDS = "test_write_commands"
private const val KEY_IC_CODE_SCAN_HEURISTICS = "ic_code_scan_heuristics"
private const val KEY_SPECIFICATION_VERSION_SCAN_HEURISTICS =
    "specification_version_scan_heuristics"
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
    val isTagPresent: Boolean = false,
    val issueMessage: String? = null,
    val manualSessionSecondsRemaining: Int? = null,
    val automaticRestartSecondsRemaining: Int? = null,
)

class MainActivity : ComponentActivity() {
    private lateinit var nfcReader: NfcReader
    private var activeReaderSession: NfcReaderSession? = null
    private var readerJob: Job? = null
    private var automaticReaderStartNowSignal: CompletableDeferred<Unit>? = null
    private val authenticationProviders by lazy {
        AndroidAuthenticationProviders.instance(this)
    }
    private val cardScanService by lazy { CardScanService(NodeRegistry, authenticationProviders) }

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
    private var showAuthenticationProviders by mutableStateOf(false)
    private var showAbout by mutableStateOf(false)
    private var selectedAuthenticationProviderId by mutableStateOf<String?>(null)
    private var showKeyImportIdentities by mutableStateOf(false)
    private val keyImportIdentities by lazy { AndroidKeyImportIdentities(applicationContext) }
    private var isBackgroundReadingEnabled by mutableStateOf(false)
    private var scanSettings by mutableStateOf(ScanSettings())
    private var isActivityResumed = false
    private val readerPreferences by lazy {
        applicationContext.getSharedPreferences(READER_PREFERENCES_NAME, Context.MODE_PRIVATE)
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
        KeystoreCapabilities.start(applicationContext)

        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                NodeRegistry.ensureReady()
                IcTypeRegistry.ensureReady()
                MobileDeviceRegistry.ensureReady()
                IssuerRegistry.ensureReady()
            }
                .onFailure { Log.w(TAG, "Failed to preload shared metadata", it) }
            runCatching { authenticationProviders.summaries() }
                .onFailure { Log.w(TAG, "Failed to preload authentication providers", it) }
        }

        savedInstanceState?.let {
            showAbout = it.getBoolean(STATE_SHOW_ABOUT)
            showAuthenticationProviders = it.getBoolean(STATE_SHOW_AUTHENTICATION_PROVIDERS)
            selectedAuthenticationProviderId =
                it.getString(STATE_SELECTED_AUTHENTICATION_PROVIDER_ID)
            showKeyImportIdentities = it.getBoolean(STATE_SHOW_KEY_IMPORT_IDENTITIES)
        }
        isBackgroundReadingEnabled = readerPreferences.getBoolean(KEY_BACKGROUND_READING, false)
        scanSettings =
            scanSettings.copy(
                enableIcCodeScanHeuristics =
                    readerPreferences.getBoolean(KEY_IC_CODE_SCAN_HEURISTICS, true),
                enableSpecificationVersionScanHeuristics =
                    readerPreferences.getBoolean(KEY_SPECIFICATION_VERSION_SCAN_HEURISTICS, true),
                testTrailingDataCommands =
                    readerPreferences.getBoolean(KEY_TEST_TRAILING_DATA_COMMANDS, false),
                testWriteCommands = readerPreferences.getBoolean(KEY_TEST_WRITE_COMMANDS, false),
            )
        val reader = AndroidNfcReader(this)
        nfcReader = reader
        refreshNfcStatus()
        if (isBackgroundReadingEnabled && !isBackgroundReadingAvailable()) {
            updateBackgroundReadingEnabled(false)
        }

        setContent {
            val isTagPresent by reader.isTagPresent.collectAsState()
            FeliCaToolTheme {
                Box(modifier = Modifier.fillMaxSize()) {
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
                                isTagPresent = isTagPresent,
                                issueMessage = readerIssueMessage,
                                manualSessionSecondsRemaining = manualReaderSessionSecondsRemaining,
                                automaticRestartSecondsRemaining =
                                    automaticReaderRestartSecondsRemaining,
                            ),
                        isBackgroundReadingEnabled = isBackgroundReadingEnabled,
                        onBackgroundReadingChange = ::updateBackgroundReadingEnabled,
                        onOpenAuthenticationProviders = {
                            showAuthenticationProviders = true
                            pauseReader()
                        },
                        scanSettings = scanSettings,
                        onScanSettingsChange = ::updateScanSettings,
                        onShowAbout = {
                            showAbout = true
                            pauseReader()
                        },
                        onStartScan = ::startManualReaderSession,
                        onStopScan = { stopReaderSession(status = "Ready") },
                        onStartAutomaticReaderNow = {
                            automaticReaderStartNowSignal?.complete(Unit)
                        },
                    )

                    if (showAbout) {
                        AboutScreen(
                            onBackPressed = {
                                showAbout = false
                                resumeReader()
                            },
                        )
                    } else if (showAuthenticationProviders) {
                        val providerId = selectedAuthenticationProviderId
                        if (showKeyImportIdentities) {
                            KeyImportIdentitiesScreen(
                                identities = keyImportIdentities,
                                onBackPressed = { showKeyImportIdentities = false },
                            )
                        } else if (providerId == null) {
                            AuthenticationProvidersScreen(
                                settings = authenticationProviders,
                                localCredentials = authenticationProviders,
                                onBackPressed = {
                                    showAuthenticationProviders = false
                                    resumeReader()
                                },
                                onOpenProvider = { selectedAuthenticationProviderId = it },
                                onOpenImportIdentities = { showKeyImportIdentities = true },
                            )
                        } else {
                            AuthenticationProviderEditorScreen(
                                settings = authenticationProviders,
                                localCredentials = authenticationProviders,
                                providerId = providerId,
                                onBackPressed = { selectedAuthenticationProviderId = null },
                                onDeleted = { selectedAuthenticationProviderId = null },
                            )
                        }
                    } else if (showComprehensiveData) {
                        ScanResultsOverview(
                            cardScanService = cardScanService,
                            onBackPressed = {
                                showComprehensiveData = false
                                resumeReader()
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    private fun pauseReader() {
        if (readerJob != null) {
            stopReaderSession(status = "Ready")
        }
    }

    private fun resumeReader() {
        refreshNfcStatus()
        if (isBackgroundReadingEnabled) {
            stopReaderSession {
                startAutomaticReaderAfterDelay(AUTOMATIC_READER_START_DELAY_SECONDS)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_SHOW_ABOUT, showAbout)
        outState.putBoolean(STATE_SHOW_AUTHENTICATION_PROVIDERS, showAuthenticationProviders)
        outState.putString(
            STATE_SELECTED_AUTHENTICATION_PROVIDER_ID,
            selectedAuthenticationProviderId,
        )
        outState.putBoolean(STATE_SHOW_KEY_IMPORT_IDENTITIES, showKeyImportIdentities)
    }

    private fun refreshNfcStatus(): Boolean {
        if (!nfcReader.isAvailable) {
            isNfcReady = false
            statusMessage = "NFC is not supported on this device"
            return false
        }

        if (!nfcReader.isEnabled) {
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
        val capabilities = nfcReader.capabilities
        return capabilities.maximumReaderSessionDuration.isInfinite() &&
            !capabilities.activeSessionDisplaysSystemModel
    }

    private fun shouldRunAutomaticReader(): Boolean =
        isBackgroundReadingEnabled &&
            isBackgroundReadingAvailable() &&
            isActivityResumed &&
            !showComprehensiveData &&
            !showAuthenticationProviders &&
            !showAbout

    override fun onResume() {
        super.onResume()
        isActivityResumed = true
        refreshNfcStatus()

        if (shouldRunAutomaticReader() && readerJob?.isActive != true) {
            startAutomaticReaderAfterDelay(AUTOMATIC_READER_START_DELAY_SECONDS)
        }
    }

    override fun onPause() {
        isActivityResumed = false
        super.onPause()
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

    private fun updateScanSettings(settings: ScanSettings) {
        scanSettings = settings
        readerPreferences
            .edit()
            .putBoolean(KEY_IC_CODE_SCAN_HEURISTICS, settings.enableIcCodeScanHeuristics)
            .putBoolean(
                KEY_SPECIFICATION_VERSION_SCAN_HEURISTICS,
                settings.enableSpecificationVersionScanHeuristics,
            )
            .putBoolean(KEY_TEST_TRAILING_DATA_COMMANDS, settings.testTrailingDataCommands)
            .putBoolean(KEY_TEST_WRITE_COMMANDS, settings.testWriteCommands)
            .apply()
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
        val job = readerJob
        job?.cancel()
        activeReaderSession?.close()
        activeReaderSession = null

        if (job != null) {
            lifecycleScope.launch {
                job.join()
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
                nfcReader.startReaderSession()
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
                    nfcReader.startReaderSession()
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
                        val pollingCommand =
                            PollingCommand(
                                systemCode =
                                    activeTarget.systemCode
                                        ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                                requestCode = RequestCode.NO_REQUEST,
                                timeSlot = TimeSlot.SLOT_1,
                            )
                        activeTarget.transceive(
                            data = pollingCommand.toByteArray(),
                            timeout = activeTarget.inferTimeout(pollingCommand),
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

@Composable
internal fun AppBarMenu(
    icon: ImageVector,
    label: String,
    content: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(icon, contentDescription = label)
        }
        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            // Leave room inside the scroll viewport for the menu groups' elevation shadows.
            modifier =
                Modifier.verticalScroll(rememberScrollState())
                    .padding(MenuDefaults.ShadowElevation * 2),
        ) {
            content { expanded = false }
        }
    }
}

@Composable
internal fun AppBarMenuGroup(
    shapes: MenuGroupShapes,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenuGroup(
        shapes = shapes,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        content = content,
    )
}

@Composable
internal fun CheckableMenuOption(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shapes: MenuItemShapes,
    enabled: Boolean = true,
) {
    val checkmarkSize = MenuDefaults.TrailingIconSize
    val trailingSpace = checkmarkSize + MenuDefaults.DropdownMenuItemHorizontalArrangement.spacing
    CheckableDropdownMenuItem(
        text = {
            // Material measures the label before the trailing slot. Leave room for the checkmark.
            Text(
                label,
                modifier =
                    Modifier.layout { measurable, constraints ->
                        val labelConstraints =
                            constraints.offset(horizontal = -trailingSpace.roundToPx())
                        val placeable = measurable.measure(labelConstraints)
                        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
                    },
            )
        },
        checked = checked,
        onCheckedChange = onCheckedChange,
        shapes = shapes,
        contentPadding = MenuDefaults.DropdownMenuItemContentPadding,
        colors =
            MenuDefaults.selectableItemColors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
            )
        },
        trailingContent = {
            if (checked) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(checkmarkSize),
                )
            }
        },
        enabled = enabled,
    )
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
    onOpenAuthenticationProviders: () -> Unit = {},
    scanSettings: ScanSettings = ScanSettings(),
    onScanSettingsChange: (ScanSettings) -> Unit = {},
    onShowAbout: () -> Unit = {},
    onStartScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onStartAutomaticReaderNow: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
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
                    AppBarMenu(icon = Icons.Filled.Settings, label = "Reader settings") {
                        AppBarMenuGroup(
                            shapes = MenuDefaults.groupShape(0, 1),
                            modifier = Modifier.widthIn(min = 200.dp),
                        ) {
                            CheckableMenuOption(
                                label = "Background reading",
                                icon = Icons.Filled.Refresh,
                                checked = isBackgroundReadingEnabled,
                                enabled = isBackgroundReadingToggleEnabled,
                                onCheckedChange = onBackgroundReadingChange,
                                shapes = MenuDefaults.itemShape(0, 1),
                            )
                        }
                    }
                    val canPauseReader =
                        !readerControlState.isReaderActive ||
                            (readerControlState.isAutomaticReadingEnabled &&
                                (readerControlState.isReaderDiscovering ||
                                    readerControlState.isWaitingForRemoval))
                    AppBarMenu(icon = Icons.Default.MoreVert, label = "More options") { dismiss ->
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(0, 5)) {
                            DropdownMenuItem(
                                text = { Text("About") },
                                shape = MenuDefaults.standaloneItemShape,
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                enabled = canPauseReader,
                                onClick = {
                                    dismiss()
                                    onShowAbout()
                                },
                            )
                        }
                        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(1, 5)) {
                            DropdownMenuItem(
                                text = { Text("Authentication providers") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Lock, contentDescription = null)
                                },
                                shape = MenuDefaults.standaloneItemShape,
                                enabled = canPauseReader,
                                onClick = {
                                    dismiss()
                                    onOpenAuthenticationProviders()
                                },
                            )
                        }
                        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(2, 5)) {
                            CheckableMenuOption(
                                label = "Enable IC code scan heuristics",
                                icon = ImageVector.vectorResource(R.drawable.ic_lightbulb),
                                checked = scanSettings.enableIcCodeScanHeuristics,
                                onCheckedChange = {
                                    onScanSettingsChange(
                                        scanSettings.copy(enableIcCodeScanHeuristics = it),
                                    )
                                },
                                shapes = MenuDefaults.itemShape(0, 2),
                            )
                            CheckableMenuOption(
                                label = "Enable Specification Version scan heuristics",
                                icon = ImageVector.vectorResource(R.drawable.ic_lightbulb),
                                checked = scanSettings.enableSpecificationVersionScanHeuristics,
                                onCheckedChange = {
                                    onScanSettingsChange(
                                        scanSettings.copy(
                                            enableSpecificationVersionScanHeuristics = it,
                                        ),
                                    )
                                },
                                shapes = MenuDefaults.itemShape(1, 2),
                            )
                        }
                        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(3, 5)) {
                            CheckableMenuOption(
                                label = "Test trailing data",
                                icon = ImageVector.vectorResource(R.drawable.ic_data_array),
                                checked = scanSettings.testTrailingDataCommands,
                                onCheckedChange = {
                                    onScanSettingsChange(
                                        scanSettings.copy(testTrailingDataCommands = it),
                                    )
                                },
                                shapes = MenuDefaults.itemShape(0, 2),
                            )
                            CheckableMenuOption(
                                label = "Test write commands",
                                icon = Icons.Filled.Edit,
                                checked = scanSettings.testWriteCommands,
                                onCheckedChange = {
                                    onScanSettingsChange(scanSettings.copy(testWriteCommands = it))
                                },
                                shapes = MenuDefaults.itemShape(1, 2),
                            )
                        }
                        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(4, 5)) {
                            CheckableMenuOption(
                                label = "Force discover all nodes",
                                icon = Icons.Filled.Search,
                                checked = scanSettings.forceDiscoverAllNodes,
                                onCheckedChange = {
                                    onScanSettingsChange(
                                        scanSettings.copy(forceDiscoverAllNodes = it),
                                    )
                                },
                                shapes = MenuDefaults.itemShape(0, 3),
                            )
                            CheckableMenuOption(
                                label = "Force discover all blocks",
                                icon = Icons.Filled.Search,
                                checked = scanSettings.forceDiscoverAllBlocks,
                                onCheckedChange = {
                                    onScanSettingsChange(
                                        scanSettings.copy(forceDiscoverAllBlocks = it),
                                    )
                                },
                                shapes = MenuDefaults.itemShape(1, 3),
                            )
                            CheckableMenuOption(
                                label = "Brute-force system codes",
                                icon = Icons.Filled.Search,
                                checked = scanSettings.bruteForceSystemCodePrefixes,
                                onCheckedChange = {
                                    onScanSettingsChange(
                                        scanSettings.copy(bruteForceSystemCodePrefixes = it),
                                    )
                                },
                                shapes = MenuDefaults.itemShape(2, 3),
                            )
                        }
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
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.ContentPadding,
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
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.ContentPadding,
                enabled = buttonEnabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = ReaderActionButtonMinHeight),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isIdleWithoutTag =
                        !isAutomatic && !state.isReaderActive && !state.isTagPresent
                    val iconAlpha by
                        animateFloatAsState(
                            targetValue = if (isIdleWithoutTag) 0.5f else 1f,
                            animationSpec = tween(durationMillis = 500),
                            label = "Reader button icon opacity",
                        )
                    val labelAlpha by
                        animateFloatAsState(
                            targetValue = if (isIdleWithoutTag) 0.75f else 1f,
                            animationSpec = tween(durationMillis = 500),
                            label = "Reader button label opacity",
                        )
                    if (showSpinner) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (!isAutomatic && !state.isReaderActive) {
                        val playIconScale = remember { Animatable(1f) }
                        LaunchedEffect(state.isTagPresent) {
                            if (state.isTagPresent) {
                                playIconScale.animateTo(1.40f, tween(durationMillis = 150))
                                playIconScale.animateTo(1.25f, tween(durationMillis = 200))
                            } else {
                                playIconScale.animateTo(1f, tween(durationMillis = 200))
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription =
                                if (state.isTagPresent) "Card detected" else "No card detected",
                            modifier =
                                Modifier.padding(end = 8.dp).graphicsLayer {
                                    alpha = iconAlpha
                                    scaleX = playIconScale.value
                                    scaleY = playIconScale.value
                                },
                        )
                    }
                    Text(
                        text = buttonText,
                        modifier = Modifier.graphicsLayer { alpha = labelAlpha },
                    )
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
                    result = "IDm: 0123456789ABCDEF\nManufacturer: 2\nNFC System Code: 8008",
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
