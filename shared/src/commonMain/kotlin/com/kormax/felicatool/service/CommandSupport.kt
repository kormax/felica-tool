package com.kormax.felicatool.service

/** Enum representing the support status of a FeliCa command */
enum class CommandSupport {
    UNKNOWN,
    SUPPORTED,
    UNSUPPORTED,
}

internal fun CommandSupport.toOutputLabel(): String =
    when (this) {
        CommandSupport.UNKNOWN -> "unknown"
        CommandSupport.SUPPORTED -> "supported"
        CommandSupport.UNSUPPORTED -> "not supported"
    }
