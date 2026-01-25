package com.kormax.felicatool.service

/**
 * Exception thrown when a command cannot be executed due to missing prerequisites, but this does
 * not mean the command is unsupported by the card.
 *
 * When this exception is caught, the command support status should remain UNKNOWN rather than being
 * marked as UNSUPPORTED.
 *
 * Examples of prerequisites:
 * - No suitable writable blocks available for write command testing
 * - Required services not discovered yet
 * - Required data not read yet
 */
class PrerequisiteException(message: String) : RuntimeException(message)
