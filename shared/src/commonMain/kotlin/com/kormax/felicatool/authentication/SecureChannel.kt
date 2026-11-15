package com.kormax.felicatool.authentication

import com.kormax.felicatool.felica.SecureCommand
import com.kormax.felicatool.felica.SecureResponse

interface SecureChannel {
    val scope: DesAuthenticationScope

    suspend fun <T : SecureResponse> transceive(command: SecureCommand<T>): T?

    suspend fun close()
}
