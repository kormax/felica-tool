package com.kormax.felicatool.felica

/** Base interface for all FeliCa commands */
interface FelicaCommand<T : FelicaResponse> {
    /**
     * Returns the command class for timeout calculation. Default implementation returns OTHER for
     * unknown commands. Subclasses should override this to return the appropriate command class.
     */
    val commandClass: CommandClass
        get() = CommandClass.OTHER

    /**
     * Returns the number of timeout units for variable timeout commands. Default implementation
     * returns 0 units. Subclasses should override this to return the appropriate unit count.
     */
    val timeoutUnits: Int
        get() = 0

    /** Converts the command to a byte array for transmission */
    fun toByteArray(): ByteArray

    fun responseFromByteArray(data: ByteArray): T
}

/** Interface for command companions that provide command class information */
interface CommandCompanion {
    val COMMAND_CODE: Short
    val COMMAND_CLASS: CommandClass
}
