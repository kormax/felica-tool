package com.kormax.felicatool.util

/**
 * Utility object for mapping IC Type codes to their corresponding IC names. Source:
 * https://nullablevoidptr.github.io/nfcf-info/IC-chips/card/
 */
object IcTypeMapping {

    /**
     * Maps IC Type byte values to their corresponding IC names. Based on the FeliCa IC
     * specifications.
     */
    private val icTypeMap =
        mapOf(
            0x00.toByte() to "RC-S830",
            0x01.toByte() to "RC-S915",
            0x02.toByte() to "RC-S919",
            0x06.toByte() to "Mobile FeliCa 1.0",
            0x07.toByte() to "Mobile FeliCa 1.1",
            0x08.toByte() to "RC-S952",
            0x09.toByte() to "RC-S953",
            0x0C.toByte() to "RC-S954",
            0x0D.toByte() to "RC-S960",
            0x10.toByte() to "Mobile FeliCa 2.0",
            0x11.toByte() to "Mobile FeliCa 2.0",
            0x12.toByte() to "Mobile FeliCa 2.0",
            0x13.toByte() to "Mobile FeliCa 2.0",
            0x14.toByte() to "Mobile FeliCa 3.0",
            0x15.toByte() to "Mobile FeliCa 3.0",
            0x16.toByte() to "Mobile FeliCa 4.0 for Apple Wallet",
            0x17.toByte() to "Mobile FeliCa 4.0",
            0x18.toByte() to "Mobile FeliCa 4.1",
            0x19.toByte() to "Mobile FeliCa 4.1",
            0x1A.toByte() to "Mobile FeliCa 4.1",
            0x1B.toByte() to "Mobile FeliCa 4.1",
            0x1C.toByte() to "Mobile FeliCa 4.1",
            0x1D.toByte() to "Mobile FeliCa 4.1",
            0x1E.toByte() to "Mobile FeliCa 4.1",
            0x1F.toByte() to "Mobile FeliCa 4.1",
            0x20.toByte() to "RC-S962",
            0x32.toByte() to "RC-SA00/1",
            0x33.toByte() to "RC-SA00/2",
            0x34.toByte() to "RC-SA01/1",
            0x35.toByte() to "RC-SA01/2",
            0x36.toByte() to "RC-SA04/1",
            0x3E.toByte() to "RC-SA08/1",
            0x43.toByte() to "RC-SA24/1",
            0x44.toByte() to "RC-SA20/1",
            0x45.toByte() to "RC-SA20/2",
            0x46.toByte() to "RC-SA21/2",
            0x47.toByte() to "RC-SA24/1x1",
            0x48.toByte() to "RC-SA21/2x1",
            0xE0.toByte() to "RC-S926 FeliCa Plug",
            0xF0.toByte() to "RC-S965 FeliCa Lite",
            0xF1.toByte() to "RC-S966 FeliCa Lite-S",
        )

    /**
     * Returns the IC name for the given IC type byte.
     *
     * @param icType The IC type byte value
     * @return The corresponding IC name, or null if the IC type is unknown
     */
    fun getIcName(icType: Byte): String? {
        return icTypeMap[icType]
    }

    /**
     * Returns a formatted string with both the hex value and the IC name.
     *
     * @param icType The IC type byte value
     * @return Formatted string like "0x16 (Mobile FeliCa 4.0 (Apple Wallet))" or just "0x99" if
     *   unknown
     */
    fun getFormattedIcType(icType: Byte): String {
        val hexValue = "0x${icType.toUByte().toString(16).uppercase().padStart(2, '0')}"
        val icName = getIcName(icType)
        return if (icName != null) {
            "$hexValue ($icName)"
        } else {
            hexValue
        }
    }
}
