package com.kormax.felicatool.felica

/**
 * System class representing the System node in the FeliCa file system. The System node is the root
 * node and is represented by 0xFFFF.
 */
object System : Node {

    /** Returns the system code as a byte array (2 bytes: 0xFF 0xFF). */
    override val code: ByteArray
        get() = byteArrayOf(0xFF.toByte(), 0xFF.toByte())

    override val number: Int = 0xFFFF

    override fun belongsTo(other: Node): Boolean {
        // System belongs to itself (root node)
        return other is System
    }
}
