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

    /** Non-standard bytes appended after the command's regular payload. */
    val trailingData: ByteArray
        get() = ByteArray(0)

    /** Converts the command to a byte array for transmission */
    fun toByteArray(): ByteArray

    fun responseFromByteArray(data: ByteArray): T
}

internal fun FelicaCommand<*>.requireFrameLength(canonicalLength: Int) {
    val frameLength = canonicalLength + trailingData.size
    require(frameLength <= FELICA_FRAME_MAX_LENGTH) {
        "Command frame length ($frameLength bytes) exceeds FeliCa frame length limit ($FELICA_FRAME_MAX_LENGTH bytes)"
    }
}

internal inline fun FelicaCommand<*>.buildFelicaCommandMessage(
    code: Short,
    idm: ByteArray? = null,
    capacity: Int,
    block: MutableList<Byte>.() -> Unit,
): ByteArray {
    val trailingData = trailingData
    return buildFelicaMessage(code, idm, capacity = capacity + trailingData.size) {
        block()
        addBytes(trailingData)
    }
}

/** Interface for command companions that provide command class information */
interface CommandCompanion {
    val COMMAND_CODE: Short
    val COMMAND_CLASS: CommandClass
}
