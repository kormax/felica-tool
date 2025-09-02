package com.kormax.felicatool.felica

/** Enum representing DES key types in the FeliCa system. */
enum class DesKeyType(val value: Int) {
    NONE(0),
    DES56(56),
    DES112(112),
}
