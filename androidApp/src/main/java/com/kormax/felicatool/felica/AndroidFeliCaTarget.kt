package com.kormax.felicatool.felica

import android.nfc.TagLostException as AndroidTagLostException
import android.nfc.tech.NfcF
import android.util.Log
import com.kormax.felicatool.nfc.AnotherTagDiscoveredException
import com.kormax.felicatool.nfc.TagLostException
import com.kormax.felicatool.nfc.TagRediscoveredException
import kotlin.time.Duration

/** Android implementation of FeliCaTarget using NfcF technology */
class AndroidFeliCaTarget(
    private var nfcF: NfcF,
    override val initialIdm: ByteArray,
    override val pmm: Pmm,
    override val initialSystemCode: ByteArray? = null,
    override var currentIdm: ByteArray = initialIdm,
    override var currentSystemCode: ByteArray? = initialSystemCode,
    private val ensureSessionActive: () -> Unit = {},
) : FeliCaTarget {
    private val lock = Any()
    private var pendingReaderException: IllegalStateException? = null

    companion object {
        private const val TAG = "AndroidFeliCaTarget"

        /**
         * Creates an AndroidFeliCaTarget from an NfcF instance and tag ID. Android discovery
         * metadata is used when available; polling is used as a fallback when PMM is unavailable.
         */
        suspend fun create(
            nfcF: NfcF,
            tagId: ByteArray,
            ensureSessionActive: () -> Unit = {},
        ): AndroidFeliCaTarget {
            val discoveredSystemCode = nfcF.getSystemCode()?.takeIf { it.size == 2 }?.copyOf()
            val discoveredPmm =
                nfcF.getManufacturer()?.takeIf { it.size == 8 }?.let { Pmm(it.copyOf()) }
            if (discoveredPmm != null) {
                return AndroidFeliCaTarget(
                    nfcF = nfcF,
                    initialIdm = tagId,
                    pmm = discoveredPmm,
                    initialSystemCode = discoveredSystemCode,
                    ensureSessionActive = ensureSessionActive,
                )
            }

            // Perform polling to get PMM
            val pollingCommand = PollingCommand(requestCode = RequestCode.NO_REQUEST)
            val pollingResponse =
                PollingResponse.fromByteArray(nfcF.transceive(pollingCommand.toByteArray()))

            // Create the final target with proper PMM
            val actualPmm = Pmm(pollingResponse.pmm)
            return AndroidFeliCaTarget(
                nfcF = nfcF,
                initialIdm = tagId,
                pmm = actualPmm,
                initialSystemCode = discoveredSystemCode,
                currentIdm = pollingResponse.idm,
                currentSystemCode = null,
                ensureSessionActive = ensureSessionActive,
            )
        }
    }

    init {
        require(initialIdm.size == 8) { "IDM must be exactly 8 bytes, got ${initialIdm.size}" }
        require(currentIdm.size == 8) {
            "Current IDM must be exactly 8 bytes, got ${currentIdm.size}"
        }
        initialSystemCode?.let {
            require(it.size == 2) { "Initial system code must be exactly 2 bytes, got ${it.size}" }
        }
        currentSystemCode?.let {
            require(it.size == 2) { "Current system code must be exactly 2 bytes, got ${it.size}" }
        }
    }

    internal fun replaceNativeTag(nfcF: NfcF): NfcF =
        synchronized(lock) {
            val previousNfcF = this.nfcF
            this.nfcF = nfcF
            pendingReaderException = TagRediscoveredException()
            previousNfcF
        }

    internal fun reportAnotherTagDiscovered() {
        synchronized(lock) { pendingReaderException = AnotherTagDiscoveredException() }
    }

    internal fun closeNativeTag() {
        val currentNfcF = synchronized(lock) { nfcF }
        runCatching { currentNfcF.close() }
    }

    override suspend fun transceive(data: ByteArray, timeout: Duration?): ByteArray {
        ensureSessionActive()
        val currentNfcF = currentNativeTag()
        Log.d(TAG, "Sending command: ${data.toHexString()}")

        // Set timeout if provided
        timeout?.let {
            val timeoutMs = it.inWholeMilliseconds.toInt()
            Log.d(TAG, "Set timeout to ${timeoutMs}ms")
            currentNfcF.timeout = timeoutMs
        }

        try {
            throwPendingReaderException()
            val responseBytes = currentNfcF.transceive(data)
            ensureSessionActive()
            throwPendingReaderException()
            Log.d(TAG, "Received response: ${responseBytes.toHexString()}")
            return responseBytes
        } catch (e: Exception) {
            ensureSessionActive()
            throwPendingReaderException()
            val mappedException =
                if (
                    e is AndroidTagLostException ||
                        (e is SecurityException &&
                            e.message?.contains("out of date", ignoreCase = true) == true)
                ) {
                    TagLostException(e)
                } else {
                    e
                }
            Log.e(TAG, "Transceive failed", mappedException)
            throw mappedException
        }
    }

    private fun currentNativeTag(): NfcF =
        synchronized(lock) {
            pendingReaderException?.let {
                pendingReaderException = null
                throw it
            }
            nfcF
        }

    private fun throwPendingReaderException() {
        val exception =
            synchronized(lock) { pendingReaderException.also { pendingReaderException = null } }
        if (exception != null) {
            throw exception
        }
    }
}
