package com.kormax.felicatool.felica

/**
 * Base class for FeliCa commands that do not require an IDM These are typically broadcast commands
 * or system-level commands
 */
abstract class FelicaCommandWithoutIdm<T : FelicaResponse> : FelicaCommand<T> {
    companion object {
        /** Base length for commands without IDM: length(1) + command_code(1) */
        const val BASE_LENGTH: Int = 2
    }
}
