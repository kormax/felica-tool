package com.kormax.felicatool.service

internal object ScanLog {
    fun d(tag: String, message: String) = Unit

    fun i(tag: String, message: String) = Unit

    fun w(tag: String, message: String, throwable: Throwable? = null) = Unit

    fun e(tag: String, message: String, throwable: Throwable? = null) = Unit
}
