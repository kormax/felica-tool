package com.kormax.felicatool.util

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.felica.System
import com.kormax.felicatool.service.SystemScanContext

/** Provides naming for known FeliCa systems, areas, and services. */
object NodeNaming {

    /** Map of system codes to their known names. */
    private val systemNames =
        mapOf(
            "88B4" to "FeliCa Lite",
            "12FC" to "NDEF Type 3 Tag",
            "0003" to "CJRC Standard",
            "86A7" to "Suica",
            "8008" to "Octopus",
            "FE00" to "FeliCa Networks Common Area",
            "FE0F" to "Osaifu Keitai Container",
        )

    /** Map of system code -> area code -> area name. */
    private val areaNames =
        mapOf(
            "FE00" to
                mapOf(
                    "00103F17" to "Edy root",
                    "01103F17" to "Edy",
                    "4039FF39" to "FeliCa Pocket root",
                    "4139FF39" to "FeliCa Pocket",
                    "8055BF56" to "Nanaco root",
                    "8155BF56" to "Nanaco",
                    "C067FF68" to "WAON root",
                    "C167FF68" to "WAON",
                )
        )

    /** Map of system code -> area code -> service code -> service name. */
    private val serviceNames =
        mapOf(
            "FE00" to
                mapOf(
                    // Edy area services
                    "01103F17" to
                        mapOf("0B11" to "Edy ID", "1713" to "Edy balance", "0F17" to "Edy log"),
                    "4139FF39" to
                        mapOf(
                            "4B39" to "Pocket Capabilities",
                            "8B39" to "Pocket Directory",
                            "C939" to "Pocket Data",
                        ),

                    // nanaco area services
                    "8155BF56" to
                        mapOf(
                            "8B55" to "Nanaco ID",
                            "9755" to "Nanaco balance",
                            "4F56" to "Nanaco log",
                        ),

                    // WAON area services
                    "C167FF68" to
                        mapOf(
                            "CF67" to "WAON ID",
                            "1768" to "WAON balance",
                            "0B68" to "WAON log",
                            "4B68" to "WAON points",
                        ),
                ),
            "0003" to
                mapOf(
                    // Suica transaction services (area not specified in scan, using system-level)
                    "" to mapOf("0F09" to "CJRC log")
                ),
            "88B4" to
                mapOf(
                    // FeliCa Lite services (root area)
                    "" to mapOf("0900" to "FeliCa Lite", "0B00" to "FeliCa Lite")
                ),
            "12FC" to
                mapOf(
                    // NDEF services (root area)
                    "" to mapOf("0900" to "NDEF", "0B00" to "NDEF")
                ),
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
     * Get the display name for a service within a system and area, if known.
     *
     * @param systemCode The system code as hex string (e.g., "FE00")
     * @param areaCode The area code as hex string (e.g., "01103F17"), or empty string for
     *   system-level services
     * @param serviceCode The service code as hex string (e.g., "110B")
     * @return The known name, or null if not recognized
     */
    fun getServiceName(systemCode: String, areaCode: String, serviceCode: String): String? {
        return serviceNames[systemCode.uppercase()]
            ?.get(areaCode.uppercase())
            ?.get(serviceCode.uppercase())
            ?: if (areaCode.isNotEmpty())
                serviceNames[systemCode.uppercase()]?.get("")?.get(serviceCode.uppercase())
            else null
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

    /**
     * Get the display name for a service node based on the context.
     *
     * @param node The service node
     * @param context The system scan context
     * @return The known name, or null if not recognized
     */
    fun getServiceName(node: Service, context: SystemScanContext): String? {
        val systemCode = context.systemCode?.let { it.toHexString().uppercase() } ?: return null
        val serviceCode = node.code.toHexString().uppercase()

        // Find the most specific area that contains this service
        // Sort by area number and reverse to get most specific (highest numbered) areas first
        val containingArea =
            context.nodes
                .filterIsInstance<Area>()
                .sortedBy { area -> area.number }
                .reversed()
                .filter { area -> node.belongsTo(area) }
                .firstOrNull()

        val areaCode = containingArea?.fullCode?.toHexString()?.uppercase() ?: ""
        return getServiceName(systemCode, areaCode, serviceCode)
    }
}

/** Extension function to convert ByteArray to hex string. */
private fun ByteArray.toHexString(): String {
    return this.joinToString("") { "%02X".format(it) }
}
