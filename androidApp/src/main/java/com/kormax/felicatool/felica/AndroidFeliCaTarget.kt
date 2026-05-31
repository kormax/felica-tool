package com.kormax.felicatool.felica

import android.nfc.NfcAdapter
import android.nfc.TagLostException as AndroidTagLostException
import android.nfc.tech.NfcF
import android.util.Log
import com.kormax.felicatool.nfc.NfcReaderSession
import com.kormax.felicatool.nfc.TagLostException
import com.kormax.felicatool.nfc.TagUnavailableException
import kotlin.time.Duration

/** Android implementation of FeliCaTarget using NfcF technology */
class AndroidFeliCaTarget(
    private val adapter: NfcAdapter,
    private val nfcF: NfcF,
    override val readerSession: NfcReaderSession,
    override val initialIdm: ByteArray,
    override val pmm: Pmm,
    override val initialSystemCode: ByteArray? = null,
    override var currentIdm: ByteArray = initialIdm,
    override var currentSystemCode: ByteArray? = initialSystemCode,
    private val ensureSessionAvailable: () -> Unit = {},
) : FeliCaTarget {
    private val lock = Any()
    private var unavailableException: IllegalStateException? = null

    override val isAvailable: Boolean
        get() = synchronized(lock) { unavailableException == null }

    companion object {
        private const val TAG = "AndroidFeliCaTarget"

        /**
         * Creates an AndroidFeliCaTarget from an NfcF instance and tag ID. Android discovery
         * metadata is used when available; polling is used as a fallback when PMM is unavailable.
         */
        suspend fun create(
            adapter: NfcAdapter,
            nfcF: NfcF,
            readerSession: NfcReaderSession,
            tagId: ByteArray,
            ensureSessionAvailable: () -> Unit = {},
        ): AndroidFeliCaTarget {
            val discoveredSystemCode = nfcF.getSystemCode()?.takeIf { it.size == 2 }?.copyOf()
            val discoveredPmm =
                nfcF.getManufacturer()?.takeIf { it.size == 8 }?.let { Pmm(it.copyOf()) }
            if (discoveredPmm != null) {
                return AndroidFeliCaTarget(
                    adapter = adapter,
                    nfcF = nfcF,
                    readerSession = readerSession,
                    initialIdm = tagId,
                    pmm = discoveredPmm,
                    initialSystemCode = discoveredSystemCode,
                    ensureSessionAvailable = ensureSessionAvailable,
                )
            }

            // Perform polling to get PMM
            val pollingCommand = PollingCommand(requestCode = RequestCode.NO_REQUEST)
            val pollingResponse =
                PollingResponse.fromByteArray(nfcF.transceive(pollingCommand.toByteArray()))

            // Create the final target with proper PMM
            val actualPmm = Pmm(pollingResponse.pmm)
            return AndroidFeliCaTarget(
                adapter = adapter,
                nfcF = nfcF,
                readerSession = readerSession,
                initialIdm = tagId,
                pmm = actualPmm,
                initialSystemCode = discoveredSystemCode,
                currentIdm = pollingResponse.idm,
                currentSystemCode = null,
                ensureSessionAvailable = ensureSessionAvailable,
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

    override suspend fun drop() {
        if (!markUnavailable(TagUnavailableException())) {
            return
        }
        runCatching { adapter.ignore(nfcF.tag, 0, null, null) }
            .onFailure { Log.w(TAG, "Unable to drop NFC tag from reader field", it) }
    }

    override suspend fun transceive(data: ByteArray, timeout: Duration?): ByteArray {
        ensureSessionAvailable()
        val currentNfcF = ensureNativeTagAvailable()
        Log.d(TAG, "Sending command: ${data.toHexString()}")

        // Set timeout if provided
        timeout?.let {
            val timeoutMs = it.inWholeMilliseconds.toInt()
            Log.d(TAG, "Set timeout to ${timeoutMs}ms")
            currentNfcF.timeout = timeoutMs
        }

        try {
            val responseBytes = currentNfcF.transceive(data)
            ensureSessionAvailable()
            ensureNativeTagAvailable()
            Log.d(TAG, "Received response: ${responseBytes.toHexString()}")
            return responseBytes
        } catch (e: Exception) {
            ensureSessionAvailable()
            ensureNativeTagAvailable()
            val staleTagException =
                e is SecurityException &&
                    e.message?.contains("out of date", ignoreCase = true) == true
            val mappedException =
                when {
                    e is AndroidTagLostException -> TagLostException(e)
                    staleTagException -> TagLostException(e)
                    else -> e
                }
            if (staleTagException && mappedException is TagLostException) {
                markUnavailable(mappedException)
                Log.i(TAG, "Marking NFC tag unavailable after stale tag transceive failure")
            }
            Log.e(TAG, "Transceive failed", mappedException)
            throw mappedException
        }
    }

    private fun ensureNativeTagAvailable(): NfcF =
        synchronized(lock) {
            unavailableException?.let { throw it }
            nfcF
        }

    internal fun markUnavailable(exception: IllegalStateException): Boolean =
        synchronized(lock) {
            if (unavailableException != null) {
                false
            } else {
                unavailableException = exception
                true
            }
        }
}
