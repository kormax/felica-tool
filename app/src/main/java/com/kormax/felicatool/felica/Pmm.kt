package com.kormax.felicatool.felica

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** PMM (Product Manufacturing Model) value for FeliCa cards Stores an 8-byte PMM value */
data class Pmm(val value: ByteArray) {

    init {
        require(value.size == 8) { "PMM must be exactly 8 bytes, got ${value.size}" }
    }

    /** Returns a copy of the PMM value as a byte array */
    fun toByteArray(): ByteArray = value.copyOf()

    /** IC Code (2 bytes: ROM Type and IC Type) */
    val icCode: ByteArray
        get() = value.sliceArray(0..1)

    /** ROM Type (first byte of IC Code) */
    val romType: Byte
        get() = value[0]

    /** IC Type (second byte of IC Code) */
    val icType: Byte
        get() = value[1]

    // Timeout parameters for different command classes
    // Each consists of constant timeout + timeout per unit (node/block/etc.)

    /**
     * Timeout for commands where response time varies depending on packet elements (e.g., Request
     * Service, Request Service v2, Get Node Property)
     */
    val variableResponseTimeConstant: Duration
        get() = calculateConstantTimeout(value[2])

    val variableResponseTimePerUnit: Duration
        get() = calculateTimeoutPerUnit(value[2])

    /**
     * Timeout for commands with fixed response time (e.g., Request Response, Search Service Code,
     * Request System Code, etc.)
     */
    val fixedResponseTimeConstant: Duration
        get() = calculateConstantTimeout(value[3])

    val fixedResponseTimePerUnit: Duration
        get() = calculateTimeoutPerUnit(value[3])

    /**
     * Timeout for mutual authentication commands (e.g., Authentication1, Authentication2,
     * Authentication1 v2, Authentication2 v2)
     */
    val mutualAuthConstant: Duration
        get() = calculateConstantTimeout(value[4])

    val mutualAuthPerUnit: Duration
        get() = calculateTimeoutPerUnit(value[4])

    /**
     * Timeout for data read commands (e.g., Read Without Encryption, Read, Internal Authenticate
     * and Read, Read v2)
     */
    val dataReadConstant: Duration
        get() = calculateConstantTimeout(value[5])

    val dataReadPerUnit: Duration
        get() = calculateTimeoutPerUnit(value[5])

    /**
     * Timeout for data write commands (e.g., Write Without Encryption, Write, External Authenticate
     * and Write, Write v2, Set Node Property)
     */
    val dataWriteConstant: Duration
        get() = calculateConstantTimeout(value[6])

    val dataWritePerUnit: Duration
        get() = calculateTimeoutPerUnit(value[6])

    /** Timeout for other commands (e.g., Issuance commands) */
    val otherConstant: Duration
        get() = calculateConstantTimeout(value[7])

    val otherPerUnit: Duration
        get() = calculateTimeoutPerUnit(value[7])

    val maxConstant: Duration =
        maxOf(
            variableResponseTimeConstant,
            fixedResponseTimeConstant,
            mutualAuthConstant,
            dataReadConstant,
            dataWriteConstant,
            otherConstant,
        )

    val maxPerUnit: Duration =
        maxOf(
            variableResponseTimePerUnit,
            fixedResponseTimePerUnit,
            mutualAuthPerUnit,
            dataReadPerUnit,
            dataWritePerUnit,
            otherPerUnit,
        )

    /**
     * Command support properties If the corresponding PMM byte is 0x00, the command type is not
     * supported
     */
    val variableResponseTimeCommandSupported: Boolean
        get() = value[2] != 0x00.toByte()

    val fixedResponseTimeCommandSupported: Boolean
        get() = value[3] != 0x00.toByte()

    val mutualAuthCommandSupported: Boolean
        get() = value[4] != 0x00.toByte()

    val dataReadCommandSupported: Boolean
        get() = value[5] != 0x00.toByte()

    val dataWriteCommandSupported: Boolean
        get() = value[6] != 0x00.toByte()

    val otherCommandSupported: Boolean
        get() = value[7] != 0x00.toByte()

    /** Calculates the total timeout for a given command class and number of units */
    fun totalTimeout(commandClass: CommandClass, units: Int = 0x00): Duration {
        return when (commandClass) {
            CommandClass.VARIABLE_RESPONSE_TIME ->
                variableResponseTimeConstant + variableResponseTimePerUnit * units
            CommandClass.FIXED_RESPONSE_TIME ->
                fixedResponseTimeConstant + fixedResponseTimePerUnit * units
            CommandClass.MUTUAL_AUTH -> mutualAuthConstant + mutualAuthPerUnit * units
            CommandClass.DATA_READ -> dataReadConstant + dataReadPerUnit * units
            CommandClass.DATA_WRITE -> dataWriteConstant + dataWritePerUnit * units
            CommandClass.OTHER -> otherConstant + otherPerUnit * units
            CommandClass.UNKNOWN -> maxConstant + maxPerUnit * units
            else -> maxConstant + maxPerUnit * units
        }
    }

    override fun toString(): String = value.toHexString()

    fun toHexString(): String = value.toHexString().uppercase()

    companion object {
        // T = 256 × 16 / fc (fc = 13.56 MHz)
        private val T: Duration =
            ((256.0 * 16.0) / (13.56e6) * 1000.0).toDuration(DurationUnit.MILLISECONDS)

        /**
         * Parses A, B, E values from a PMM byte Byte format: b7 b6 b5 b4 b3 b2 b1 b0 A: b7 b6 b5,
         * B: b4 b3 b2, E: b1 b0
         */
        private fun parseTimeoutByte(byte: Byte): Triple<Int, Int, Int> {
            val intValue = byte.toInt() and 0xFF
            val a = intValue and 0x07
            val b = (intValue shr 3) and 0x07
            val e = (intValue shr 6) and 0x03
            return Triple(a, b, e)
        }

        /** Calculates the constant timeout part: T × (A+1) × 4^E */
        private fun calculateConstantTimeout(byte: Byte): Duration {
            val (a, _, e) = parseTimeoutByte(byte)
            val multiplier = (a + 1L) * (1L shl (2 * e))
            return T * multiplier.toInt()
        }

        /** Calculates the timeout per unit: T × (B+1) × 4^E */
        private fun calculateTimeoutPerUnit(byte: Byte): Duration {
            val (_, b, e) = parseTimeoutByte(byte)
            val multiplier = (b + 1L) * (1L shl (2 * e))
            return T * multiplier.toInt()
        }
    }
}
