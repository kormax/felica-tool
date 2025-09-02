package com.kormax.felicatool.felica

/**
 * Communication Performance data class for interpreting polling response data when
 * RequestCode.COMMUNICATION_PERFORMANCE_REQUEST was used
 */
data class CommunicationPerformance(
    /** Whether 212 kbps communication is supported */
    val supports212kbps: Boolean,

    /** Whether 424 kbps communication is supported */
    val supports424kbps: Boolean,

    /** Whether 848 kbps communication is supported (reserved) */
    val supports848kbps: Boolean,

    /** Whether 1696 kbps communication is supported (reserved) */
    val supports1696kbps: Boolean,

    /** Whether communication rate automatic detection is compliant */
    val isAutomaticDetectionCompliant: Boolean,

    /** Reserved bits from D0 (bits 4-6, should be 000b) */
    val reservedBitsD0: Byte = 0,

    /** Reserved value from D1 (entire second byte) */
    val reservedBitsD1: Byte = 0,
) {
    /** Get the highest supported data rate */
    fun getHighestSupportedRate(): String {
        return when {
            supports1696kbps -> "1696 kbps (reserved)"
            supports848kbps -> "848 kbps (reserved)"
            supports424kbps -> "424 kbps"
            supports212kbps -> "212 kbps"
            else -> "No supported rates"
        }
    }

    /** Get all supported data rates as a list */
    fun getSupportedRates(): List<String> {
        val rates = mutableListOf<String>()
        if (supports212kbps) rates.add("212 kbps")
        if (supports424kbps) rates.add("424 kbps")
        if (supports848kbps) rates.add("848 kbps (reserved)")
        if (supports1696kbps) rates.add("1696 kbps (reserved)")
        return rates
    }

    /** Get the raw data as a byte array */
    fun toByteArray(): ByteArray {
        // Reconstruct D0 byte from individual bits
        var d0 = 0
        if (supports212kbps) d0 = d0 or 0x01
        if (supports424kbps) d0 = d0 or 0x02
        if (supports848kbps) d0 = d0 or 0x04
        if (supports1696kbps) d0 = d0 or 0x08
        if (isAutomaticDetectionCompliant) d0 = d0 or 0x80
        // Add reserved bits (bits 4-6)
        d0 = d0 or (reservedBitsD0.toInt() and 0x70)

        return byteArrayOf(reservedBitsD1, d0.toByte())
    }

    companion object {
        /**
         * Parse communication performance from 2-byte data Based on Table 4-8 in the FeliCa
         * specification
         */
        fun fromByteArray(data: ByteArray): CommunicationPerformance {
            require(data.size == 2) { "Communication performance data must be exactly 2 bytes" }

            val d0 = data[1]
            val d1 = data[0]

            // Parse D0 byte for communication performance bits
            // Bit positions according to specification:
            // b0: 212 kbps support
            // b1: 424 kbps support
            // b2: 848 kbps support (reserved)
            // b3: 1696 kbps support (reserved)
            // b4-b6: Fixed value (should be 000b) - these are reserved
            // b7: Communication rate automatic detection compliant

            val supports212kbps = (d0.toInt() and 0x01) != 0
            val supports424kbps = (d0.toInt() and 0x02) != 0
            val supports848kbps = (d0.toInt() and 0x04) != 0
            val supports1696kbps = (d0.toInt() and 0x08) != 0
            val isAutomaticDetectionCompliant = (d0.toInt() and 0x80) != 0

            // Extract only the reserved bits
            val reservedBitsD0 = (d0.toInt() and 0x70).toByte() // bits 4-6
            val reservedBitsD1 = d1 // entire second byte is reserved

            // Validate reserved bits are zero as expected
            if (reservedBitsD0 != 0.toByte()) {
                throw IllegalArgumentException(
                    "Reserved bits D0[4-6] should be 000b but got: 0x${"%02X".format(reservedBitsD0)}"
                )
            }
            if (reservedBitsD1 != 0.toByte()) {
                throw IllegalArgumentException(
                    "Reserved byte D1 should be 0x00 but got: 0x${"%02X".format(reservedBitsD1)}"
                )
            }

            return CommunicationPerformance(
                supports212kbps = supports212kbps,
                supports424kbps = supports424kbps,
                supports848kbps = supports848kbps,
                supports1696kbps = supports1696kbps,
                isAutomaticDetectionCompliant = isAutomaticDetectionCompliant,
                reservedBitsD0 = reservedBitsD0,
                reservedBitsD1 = reservedBitsD1,
            )
        }
    }
}
