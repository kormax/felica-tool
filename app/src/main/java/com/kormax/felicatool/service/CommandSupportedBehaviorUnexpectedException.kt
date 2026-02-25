package com.kormax.felicatool.service

/**
 * Exception thrown when a command clearly responds (therefore is supported), but the behavior does
 * not match the expected probe pattern and a fallback value is applied.
 *
 * This should surface as a step error (red card) while preserving command support.
 */
class CommandSupportedBehaviorUnexpectedException(message: String) : RuntimeException(message)
