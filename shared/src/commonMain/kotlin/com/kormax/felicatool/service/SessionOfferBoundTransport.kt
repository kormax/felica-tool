package com.kormax.felicatool.service

import com.kormax.felicatool.authentication.DesAuthenticationScope
import com.kormax.felicatool.authentication.OfferBoundAuthenticationTransport
import com.kormax.felicatool.felica.Authentication1Des
import com.kormax.felicatool.felica.Authentication2Des
import com.kormax.felicatool.felica.EncryptedCommand
import com.kormax.felicatool.felica.EncryptedResponse
import com.kormax.felicatool.nfc.TransceiveTimeoutException

internal class SessionOfferBoundTransport(
    private val session: ScanSession,
    override val scope: DesAuthenticationScope,
) : OfferBoundAuthenticationTransport {
    override val systemCode: String = scope.systemCode
    override val idm: String = scope.idm

    override suspend fun authentication1(
        challenge1A: ByteArray,
        attempts: Int,
    ): Authentication1Des.Response? =
        try {
            session.executeCommand(
                withSelectedSystemCode = systemCode.hexToByteArray(),
                attempts = attempts,
            ) {
                Authentication1Des.Command(
                    this@SessionOfferBoundTransport.idm.hexToByteArray(),
                    scope.areaCodes.map { it.hexToByteArray() }.toTypedArray(),
                    scope.nodeCodes.map { it.hexToByteArray() }.toTypedArray(),
                    challenge1A,
                )
            }
        } catch (_: TransceiveTimeoutException) {
            null
        }

    override suspend fun authentication2(
        challenge2B: ByteArray,
        attempts: Int,
    ): Authentication2Des.Response.Encrypted? =
        try {
            session.executeCommand(
                withSelectedSystemCode = systemCode.hexToByteArray(),
                attempts = attempts,
            ) {
                Authentication2Des.Command(
                    this@SessionOfferBoundTransport.idm.hexToByteArray(),
                    challenge2B,
                )
            }
        } catch (_: TransceiveTimeoutException) {
            null
        }

    override suspend fun <T : EncryptedResponse> transceiveSecure(
        command: EncryptedCommand<T>,
        attempts: Int,
    ): T? =
        try {
            session.executeCommand(
                withSelectedSystemCode = systemCode.hexToByteArray(),
                attempts = attempts,
            ) {
                command
            }
        } catch (_: TransceiveTimeoutException) {
            null
        }

    override suspend fun recoverMode0() {
        session.recoverMode0(systemCode.hexToByteArray(), idm.hexToByteArray())
    }
}
