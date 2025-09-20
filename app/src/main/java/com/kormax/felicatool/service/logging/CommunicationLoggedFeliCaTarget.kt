package com.kormax.felicatool.service.logging

import com.kormax.felicatool.felica.FeliCaTarget
import com.kormax.felicatool.felica.FelicaCommand
import com.kormax.felicatool.felica.FelicaResponse
import kotlin.time.Duration
import kotlin.time.TimeSource

class CommunicationLoggedFeliCaTarget(private val target: FeliCaTarget) : FeliCaTarget by target {
    private val _log: MutableList<CommunicationLogEntry> = mutableListOf()

    val log: List<CommunicationLogEntry>
        get() = _log

    override suspend fun <T : FelicaResponse> transceive(
        command: FelicaCommand<T>,
        timeout: Duration?,
    ): T {
        val now = mark.elapsedNow().inWholeNanoseconds
        _log.add(
            CommunicationLogEntry(
                CommunicationLogEntry.Type.COMMAND,
                now,
                command.toByteArray(),
                command::class.simpleName,
            )
        )
        val response = target.transceive(command, timeout)
        val nowResp = mark.elapsedNow().inWholeNanoseconds
        _log.add(
            CommunicationLogEntry(
                CommunicationLogEntry.Type.RESPONSE,
                nowResp,
                response.toByteArray(),
                response::class.simpleName,
            )
        )
        return response
    }

    companion object {
        val mark = TimeSource.Monotonic.markNow()
    }
}
