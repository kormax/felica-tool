package com.kormax.felicatool.nfc

class TagRediscoveredException :
    IllegalStateException("Active NFC tag was rediscovered by the reader session")

class AnotherTagDiscoveredException :
    IllegalStateException("Another NFC tag was discovered by the reader session")

class TagLostException(cause: Throwable? = null) :
    IllegalStateException("Active NFC tag was lost", cause)
