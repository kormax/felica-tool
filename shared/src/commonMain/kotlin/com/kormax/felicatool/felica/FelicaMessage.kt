package com.kormax.felicatool.felica

/** Base class for all FeliCa messages (commands and responses) */
abstract class FelicaMessage {
    /** Converts the message to a byte array for transmission */
    abstract fun toByteArray(): ByteArray
}
