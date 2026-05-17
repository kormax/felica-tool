package com.kormax.felicatool.service

internal object ScanLog {
    fun d(tag: String, message: String) {
        platformLogD(tag, message)
    }

    fun i(tag: String, message: String) {
        platformLogI(tag, message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        platformLogW(tag, message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        platformLogE(tag, message, throwable)
    }
}

internal expect fun platformLogD(tag: String, message: String)

internal expect fun platformLogI(tag: String, message: String)

internal expect fun platformLogW(tag: String, message: String, throwable: Throwable?)

internal expect fun platformLogE(tag: String, message: String, throwable: Throwable?)
