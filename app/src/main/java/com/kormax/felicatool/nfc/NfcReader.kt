package com.kormax.felicatool.nfc

import com.kormax.felicatool.felica.FeliCaTarget
import kotlin.time.Duration

data class NfcReaderCapabilities(
    /**
     * Maximum duration for one reader session. Duration.INFINITE means the reader does not impose a
     * fixed maximum.
     */
    val maximumReaderSessionDuration: Duration = Duration.INFINITE,
    /**
     * Whether an active reader session is surfaced by the operating system outside the app UI.
     * Background reading is only app-controlled when this is false.
     */
    val activeSessionDisplaysSystemModel: Boolean = false,
)

enum class NfcTagTechnology {
    FeliCa
}

interface NfcReader {
    val capabilities: NfcReaderCapabilities
    val isAvailable: Boolean
    val isEnabled: Boolean

    fun startReaderSession(): NfcReaderSession
}

interface NfcReaderSession : AutoCloseable {
    suspend fun discoverTagTechnologies(
        primary: List<NfcTagTechnology> = emptyList(),
        timeout: Duration = Duration.INFINITE,
    ): NfcTag

    override fun close()
}

sealed interface NfcTag : AutoCloseable {
    override fun close()

    class FeliCa(val target: FeliCaTarget, private val onClose: () -> Unit) : NfcTag {
        override fun close() {
            onClose()
        }
    }
}

class ActivitySuspendedException :
    IllegalStateException("Reader session was suspended because the activity moved to background")

class TagRediscoveredException :
    IllegalStateException("Active NFC tag was rediscovered by the reader session")

class AnotherTagDiscoveredException :
    IllegalStateException("Another NFC tag was discovered by the reader session")
