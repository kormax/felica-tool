package com.kormax.felicatool.nfc

open class NfcReaderException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

class NfcTargetUnavailableException(
    message: String = "NFC target is no longer available",
    cause: Throwable? = null,
) : NfcReaderException(message, cause)

class TransceiveTimeoutException(
    message: String = "NFC transceive timed out",
    cause: Throwable? = null,
) : NfcReaderException(message, cause)

class TransceiveErrorException(
    message: String = "NFC transceive failed",
    cause: Throwable? = null,
) : NfcReaderException(message, cause)

typealias TagUnavailableException = NfcTargetUnavailableException
