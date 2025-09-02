package com.kormax.felicatool.felica

import android.nfc.tech.NfcF
import android.util.Log
import kotlin.time.Duration

/** Android implementation of FeliCaTarget using NfcF technology */
class AndroidFeliCaTarget(
    private val nfcF: NfcF,
    override var idm: ByteArray,
    override val pmm: Pmm,
) : FeliCaTarget {

    companion object {
        private const val TAG = "AndroidFeliCaTarget"

        /**
         * Creates an AndroidFeliCaTarget from an NfcF instance and tag ID The PMM is obtained by
         * performing a polling command
         */
        suspend fun create(nfcF: NfcF, tagId: ByteArray): AndroidFeliCaTarget {
            // Create a temporary target for polling
            val tempPmm = Pmm(ByteArray(8)) // Temporary PMM for polling
            val tempTarget = AndroidFeliCaTarget(nfcF, tagId, tempPmm)

            // Perform polling to get PMM
            val pollingCommand = PollingCommand(requestCode = RequestCode.NO_REQUEST)
            val pollingResponse = tempTarget.transceive(pollingCommand)

            // Create the final target with proper PMM
            val actualPmm = Pmm(pollingResponse.pmm)
            return AndroidFeliCaTarget(nfcF, pollingResponse.idm, actualPmm)
        }
    }

    init {
        require(idm.size == 8) { "IDM must be exactly 8 bytes, got ${idm.size}" }
    }

    override suspend fun transceive(data: ByteArray, timeout: Duration?): ByteArray {
        Log.d(TAG, "Sending command: ${data.toHexString()}")

        // Set timeout if provided
        timeout?.let {
            val timeoutMs = it.inWholeMilliseconds.toInt()
            Log.d(TAG, "Set timeout to ${timeoutMs}ms")
            nfcF.timeout = timeoutMs
        }

        try {
            val responseBytes = nfcF.transceive(data)
            Log.d(TAG, "Received response: ${responseBytes.toHexString()}")
            return responseBytes
        } catch (e: Exception) {
            Log.e(TAG, "Transceive failed", e)
            throw e
        }
    }
}
