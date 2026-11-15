package com.kormax.felicatool.util

internal fun maskIdm(bytes: ByteArray): String = maskIdm(bytes.toHexString().uppercase())

internal fun maskIdm(hex: String): String {
    if (hex.length < 6) return hex
    return hex.take(4) + "XX".repeat((hex.length - 6) / 2) + hex.takeLast(2)
}

internal fun maskIdi(hex: String): String {
    val pairs = hex.chunked(2)
    if (pairs.isEmpty() || pairs.all { it.equals(pairs[0], ignoreCase = true) }) return hex
    return pairs.take(2).joinToString("") + "XX".repeat((pairs.size - 2).coerceAtLeast(0))
}

internal fun maskPmi(hex: String): String {
    val pairs = hex.chunked(2)
    return pairs.indices.joinToString("") { index ->
        val inEqualRun =
            (index > 0 && pairs[index].equals(pairs[index - 1], ignoreCase = true)) ||
                (index < pairs.lastIndex &&
                    pairs[index].equals(pairs[index + 1], ignoreCase = true))
        if (inEqualRun) pairs[index] else "XX"
    }
}
