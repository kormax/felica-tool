package com.kormax.felicatool.felica

import kotlin.math.ceil

/**
 * Echo command - sends data to the card and expects it to be echoed back
 *
 * This command is used for testing communication with FeliCa cards. The card should respond with
 * the same data that was sent.
 */
class EchoCommand(
    /** Data to be echoed back by the card (up to 256 bytes) */
    val data: ByteArray
) : FelicaCommandWithoutIdm<EchoResponse>() {

    init {
        require(data.size <= 252) { "Data must be at most 252 bytes, got ${data.size}" }
    }

    override val commandClass: CommandClass = Companion.COMMAND_CLASS
    override val timeoutUnits: Int =
        ceil(data.size.toDouble() / 16.0).toInt() // Consider each 16 bytes (1 block) is 1 unit

    override fun responseFromByteArray(data: ByteArray) = EchoResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            COMMAND_CODE,
            capacity = FelicaCommandWithoutIdm.BASE_LENGTH + 1 + data.size,
        ) {
            addBytes(data)
        }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0xF000.toShort()
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val COMMAND_LENGTH: Int =
            FelicaCommandWithoutIdm.BASE_LENGTH +
                1 // +1 for second byte of command code, + data length (assuming 2 bytes default)

        /** Parse an Echo command from raw bytes */
        fun fromByteArray(data: ByteArray): EchoCommand =
            parseFelicaCommandWithoutIdm(data, COMMAND_CODE, minLength = COMMAND_LENGTH) {
                EchoCommand(bytes(remaining()))
            }
    }
}
