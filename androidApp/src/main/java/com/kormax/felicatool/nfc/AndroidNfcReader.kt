package com.kormax.felicatool.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.kormax.felicatool.felica.AndroidFeliCaTarget
import com.kormax.felicatool.felica.FeliCaTarget
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout

class AndroidNfcReader(private val activity: ComponentActivity) : NfcReader {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    fun disableBackgroundDiscovery() {
        adapter?.let { disableDiscoveryTechnologyForActivity(it, activity) }
    }

    override val capabilities: NfcReaderCapabilities =
        NfcReaderCapabilities(activeSessionDisplaysSystemModel = false)

    override val isAvailable: Boolean
        get() = adapter != null

    override val isEnabled: Boolean
        get() = adapter?.isEnabled == true

    override fun startReaderSession(): NfcReaderSession {
        val nfcAdapter =
            adapter ?: throw IllegalStateException("NFC is not supported on this device")
        if (!nfcAdapter.isEnabled) {
            throw IllegalStateException("NFC is disabled. Please enable it in Settings")
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            throw ActivitySuspendedException()
        }

        return AndroidNfcReaderSession(activity, nfcAdapter)
    }
}

private fun disableDiscoveryTechnologyForActivity(
    adapter: NfcAdapter,
    activity: ComponentActivity,
) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        return
    }

    runCatching {
            adapter.setDiscoveryTechnology(
                activity,
                NfcAdapter.FLAG_READER_DISABLE,
                NfcAdapter.FLAG_LISTEN_DISABLE,
            )
        }
        .onFailure { Log.w(TAG, "Unable to disable default NFC discovery technology", it) }
}

private const val TAG = "AndroidNfcReader"

