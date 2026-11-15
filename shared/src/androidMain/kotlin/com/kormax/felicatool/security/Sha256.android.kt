package com.kormax.felicatool.security

import java.security.MessageDigest

internal actual fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)
