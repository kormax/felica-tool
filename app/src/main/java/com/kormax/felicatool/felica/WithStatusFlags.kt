package com.kormax.felicatool.felica

/**
 * Interface for FeliCa responses that include status flags
 *
 * Status flags are part of many FeliCa response messages and indicate whether the operation was
 * successful or encountered an error.
 */
interface WithStatusFlags {
    /** Status Flag1 - indicates success (0x00) or error location */
    val statusFlag1: Byte

    /** Status Flag2 - indicates detailed error information */
    val statusFlag2: Byte

    /** Check if the response indicates success (statusFlag1 == 0x00) */
    val isStatusSuccessful: Boolean
        get() = statusFlag1 == 0x00.toByte()
}
