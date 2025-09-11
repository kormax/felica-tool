package com.kormax.felicatool.service.logging

data class CommunicationLogEntry(
    val type: Type,
    val timestamp: Long, // monotonic time in nanoseconds
    val data: ByteArray?,
    val name: String? = null,
) {
    enum class Type {
        COMMAND,
        RESPONSE,
    }
}
