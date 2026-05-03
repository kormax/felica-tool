package com.kormax.felicatool.felica

/**
 * Base class for FeliCa messages that include an IDM (card identifier) This class contains the IDM
 * as a real field to avoid re-declaration in subclasses
 */
abstract class FelicaMessageWithIdm(
    /** The 8-byte IDM (card identifier) */
    val idm: ByteArray
) : FelicaMessage() {

    init {
        require(idm.size == 8) { "IDM must be exactly 8 bytes, got ${idm.size}" }
    }
}
