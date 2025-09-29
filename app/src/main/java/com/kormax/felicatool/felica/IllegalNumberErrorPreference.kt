package com.kormax.felicatool.felica

/**
 * Indicates which error code a card prefers when signalling ILLEGAL NUMBER conditions (0xA1 vs
 * 0xA2) during Read Without Encryption requests.
 */
enum class IllegalNumberErrorPreference {
    /** Card signals the limit using SERVICE error 0xA1. */
    SERVICE_ERROR,

    /** Card signals the limit using BLOCK error 0xA2. */
    BLOCK_ERROR,
}
