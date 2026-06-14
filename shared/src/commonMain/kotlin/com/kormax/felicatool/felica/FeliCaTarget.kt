package com.kormax.felicatool.felica

import com.kormax.felicatool.nfc.NfcReaderSession
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Interface representing a FeliCa target that can be communicated with. This abstraction allows for
 * different implementations (Android NFC, test mocks, etc.)
 */
interface FeliCaTarget {
    /** Reader session that created this target. */
    val readerSession: NfcReaderSession

    /** IDM observed when the target was first acquired by the reader session. */
    val initialIdm: ByteArray

    /** System code observed when the target was first acquired by the reader session, if known. */
    val initialSystemCode: ByteArray?

    /** IDM for the system the target is currently expected to address. */
    var currentIdm: ByteArray

    /** System code for the system the target is currently expected to address, if known. */
    var currentSystemCode: ByteArray?

    /** The card's current IDM (8 bytes) - unique identifier. */
    var idm: ByteArray
        get() = currentIdm
        set(value) {
            currentIdm = value
        }

    /** The card's PMM (8 bytes) - Product Manufacturing Model */
    val pmm: Pmm

    /** Current platform-selected system code, when known. */
    val systemCode: ByteArray?
        get() = currentSystemCode

    /** Whether this target can still perform I/O. */
    val isAvailable: Boolean

    /** Drops this target from the active reader field and makes it unavailable for future I/O. */
    suspend fun drop()

    /**
     * Raw transceive operation - sends raw bytes and receives raw bytes
     *
     * @param data The command data to send
     * @param timeout Optional timeout override. If null, uses default timeout
     * @return The response data received from the card
     */
    suspend fun transceive(data: ByteArray, timeout: Duration? = null): ByteArray

    /** Infers the appropriate timeout for a command based on PMM and command type */
    fun inferTimeout(command: FelicaCommand<*>): Duration {
        // Get command class from the command itself
        val commandClass = command.commandClass

        // Get number of units from the command itself
        val units = command.timeoutUnits

        // Return the calculated timeout with 50ms margin
        return pmm.totalTimeout(commandClass, units) + 50.toDuration(DurationUnit.MILLISECONDS)
    }
}
