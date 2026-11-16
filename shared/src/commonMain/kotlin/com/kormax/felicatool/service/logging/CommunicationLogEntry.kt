package com.kormax.felicatool.service.logging

import com.kormax.felicatool.felica.FelicaCommand
import com.kormax.felicatool.felica.FelicaResponse
import com.kormax.felicatool.felica.SecureCommand
import com.kormax.felicatool.felica.SecureResponse
import com.kormax.felicatool.felica.requiresTwoBytes

enum class CommunicationLogLayer {
    WIRE,
    PLAIN,
}

data class CommunicationLogEntry(
    val timestamp: Long, // monotonic time in nanoseconds
    val message: Any,
    val layer: CommunicationLogLayer = CommunicationLogLayer.WIRE,
) {
    val code: Short?
        get() =
            when (message) {
                is FelicaCommand<*> -> message.code
                is FelicaResponse -> message.code
                is SecureCommand<*> -> message.code
                is SecureResponse -> message.code
                else -> null
            }

    val codeBytes: ByteArray
        get() {
            val code = code?.toInt() ?: return ByteArray(0)
            return if (code.toShort().requiresTwoBytes()) {
                byteArrayOf((code shr 8).toByte(), code.toByte())
            } else {
                byteArrayOf(code.toByte())
            }
        }

    val data: ByteArray
        get() =
            when (message) {
                is FelicaCommand<*> -> message.toByteArray().withoutFrameHeader()
                is FelicaResponse -> message.toByteArray().withoutFrameHeader()
                is SecureCommand<*> -> message.toByteArray()
                is SecureResponse -> message.toByteArray()
                else -> ByteArray(0)
            }

    private fun ByteArray.withoutFrameHeader(): ByteArray = copyOfRange(1 + codeBytes.size, size)
}
