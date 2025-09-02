package com.kormax.felicatool.felica

/** Base class for FeliCa commands that include an IDM (card identifier) */
abstract class FelicaCommandWithIdm<T : FelicaResponse>(idm: ByteArray) :
    FelicaMessageWithIdm(idm), FelicaCommand<T> {
    companion object {
        /** Base length for commands with IDM: length(1) + command_code(1) + idm(8) */
        const val BASE_LENGTH: Int = 10
    }
}