private class AndroidNfcReaderSession(
    private val activity: ComponentActivity,
    private val adapter: NfcAdapter,
) : NfcReaderSession, DefaultLifecycleObserver {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var closed = false
    private var suspendedException: ActivitySuspendedException? = null
    private var pendingDiscovery: CancellableContinuation<FeliCaTarget>? = null
    private var pendingTag: Tag? = null
    private var currentNfcF: NfcF? = null
    private var currentTarget: AndroidFeliCaTarget? = null

    init {
        activity.lifecycle.addObserver(this)
    }

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

            val queuedTag =
                synchronized(lock) {
                    ensureActiveLocked()
                    pendingTag.also { pendingTag = null }
                }
            if (queuedTag != null) {
                suspendCancellableCoroutine { continuation ->
                    resumeDiscoveryWithTag(queuedTag, continuation)
                }
            } else {
                suspendCancellableCoroutine { continuation ->
                    synchronized(lock) {
                        ensureActiveLocked()
                        check(pendingDiscovery == null) {
                            "A tag discovery request is already active for this reader session"
                        }

                        pendingDiscovery = continuation
                    }

                    continuation.invokeOnCancellation {
                        synchronized(lock) {
                            if (pendingDiscovery === continuation) {
                                pendingDiscovery = null
                            }
                        }
                    }

                    enableReaderMode()
                }
            }
        }

        return if (timeout.isInfinite()) {
            discovery()
        } else {
            withTimeout(timeout) { discovery() }
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        closeWithException(ActivitySuspendedException())
    }

    override fun close() {
        closeWithException(CancellationException("NFC reader session closed"))
    }

    private fun enableReaderMode() {
        activity.runOnUiThread {
            synchronized(lock) {
                if (closed) {
                    return@runOnUiThread
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                runCatching { adapter.resetDiscoveryTechnology(activity) }
                    .onFailure { Log.w(TAG, "Unable to reset NFC discovery technology", it) }
            }
            val flags = NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS
            val options =
                Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 30000) }
            adapter.enableReaderMode(
                activity,
                object : NfcAdapter.ReaderCallback {
                    override fun onTagDiscovered(tag: Tag) {
                        handleTag(tag)
                    }

                    override fun onTagLost(tag: Tag) {
                        handleTagLost(tag)
                    }
                },
                flags,
                options,
            )
        }
    }

    private fun disableReaderMode() {
        activity.runOnUiThread {
            adapter.disableReaderMode(activity)
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                disableDiscoveryTechnologyForActivity(adapter, activity)
            }
        }
    }

    private fun handleTag(tag: Tag) {
        Log.i(TAG, "Discovered tag: $tag")
        val discovery: CancellableContinuation<FeliCaTarget>?
        val shouldHandleTag: Boolean
        val replacedTarget: AndroidFeliCaTarget?

        synchronized(lock) {
            if (closed) {
                return
            }

            replacedTarget =
                if (currentTarget != null) {
                    currentTarget.also {
                        currentTarget = null
                        currentNfcF = null
                    }
                } else {
                    null
                }
            shouldHandleTag = currentTarget == null
            discovery =
                if (shouldHandleTag) {
                    pendingDiscovery.also { pendingDiscovery = null }
                } else {
                    null
                }
            if (shouldHandleTag && discovery == null) {
                pendingTag = tag
            }
        }

        if (!shouldHandleTag) {
            Log.i(TAG, "Ignoring tag callback while another NFC tag is active")
            return
        }

        if (replacedTarget != null) {
            Log.i(TAG, "Marking previous NFC tag unavailable after rediscovery")
            replacedTarget.markUnavailable(
                NfcTargetUnavailableException("NFC target was replaced by a rediscovered target")
            )
        }

        if (discovery != null) {
            resumeDiscoveryWithTag(tag, discovery)
        }
    }

    private fun handleTagLost(tag: Tag) {
        Log.i(TAG, "Lost tag: $tag")
        val target =
            synchronized(lock) {
                if (closed) {
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

    private fun resumeDiscoveryWithTag(tag: Tag, discovery: CancellableContinuation<FeliCaTarget>) {
        val nfcF = NfcF.get(tag)
        if (nfcF == null) {
            discovery.resumeWithException(
                IllegalStateException("Discovered tag does not support FeliCa")
            )
            return
        }

        scope.launch {
            try {
                ensureActive()
                nfcF.connect()
                synchronized(lock) { currentNfcF = nfcF }

                val target =
                    AndroidFeliCaTarget.create(
                        adapter = adapter,
                        nfcF = nfcF,
                        readerSession = this@AndroidNfcReaderSession,
                        tagId = tag.id,
                        ensureSessionAvailable = { ensureActive() },
                    )
                synchronized(lock) {
                    if (closed || currentNfcF !== nfcF) {
                        throw CancellationException("NFC reader session is closed")
                    }
                    currentTarget = target
                }
                if (discovery.isActive) {
                    discovery.resume(target)
                } else {
                    synchronized(lock) {
                        if (currentTarget === target) {
                            currentNfcF = null
                            currentTarget = null
                        }
                    }
                    target.markUnavailable(
                        NfcTargetUnavailableException("NFC target discovery was cancelled")
                    )
                }
            } catch (e: Exception) {
                val sessionException =
                    synchronized(lock) {
                        if (currentNfcF === nfcF) {
                            currentNfcF = null
                            currentTarget = null
                        }
                        suspendedException
                    }

                if (discovery.isActive) {
                    discovery.resumeWithException(sessionException ?: e)
                }
            }
        }
    }

    private fun closeWithException(exception: Throwable) {
        val discovery: CancellableContinuation<FeliCaTarget>?
        val target: AndroidFeliCaTarget?

        synchronized(lock) {
            if (closed) {
                return
            }

            closed = true
            if (exception is ActivitySuspendedException) {
                suspendedException = exception
            }
            discovery = pendingDiscovery
            pendingDiscovery = null
            pendingTag = null
            target = currentTarget
            currentTarget = null
            currentNfcF = null
        }

        activity.lifecycle.removeObserver(this)
        disableReaderMode()
        if (target != null) {
            target.markUnavailable(NfcTargetUnavailableException("NFC reader session closed"))
        }

        if (discovery?.isActive == true) {
            discovery.resumeWithException(exception)
        }

        scope.cancel()
    }

    private fun ensureActive() {
        synchronized(lock) { ensureActiveLocked() }
    }

    private fun ensureActiveLocked() {
        suspendedException?.let { throw it }
        if (closed) {
            throw CancellationException("NFC reader session is closed")
        }
        if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            throw ActivitySuspendedException()
        }
    }
}
