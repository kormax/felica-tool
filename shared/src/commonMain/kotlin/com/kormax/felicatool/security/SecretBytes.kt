package com.kormax.felicatool.security

class SecretBytes private constructor(private var bytes: ByteArray?) : AutoCloseable {
    val size: Int
        get() = requireBytes().size

    fun copy(): SecretBytes = fromByteArray(requireBytes())

    fun copyToByteArray(): ByteArray = requireBytes().copyOf()

    override fun close() {
        bytes?.fill(0)
        bytes = null
    }

    override fun toString(): String = "SecretBytes(${bytes?.size ?: 0})"

    private fun requireBytes(): ByteArray = checkNotNull(bytes) { "Secret has already been closed" }

    companion object {
        fun fromByteArray(bytes: ByteArray): SecretBytes = SecretBytes(bytes.copyOf())
    }
}
