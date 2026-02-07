package com.kormax.felicatool.service.logging

data class CommunicationLogEntry(
    val timestamp: Long, // monotonic time in nanoseconds
    val message: Any,
) {
    fun toByteArray(): ByteArray =
        when (message) {
            is com.kormax.felicatool.felica.FelicaCommand<*> -> message.toByteArray()
            is com.kormax.felicatool.felica.FelicaResponse -> message.toByteArray()
            else -> ByteArray(0)
        }
}
