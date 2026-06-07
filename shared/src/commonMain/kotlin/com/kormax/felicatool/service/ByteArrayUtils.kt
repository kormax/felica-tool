package com.kormax.felicatool.service

internal fun ByteArray?.sameBytes(other: ByteArray?): Boolean =
    when {
        this == null && other == null -> true
        this == null || other == null -> false
        else -> this.contentEquals(other)
    }

internal fun Iterable<ByteArray>.containsBytes(bytes: ByteArray): Boolean = any {
    it.contentEquals(bytes)
}

internal fun Iterable<ByteArray>.toUniqueByteArrays(): List<ByteArray> {
    val unique = mutableListOf<ByteArray>()
    forEach { bytes ->
        if (!unique.containsBytes(bytes)) {
            unique += bytes
        }
    }
    return unique
}
