package com.kormax.felicatool.felica

import android.nfc.tech.NfcF
import android.util.Log
import com.kormax.felicatool.nfc.AnotherTagDiscoveredException
import com.kormax.felicatool.nfc.TagRediscoveredException
import kotlin.time.Duration

/** Android implementation of FeliCaTarget using NfcF technology */
class AndroidFeliCaTarget(
    private var nfcF: NfcF,
    override var idm: ByteArray,
    override val pmm: Pmm,
    private val ensureSessionActive: () -> Unit = {},
) : FeliCaTarget {
    private val lock = Any()
    private var pendingReaderException: IllegalStateException? = null

    companion object {
        private const val TAG = "AndroidFeliCaTarget"

        /**
         * Creates an AndroidFeliCaTarget from an NfcF instance and tag ID The PMM is obtained by
         * performing a polling command
         */
        suspend fun create(
            nfcF: NfcF,
            tagId: ByteArray,
            ensureSessionActive: () -> Unit = {},
        ): AndroidFeliCaTarget {
            // Create a temporary target for polling
            val tempPmm = Pmm(ByteArray(8)) // Temporary PMM for polling
            val tempTarget = AndroidFeliCaTarget(nfcF, tagId, tempPmm, ensureSessionActive)

            // Perform polling to get PMM
            val pollingCommand = PollingCommand(requestCode = RequestCode.NO_REQUEST)
            val pollingResponse = tempTarget.transceive(pollingCommand)

            // Create the final target with proper PMM
            val actualPmm = Pmm(pollingResponse.pmm)
            return AndroidFeliCaTarget(nfcF, pollingResponse.idm, actualPmm, ensureSessionActive)
        }
    }

    init {
        require(idm.size == 8) { "IDM must be exactly 8 bytes, got ${idm.size}" }
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
            Log.e(TAG, "Transceive failed", e)
            throw e
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
