package com.kormax.felicatool.service

import android.util.Log

internal actual fun platformLogD(tag: String, message: String) {
    Log.d(tag, message)
}

internal actual fun platformLogI(tag: String, message: String) {
    Log.i(tag, message)
}

internal actual fun platformLogW(tag: String, message: String, throwable: Throwable?) {
    if (throwable == null) {
        Log.w(tag, message)
    } else {
        Log.w(tag, message, throwable)
    }
}

internal actual fun platformLogE(tag: String, message: String, throwable: Throwable?) {
    if (throwable == null) {
        Log.e(tag, message)
    } else {
        Log.e(tag, message, throwable)
    }
}
