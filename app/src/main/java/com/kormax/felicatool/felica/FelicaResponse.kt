package com.kormax.felicatool.felica

/** Base interface for all FeliCa responses */
interface FelicaResponse {
    /** Converts the response to a byte array */
    fun toByteArray(): ByteArray
}
