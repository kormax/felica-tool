package com.kormax.felicatool.felica

import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Interface representing a FeliCa target that can be communicated with. This abstraction allows for
 * different implementations (Android NFC, test mocks, etc.)
 */
interface FeliCaTarget {
    /** The card's IDM (8 bytes) - unique identifier */
    var idm: ByteArray

    /** The card's PMM (8 bytes) - Product Manufacturing Model */
    val pmm: Pmm

    /**
     * Raw transceive operation - sends raw bytes and receives raw bytes
     *
     * @param data The command data to send
     * @param timeout Optional timeout override. If null, uses default timeout
     * @return The response data received from the card
     */
    suspend fun transceive(data: ByteArray, timeout: Duration? = null): ByteArray

    /** Infers the appropriate timeout for a command based on PMM and command type */
    fun inferTimeout(command: FelicaCommand<*>): Duration {
        // Get command class from the command itself
        val commandClass = command.commandClass

        // Get number of units from the command itself
        val units = command.timeoutUnits

        // Return the calculated timeout with 50ms margin
        return pmm.totalTimeout(commandClass, units) + 50.toDuration(DurationUnit.MILLISECONDS)
    }

    /**
     * Typed transceive function - sends a command and receives a parsed response
     *
     * @param T The expected response type
     * @param command The FeliCa command to send
     * @param timeout Optional timeout override. If null, timeout is inferred from PMM and command
     *   type
     * @return The parsed response of type T
     */
    suspend fun <T : FelicaResponse> transceive(
        command: FelicaCommand<T>,
        timeout: Duration? = null,
    ): T {
        val commandBytes = command.toByteArray()
        val inferredTimeout = timeout ?: inferTimeout(command)
        val responseBytes = transceive(commandBytes, inferredTimeout)
        return command.responseFromByteArray(responseBytes)
    }
}
