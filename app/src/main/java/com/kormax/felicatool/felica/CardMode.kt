package com.kormax.felicatool.felica

/** Enum representing the Card Mode in the FeliCa system. */
enum class CardMode(val value: Int) {
    INITIAL(0),
    AUTHENTICATION_PENDING(1),
    AUTHENTICATED(2),
    ISSUANCE(3),
}
