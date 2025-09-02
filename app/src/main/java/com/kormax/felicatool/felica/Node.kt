package com.kormax.felicatool.felica

/**
 * Base interface representing a Node in the FeliCa file system. Node is a collective term for
 * System, Area, and Service.
 */
interface Node {

    /**
     * Returns the code bytes for this node.
     * - For Area: returns area code (2 bytes)
     * - For System: returns 0xFFFF (2 bytes)
     * - For Service: returns service code (2 bytes)
     */
    abstract val code: ByteArray
    abstract val number: Int

    /**
     * Returns the full code bytes for this node.
     * - For Area: returns area code + end area code (4 bytes)
     * - For System: returns 0xFFFF (2 bytes)
     * - For Service: returns service code (2 bytes)
     */
    open val fullCode: ByteArray
        get() = code

    /**
     * Checks if this node belongs to the other node.
     * - Area belongs to other area if its number range is equal or within the other's range
     * - Service belongs to area if its service number is within the area's range
     * - Area cannot belong to service
     *
     * @param other The other node to check against
     * @return true if this node belongs to the other node
     */
    abstract fun belongsTo(other: Node): Boolean

    /** Converts the node to a byte array */
    fun toByteArray(): ByteArray = fullCode
}
