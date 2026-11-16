package com.kormax.felicatool.felica

interface EncryptedResponse : FelicaResponse {
    val encryptedData: ByteArray
}

interface EncryptedCommand<T : EncryptedResponse> : FelicaCommand<T> {
    val encryptedData: ByteArray
}

interface SecureResponse {
    val code: Short

    fun toByteArray(): ByteArray
}

interface SecureCommand<out T : SecureResponse> {
    val code: Short

    fun toByteArray(): ByteArray

    fun encrypted(encryptedData: ByteArray): EncryptedCommand<*>

    fun responseFromByteArray(data: ByteArray): T
}
