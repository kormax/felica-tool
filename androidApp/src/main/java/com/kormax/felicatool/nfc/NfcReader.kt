package com.kormax.felicatool.nfc

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

interface NfcReader {
    val capabilities: NfcReaderCapabilities
    val isAvailable: Boolean
    val isEnabled: Boolean

    fun startReaderSession(): NfcReaderSession
}

class ActivitySuspendedException :
    IllegalStateException("Reader session was suspended because the activity moved to background")
