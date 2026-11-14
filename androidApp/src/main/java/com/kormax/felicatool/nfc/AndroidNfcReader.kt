package com.kormax.felicatool.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcF
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.kormax.felicatool.felica.AndroidFeliCaTarget
import com.kormax.felicatool.felica.FeliCaTarget
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class AndroidNfcReader(private val activity: ComponentActivity) :
    NfcReader, DefaultLifecycleObserver, NfcAdapter.ReaderCallback {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)
    private val lock = Any()
    private var activeSession: AndroidNfcReaderSession? = null
    private var currentTag: Tag? = null
    private var ignoredTag: Tag? = null
    private var idleTagJob: Job? = null
    private val tagPresence = MutableStateFlow(false)
    val isTagPresent = tagPresence.asStateFlow()

    init {
        activity.lifecycle.addObserver(this)
    }

    override val capabilities: NfcReaderCapabilities =
        NfcReaderCapabilities(activeSessionDisplaysSystemModel = false)

    override val isAvailable: Boolean
        get() = adapter != null

    override val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    override fun onTagDiscovered(tag: Tag) {
        synchronized(lock) {
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
            currentTag = tag
            tagPresence.value = true
            val session = activeSession
            if (session != null) session.handleTag(tag) else watchIdleTagLocked(tag)
        }
    }

    override fun onTagLost(tag: Tag) {
        synchronized(lock) {
            if (currentTag?.id?.contentEquals(tag.id) != true) return
            currentTag = null
            tagPresence.value = false
            idleTagJob?.cancel()
            activeSession?.handleTagLost(tag)
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        synchronized(lock) {
            ignoredTag?.let { ignoreTag(it, 0) }
            ignoredTag = null
            resetReaderModeLocked(IDLE_TAG_PRESENCE_CHECK_DELAY_MILLIS)
        }
    }

    private fun resetReaderModeLocked(presenceCheckDelay: Int) {
        idleTagJob?.cancel()
        idleTagJob = null
        currentTag = null
        tagPresence.value = false
        // Reset the native connection even when another app received the current tag.
        adapter?.disableReaderMode(activity)
        configureReaderMode(presenceCheckDelay)
    }

    private fun configureReaderMode(presenceCheckDelay: Int) {
        val flags =
            NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        val options =
            Bundle().apply {
                putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, presenceCheckDelay)
            }
        adapter?.enableReaderMode(
            activity,
            this,
            flags,
            options,
        )
    }

    override fun onPause(owner: LifecycleOwner) {
        val (tag, session) =
            synchronized(lock) {
                idleTagJob?.cancel()
                idleTagJob = null
                val tag = currentTag
                currentTag = null
                tagPresence.value = false
                tag to activeSession.also { activeSession = null }
            }
        ignoredTag = if (tag != null && ignoreTag(tag, PAUSED_TAG_DEBOUNCE_MILLIS)) tag else null
        session?.closeWithException(ActivitySuspendedException())
        adapter?.disableReaderMode(activity)
    }

    override fun startReaderSession(): NfcReaderSession {
        val nfcAdapter =
            adapter ?: throw IllegalStateException("NFC is not supported on this device")
        if (!nfcAdapter.isEnabled) {
            throw IllegalStateException("NFC is disabled. Please enable it in Settings")
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            throw ActivitySuspendedException()
        }

        return synchronized(lock) {
            check(activeSession == null) { "An NFC reader session is already active" }
            resetReaderModeLocked(SCAN_TAG_PRESENCE_CHECK_DELAY_MILLIS)
            val session = AndroidNfcReaderSession(activity, nfcAdapter, ::onSessionClosed)
            activeSession = session
            session
        }
    }

    private fun onSessionClosed(session: AndroidNfcReaderSession) {
        activity.runOnUiThread {
            synchronized(lock) {
                if (activeSession !== session) return@runOnUiThread
                activeSession = null
                if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    resetReaderModeLocked(IDLE_TAG_PRESENCE_CHECK_DELAY_MILLIS)
                }
            }
        }
    }

    private fun ignoreTag(tag: Tag, debounceMillis: Int): Boolean = runCatching {
        adapter?.ignore(tag, debounceMillis, null, null) == true
    }
        .onFailure { Log.w(TAG, "Unable to ignore NFC tag", it) }
        .getOrDefault(false)

    private fun watchIdleTagLocked(tag: Tag) {
        idleTagJob?.cancel()
        idleTagJob =
            activity.lifecycleScope.launch(tagConnectionDispatcher) {
                val nfcF = NfcF.get(tag) ?: return@launch
                try {
                    if (synchronized(lock) { currentTag !== tag || activeSession != null })
                        return@launch
                    nfcF.connect()
                    while (nfcF.isConnected) {
                        delay(IDLE_TAG_PRESENCE_CHECK_DELAY_MILLIS.toLong())
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A lost or outdated native handle is no longer an idle card in range.
                } finally {
                    val stillIdle =
                        synchronized(lock) {
                            if (currentTag !== tag || activeSession != null) false
                            else {
                                currentTag = null
                                tagPresence.value = false
                                true
                            }
                        }
                    if (stillIdle) runCatching { nfcF.close() }
                }
            }
    }
}

