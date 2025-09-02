package com.kormax.felicatool.felica

/** Enumeration of command classes for timeout calculation */
enum class CommandClass {
    // Polling has custom timeout
    POLLING,
    //
    VARIABLE_RESPONSE_TIME,
    FIXED_RESPONSE_TIME,
    MUTUAL_AUTH,
    DATA_READ,
    DATA_WRITE,
    OTHER,
    // For commands with unknown timeout relationship (will use max timeout)
    UNKNOWN,
}
