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
    val forceDiscoverAllNodes: Boolean = false,
    /**
     * When enabled, performs an exhaustive search for blocks in readable services. Iterates through
     * block numbers 0x0000 to 0xFFFF for each readable service to discover blocks that may exist
     * beyond the normally discovered range.
     */
    val forceDiscoverAllBlocks: Boolean = false,
    /**
     * When enabled, tests write commands during scanning. This is OFF by default to prevent
     * accidental modifications to card data.
     */
    val testWriteCommands: Boolean = false,
)
