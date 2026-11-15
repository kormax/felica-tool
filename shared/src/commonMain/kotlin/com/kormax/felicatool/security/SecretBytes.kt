package com.kormax.felicatool.security

class SecretBytes private constructor(private var bytes: ByteArray?) : AutoCloseable {
    val size: Int
        get() = requireBytes().size

    fun copy(): SecretBytes = fromByteArray(requireBytes())

    fun copyToByteArray(): ByteArray = requireBytes().copyOf()

    fun constantTimeEquals(other: SecretBytes): Boolean {
        val left = requireBytes()
        val right = other.requireBytes()
        if (left.size != right.size) return false
        var difference = 0
        for (index in left.indices) {
            difference = difference or (left[index].toInt() xor right[index].toInt())
        }
        return difference == 0
    }

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
