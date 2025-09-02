package com.kormax.felicatool.felica

/** Base class for FeliCa responses that include an IDM (card identifier) */
abstract class FelicaResponseWithIdm(idm: ByteArray) : FelicaMessageWithIdm(idm), FelicaResponse {
    companion object {
        /** Base length for responses with IDM: length(1) + response_code(1) + idm(8) */
        const val BASE_LENGTH: Int = 10
    }
}
