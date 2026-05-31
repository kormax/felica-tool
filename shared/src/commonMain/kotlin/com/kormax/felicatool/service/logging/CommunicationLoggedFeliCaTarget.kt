package com.kormax.felicatool.service.logging

import com.kormax.felicatool.felica.FeliCaTarget
import com.kormax.felicatool.felica.FelicaCommand
import com.kormax.felicatool.felica.FelicaResponse
import com.kormax.felicatool.felica.Pmm
import com.kormax.felicatool.nfc.NfcReaderSession
import kotlin.time.Duration
import kotlin.time.TimeSource

class CommunicationLoggedFeliCaTarget(private var target: FeliCaTarget) : FeliCaTarget {
    private val _log: MutableList<CommunicationLogEntry> = mutableListOf()

    val log: List<CommunicationLogEntry>
        get() = _log

    override val readerSession: NfcReaderSession
        get() = target.readerSession

    override val initialIdm: ByteArray
        get() = target.initialIdm

    override val initialSystemCode: ByteArray?
        get() = target.initialSystemCode

    override var currentIdm: ByteArray
        get() = target.currentIdm
        set(value) {
            target.currentIdm = value
        }

    override var currentSystemCode: ByteArray?
        get() = target.currentSystemCode
        set(value) {
            target.currentSystemCode = value
        }

    override val pmm: Pmm
        get() = target.pmm

    override val isAvailable: Boolean
        get() = target.isAvailable

    fun replaceTarget(target: FeliCaTarget) {
        this.target = target
    }

    override suspend fun drop() {
        target.drop()
    }

    override suspend fun transceive(data: ByteArray, timeout: Duration?): ByteArray =
        target.transceive(data, timeout)

    override suspend fun <T : FelicaResponse> transceive(
        command: FelicaCommand<T>,
        timeout: Duration?,
    ): T {
        val now = mark.elapsedNow().inWholeNanoseconds
        _log.add(CommunicationLogEntry(now, command))
        val response = target.transceive(command, timeout)
        val nowResp = mark.elapsedNow().inWholeNanoseconds
        _log.add(CommunicationLogEntry(nowResp, response))
        return response
    }

    companion object {
        val mark = TimeSource.Monotonic.markNow()
    }
}
