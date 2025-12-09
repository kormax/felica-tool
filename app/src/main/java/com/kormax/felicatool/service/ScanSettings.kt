package com.kormax.felicatool.service

/**
 * Settings to control card scanning behavior. These settings can be toggled by the user before a
 * scan starts.
 */
data class ScanSettings(
    /**
     * When enabled, performs an exhaustive search for hidden nodes using
     * RequestService/RequestServiceV2. Iterates through all possible node codes (0-1023) with all
     * known service attributes to discover nodes that may not appear in SearchServiceCode or
     * RequestCodeList responses.
     */
    val forceDiscoverAllNodes: Boolean = false
)