private const val TAG = "AndroidNfcReader"
private const val IDLE_TAG_PRESENCE_CHECK_DELAY_MILLIS = 300
private const val SCAN_TAG_PRESENCE_CHECK_DELAY_MILLIS = 30000
private const val PAUSED_TAG_DEBOUNCE_MILLIS = 10000

// Finish cleanup of one tag handle before connecting the next one.
private val tagConnectionDispatcher = Dispatchers.IO.limitedParallelism(1)

private class AndroidNfcReaderSession(
    private val activity: ComponentActivity,
    private val adapter: NfcAdapter,
    private val onClosed: (AndroidNfcReaderSession) -> Unit,
) : NfcReaderSession {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + tagConnectionDispatcher)

    private var closeCause: Exception? = null
    private var pendingDiscovery: CancellableContinuation<FeliCaTarget>? = null
    private var pendingTag: Tag? = null
    private var currentNfcF: NfcF? = null
    private var currentTarget: AndroidFeliCaTarget? = null

    override suspend fun discoverFeliCaTarget(timeout: Duration): FeliCaTarget {
        val discovery: suspend () -> FeliCaTarget = {
            while (true) {
                val targetToDrop =
                    synchronized(lock) {
                        ensureActiveLocked()
                        currentTarget.also {
                            if (it != null) {
                                currentTarget = null
                                currentNfcF = null
                            }
                        }
                    } ?: break

                targetToDrop.drop()
            }

            suspendCancellableCoroutine { continuation ->
                synchronized(lock) {
                    ensureActiveLocked()
                    check(pendingDiscovery == null) {
                        "A tag discovery request is already active for this reader session"
                    }
                    val tag = pendingTag
                    pendingTag = null
                    if (tag == null) pendingDiscovery = continuation
                    else connectTagLocked(tag, continuation)
                }

                continuation.invokeOnCancellation {
                    synchronized(lock) {
                        if (pendingDiscovery === continuation) pendingDiscovery = null
                    }
                }
            }
        }

        return if (timeout.isInfinite()) {
            discovery()
        } else {
            withTimeout(timeout) { discovery() }
        }
    }

    override fun close() {
        closeWithException(CancellationException("NFC reader session closed"))
    }

    fun handleTag(tag: Tag) {
        Log.i(TAG, "Discovered tag: $tag")
        val replacedTarget: AndroidFeliCaTarget?

        synchronized(lock) {
            if (closeCause != null) {
                return
            }

            replacedTarget = currentTarget
            currentTarget = null
            currentNfcF = null
            val discovery = pendingDiscovery
            pendingDiscovery = null
            if (discovery == null) pendingTag = tag else connectTagLocked(tag, discovery)
        }

        if (replacedTarget != null) {
            Log.i(TAG, "Marking previous NFC tag unavailable after rediscovery")
            replacedTarget.markUnavailable(
                NfcTargetUnavailableException("NFC target was replaced by a rediscovered target"),
            )
        }
    }

    fun handleTagLost(tag: Tag) {
        Log.i(TAG, "Lost tag: $tag")
        val target =
            synchronized(lock) {
                if (closeCause != null) {
                    return
                }

                pendingTag = null
                currentTarget.also {
                    currentTarget = null
                    currentNfcF = null
                }
            }

        target?.markUnavailable(NfcTargetUnavailableException("NFC target was removed"))
    }

    private fun connectTagLocked(tag: Tag, discovery: CancellableContinuation<FeliCaTarget>) {
        val nfcF = NfcF.get(tag)
        if (nfcF == null) {
            discovery.resumeWithException(
                IllegalStateException("Discovered tag does not support FeliCa"),
            )
            return
        }

        currentNfcF = nfcF
        val discoveryJob = scope.launch {
            try {
                synchronized(lock) { ensureConnectingTagLocked(nfcF) }
                nfcF.connect()

                val target =
                    AndroidFeliCaTarget.create(
                        adapter = adapter,
                        nfcF = nfcF,
                        readerSession = this@AndroidNfcReaderSession,
                        tagId = tag.id,
                        ensureSessionAvailable = { ensureActive() },
                    )
                synchronized(lock) {
                    ensureConnectingTagLocked(nfcF)
                    currentTarget = target
                }
                if (discovery.isActive) {
                    discovery.resume(target)
                } else {
                    val ownsTarget =
                        synchronized(lock) {
                            if (currentTarget !== target) false
                            else {
                                currentNfcF = null
                                currentTarget = null
                                true
                            }
                        }
                    target.markUnavailable(
                        NfcTargetUnavailableException("NFC target discovery was cancelled"),
                    )
                    if (ownsTarget) runCatching { nfcF.close() }
                }
            } catch (e: Exception) {
                val failure =
                    synchronized(lock) {
                        val cause =
                            when {
                                currentNfcF !== nfcF || e is TagLostException ->
                                    NfcTargetUnavailableException(
                                        "NFC target was lost during discovery",
                                        e,
                                    )
                                else -> e
                            }
                        if (currentNfcF === nfcF) {
                            currentNfcF = null
                            currentTarget = null
                        }
                        closeCause ?: cause
                    }
                runCatching { nfcF.close() }
                if (discovery.isActive) discovery.resumeWithException(failure)
            }
        }
        discoveryJob.invokeOnCompletion { cause ->
            // Closing the session can cancel this task before its body runs.
            if (cause is CancellationException) discovery.cancel(cause)
        }
    }

    private fun ensureConnectingTagLocked(nfcF: NfcF) {
        ensureActiveLocked()
        if (currentNfcF !== nfcF) {
            throw NfcTargetUnavailableException("NFC target was lost during discovery")
        }
    }

    fun closeWithException(exception: Exception) {
        val discovery: CancellableContinuation<FeliCaTarget>?
        val target: AndroidFeliCaTarget?
        val nfcF: NfcF?

        synchronized(lock) {
            if (closeCause != null) {
                return
            }

            closeCause = exception
            discovery = pendingDiscovery
            pendingDiscovery = null
            pendingTag = null
            target = currentTarget
            currentTarget = null
            // A discovery still connecting owns its handle until it publishes a target.
            nfcF = if (target != null) currentNfcF else null
            currentNfcF = null
        }

        scope.launch {
            runCatching { nfcF?.close() }
            scope.cancel()
        }
        onClosed(this)
        if (target != null) {
            target.markUnavailable(NfcTargetUnavailableException("NFC reader session closed"))
        }

        if (discovery?.isActive == true) {
            discovery.resumeWithException(exception)
        }
    }

    private fun ensureActive() {
        synchronized(lock) { ensureActiveLocked() }
    }

    private fun ensureActiveLocked() {
        closeCause?.let { throw it }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            throw ActivitySuspendedException()
        }
    }
}
