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

    override fun toByteArray(): ByteArray {
        val totalLength = COMMAND_LENGTH + data.size // +1 for second byte of command code
        val dataArray = ByteArray(totalLength)
        var offset = 0

        // Length (1 byte)
        dataArray[offset++] = totalLength.toByte()

        // Command code (2 bytes)
        dataArray[offset++] = (COMMAND_CODE.toInt() shr 8).toByte() // High byte
        dataArray[offset++] = COMMAND_CODE.toByte() // Low byte

        // Data
        data.copyInto(dataArray, offset)

        return dataArray
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0xF000.toShort()
        override val COMMAND_CLASS: CommandClass = CommandClass.VARIABLE_RESPONSE_TIME

        const val COMMAND_LENGTH: Int =
            FelicaCommandWithoutIdm.BASE_LENGTH +
                1 // +1 for second byte of command code, + data length (assuming 2 bytes default)

        /** Parse an Echo command from raw bytes */
        fun fromByteArray(data: ByteArray): EchoCommand {
            require(data.size >= COMMAND_LENGTH) { "Data must be at least $COMMAND_LENGTH bytes" }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Command code (2 bytes for Echo)
            val commandCode =
                ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            require(commandCode == COMMAND_CODE.toInt()) {
                "Invalid command code: expected $COMMAND_CODE, got ${commandCode.toShort()}"
            }
            offset += 2

            // Data (remaining bytes)
            val echoData = data.sliceArray(offset until data.size)

            return EchoCommand(echoData)
        }
    }
}
