package com.kormax.felicatool.nfc

class TagLostException(cause: Throwable? = null) :
    IllegalStateException("Active NFC tag was lost", cause)

class TagUnavailableException(
    message: String = "NFC tag is no longer available",
    cause: Throwable? = null,
) : IllegalStateException(message, cause)
