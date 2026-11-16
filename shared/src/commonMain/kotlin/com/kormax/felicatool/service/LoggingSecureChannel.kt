package com.kormax.felicatool.service

import com.kormax.felicatool.authentication.AuthenticationOutcome
import com.kormax.felicatool.authentication.SecureChannel
import com.kormax.felicatool.felica.Authentication2Des
import com.kormax.felicatool.felica.SecureCommand
import com.kormax.felicatool.felica.SecureResponse
import com.kormax.felicatool.service.logging.CommunicationLogLayer

internal class LoggingSecureChannel(
    private val channel: SecureChannel,
    private val log: (Any) -> Unit,
) : SecureChannel by channel {
    override suspend fun <T : SecureResponse> transceive(command: SecureCommand<T>): T? {
        log(command)
        return channel.transceive(command)?.also(log)
    }
}

internal fun ScanSession.loggedChannel(success: AuthenticationOutcome.Success): SecureChannel {
    log(
        Authentication2Des.Response.Plain(
            success.information.issueId,
            success.information.issueParameter,
        ),
        CommunicationLogLayer.PLAIN,
    )
    return LoggingSecureChannel(success.channel) { log(it, CommunicationLogLayer.PLAIN) }
}
