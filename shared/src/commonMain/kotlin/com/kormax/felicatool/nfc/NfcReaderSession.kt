package com.kormax.felicatool.nfc

import com.kormax.felicatool.felica.FeliCaTarget
import kotlin.time.Duration

interface NfcReaderSession : AutoCloseable {
    suspend fun discoverFeliCaTarget(timeout: Duration = Duration.INFINITE): FeliCaTarget

    override fun close()
}
