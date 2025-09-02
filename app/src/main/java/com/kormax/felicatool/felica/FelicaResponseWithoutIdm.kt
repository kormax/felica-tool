package com.kormax.felicatool.felica

/**
 * Base class for FeliCa responses that do not include an IDM These are typically responses to
 * broadcast commands or system-level commands
 */
abstract class FelicaResponseWithoutIdm : FelicaMessageWithoutIdm(), FelicaResponse {
    companion object {
        /** Base length for responses without IDM: length(1) + response_code(1) */
        const val BASE_LENGTH: Int = 2
    }
}
