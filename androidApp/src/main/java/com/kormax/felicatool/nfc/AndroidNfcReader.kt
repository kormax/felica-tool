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
    private var pendingDiscovery: CancellableContinuation<NfcTag>? = null
    private var pendingTag: Tag? = null
    private var currentNfcF: NfcF? = null
    private var currentTagId: ByteArray? = null
    private var currentTarget: AndroidFeliCaTarget? = null

    init {
        activity.lifecycle.addObserver(this)
    }

    override suspend fun discoverTagTechnologies(
        primary: List<NfcTagTechnology>,
        timeout: Duration,
    ): NfcTag {
        val requestedTechnologies = primary.ifEmpty { listOf(NfcTagTechnology.FeliCa) }
        require(requestedTechnologies.all { it == NfcTagTechnology.FeliCa }) {
            "Android NFC reader currently supports FeliCa discovery only"
        }

        val discovery: suspend () -> NfcTag = {
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
            adapter.enableReaderMode(activity, ::handleTag, flags, options)
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
        var rediscoveredTarget: AndroidFeliCaTarget? = null
        var anotherTagTarget: AndroidFeliCaTarget? = null
        val discovery: CancellableContinuation<NfcTag>?

        synchronized(lock) {
            if (closed) {
                return
            }

            discovery = pendingDiscovery.also { pendingDiscovery = null }
            if (discovery == null) {
                val tagId = currentTagId
                val target = currentTarget
                if (tagId != null && target != null && tag.id.contentEquals(tagId)) {
                    rediscoveredTarget = target
                } else if (target != null) {
                    anotherTagTarget = target
                } else {
                    pendingTag = tag
                }
            }
        }

        if (discovery != null) {
            resumeDiscoveryWithTag(tag, discovery)
        } else {
            rediscoveredTarget?.let { replaceRediscoveredTag(tag, it) }
            anotherTagTarget?.let {
                it.reportAnotherTagDiscovered()
                Log.w(TAG, "Another tag discovered while a tag is already active")
            }
        }
    }

    private fun resumeDiscoveryWithTag(tag: Tag, discovery: CancellableContinuation<NfcTag>) {
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
                synchronized(lock) {
                    currentNfcF = nfcF
                    currentTagId = tag.id.copyOf()
                }

                val target = AndroidFeliCaTarget.create(nfcF, tag.id) { ensureActive() }
                synchronized(lock) {
                    if (closed || currentNfcF !== nfcF) {
                        throw CancellationException("NFC reader session is closed")
                    }
                    currentTarget = target
                }
                val nfcTag =
                    NfcTag.FeliCa(target) {
                        synchronized(lock) {
                            if (currentTarget === target) {
                                currentNfcF = null
                                currentTagId = null
                                currentTarget = null
                            }
                        }
                        target.closeNativeTag()
                    }

                if (discovery.isActive) {
                    discovery.resume(nfcTag)
                } else {
                    nfcTag.close()
                }
            } catch (e: Exception) {
                runCatching { nfcF.close() }

                val sessionException =
                    synchronized(lock) {
                        if (currentNfcF === nfcF) {
                            currentNfcF = null
                            currentTagId = null
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

    private fun replaceRediscoveredTag(tag: Tag, target: AndroidFeliCaTarget) {
        val replacementNfcF = NfcF.get(tag)
        if (replacementNfcF == null) {
            Log.w(TAG, "Rediscovered tag does not support FeliCa")
            return
        }

        scope.launch {
            try {
                ensureActive()
                replacementNfcF.connect()
                val previousNfcF =
                    synchronized(lock) {
                        ensureActiveLocked()
                        val tagId = currentTagId
                        if (
                            currentTarget !== target ||
                                tagId == null ||
                                !tag.id.contentEquals(tagId)
                        ) {
                            return@synchronized null
                        }

                        currentNfcF = replacementNfcF
                        target.replaceNativeTag(replacementNfcF)
                    }

                if (previousNfcF == null) {
                    replacementNfcF.close()
                    return@launch
                }

                runCatching { previousNfcF.close() }
                Log.i(TAG, "Replaced active native tag after rediscovery")
            } catch (e: Exception) {
                runCatching { replacementNfcF.close() }
                val sessionException = synchronized(lock) { suspendedException }
                if (sessionException == null) {
                    Log.w(TAG, "Unable to replace rediscovered tag", e)
                }
            }
        }
    }

    private fun closeWithException(exception: Throwable) {
        val discovery: CancellableContinuation<NfcTag>?
        val nfcF: NfcF?

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
            currentTagId = null
            currentTarget = null
            nfcF = currentNfcF
            currentNfcF = null
        }

        activity.lifecycle.removeObserver(this)
        disableReaderMode()
        runCatching { nfcF?.close() }

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
