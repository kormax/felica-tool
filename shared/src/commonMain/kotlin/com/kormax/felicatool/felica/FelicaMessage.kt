package com.kormax.felicatool.felica

/** Base class for all FeliCa messages (commands and responses) */
abstract class FelicaMessage {
    /** Converts the message to a byte array for transmission */
    abstract fun toByteArray(): ByteArray
}

val Any.messageName: String
    get() =
        (this::class.qualifiedName?.removePrefix("com.kormax.felicatool.felica.")
                ?: this::class.simpleName
                ?: "FelicaMessage")
            .replace('$', '.')
