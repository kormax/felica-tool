package com.kormax.felicatool.felica

/**
 * Base class for FeliCa messages that do not include an IDM These are typically broadcast commands
 * or system-level commands/responses
 */
abstract class FelicaMessageWithoutIdm : FelicaMessage()
