package com.kormax.felicatool.felica

/**
 * Base class for FeliCa commands that do not require an IDM These are typically broadcast commands
 * or system-level commands
 */
abstract class FelicaCommandWithoutIdm<T : FelicaResponse>(trailingData: ByteArray = ByteArray(0)) :
    FelicaCommand<T> {
    private val trailingDataBytes = trailingData.copyOf()

    final override val trailingData: ByteArray
        get() = trailingDataBytes.copyOf()

    companion object {
        /** Base length for commands without IDM: length(1) + command_code(1) */
        const val BASE_LENGTH: Int = 2
    }
}
