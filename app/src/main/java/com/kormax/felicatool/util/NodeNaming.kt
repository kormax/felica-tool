package com.kormax.felicatool.util

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.System
import com.kormax.felicatool.service.SystemScanContext

/** Provides naming for known FeliCa systems and areas. */
object NodeNaming {

    /** Map of system codes to their known names. */
    private val systemNames =
        mapOf(
            "88B4" to "FeliCa Lite",
            "12FC" to "NDEF Type 3 Tag",
            "0003" to "CJRC Standard",
            "8008" to "Octopus",
            "FE00" to "FeliCa Networks Common Area",
            "FE0F" to "Osaifu Keitai Container",
        )

    /** Map of system code -> area code -> area name. */
    private val areaNames =
        mapOf(
            "FE00" to mapOf(
                "00103F17" to "Edy Root",
                "01103F17" to "Edy",
                "8055BF56" to "Nanaco Root",
                "8155BF56" to "Nanaco",
                "C067FF68" to "WAON Root",
                "C167FF68" to "WAON",
        )
    )

    /**
     * Get the display name for a system code, if known.
     *
     * @param systemCode The system code as hex string (e.g., "88B4")
     * @return The known name, or null if not recognized
     */
    fun getSystemName(systemCode: String): String? {
        return systemNames[systemCode.uppercase()]
    }

    /**
     * Get the display name for an area within a system, if known.
     *
     * @param systemCode The system code as hex string (e.g., "FE00")
     * @param areaCode The area code as hex string (e.g., "8055BF56")
     * @return The known name, or null if not recognized
     */
    fun getAreaName(systemCode: String, areaCode: String): String? {
        return areaNames[systemCode.uppercase()]?.get(areaCode.uppercase())
    }

    /**
     * Get the display name for a system node based on the context.
     *
     * @param node The system node
     * @param context The system scan context
     * @return The known name, or null if not recognized
     */
    fun getSystemName(node: System, context: SystemScanContext): String? {
        val systemCode = context.systemCode?.let { it.toHexString().uppercase() } ?: return null
        return getSystemName(systemCode)
    }

    /**
     * Get the display name for an area node based on the context.
     *
     * @param node The area node
     * @param context The system scan context
     * @return The known name, or null if not recognized
     */
    fun getAreaName(node: Area, context: SystemScanContext): String? {
        val systemCode = context.systemCode?.let { it.toHexString().uppercase() } ?: return null
        val areaCode = node.fullCode.toHexString().uppercase()
        return getAreaName(systemCode, areaCode)
    }
}

/** Extension function to convert ByteArray to hex string. */
private fun ByteArray.toHexString(): String {
    return this.joinToString("") { "%02X".format(it) }
}
