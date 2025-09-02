package com.kormax.felicatool.felica

/**
 * Indicates how error location information is provided in FeliCa responses. Used to determine how
 * to interpret status flags when errors occur.
 */
enum class ErrorLocationIndication {
    /** No error location information is provided */
    FLAG,
    /** Error location is indicated by an index */
    INDEX,
    /** Error location is indicated by a bitmask */
    BITMASK,
}
