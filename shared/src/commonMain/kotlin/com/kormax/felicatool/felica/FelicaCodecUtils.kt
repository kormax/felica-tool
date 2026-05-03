package com.kormax.felicatool.felica

import kotlin.math.max

internal class ByteCursor(private val data: ByteArray) {
    var position: Int = 0
        private set

    fun byte(): Byte {
        ensureAvailable(1)
        return data[position++]
    }

    fun uByte(): Int = byte().toInt() and 0xFF

    fun bytes(count: Int): ByteArray {
        ensureAvailable(count)
        return data.copyOfRange(position, position + count).also { position += count }
    }

    fun uShort(): Int {
        val high = uByte()
        val low = uByte()
        return (high shl 8) or low
    }

    fun remaining(): Int = data.size - position

    private fun ensureAvailable(count: Int) {
        require(remaining() >= count) {
            "Insufficient data: need $count bytes at offset $position, have ${remaining()}"
        }
    }
}

internal fun MutableList<Byte>.addByte(value: Int) = add(value.toByte())

internal fun MutableList<Byte>.addByte(value: Byte) = add(value)

internal fun MutableList<Byte>.addBytes(value: ByteArray) = addAll(value.asList())

private fun MutableList<Byte>.addCode(code: Short) {
    if (code.requiresTwoBytes()) {
        addByte(code.toInt() shr 8)
    }
    addByte(code.toInt())
}

internal inline fun buildFelicaMessage(
    code: Short,
    idm: ByteArray? = null,
    capacity: Int = 0,
    block: MutableList<Byte>.() -> Unit,
): ByteArray {
    val buffer = ArrayList<Byte>(capacity)
    buffer.addByte(0) // Length placeholder
    buffer.addCode(code)
    idm?.let { buffer.addBytes(it) }
    buffer.block()
    return buffer.toByteArray().also { it[0] = it.size.toByte() }
}

internal inline fun <T> parseFelicaCommand(
    data: ByteArray,
    expectedCode: Short,
    hasIdm: Boolean = true,
    minLength: Int? = null,
    maxLength: Int? = null,
    block: ByteCursor.(idm: ByteArray?) -> T,
): T = parseFelicaMessage(data, expectedCode, hasIdm, minLength, maxLength, "command", block)

internal inline fun <T> parseFelicaCommandWithIdm(
    data: ByteArray,
    expectedCode: Short,
    minLength: Int? = null,
    block: ByteCursor.(idm: ByteArray) -> T,
): T =
    parseFelicaCommand(data, expectedCode, hasIdm = true, minLength) { idm ->
        block(requireNotNull(idm) { "IDM missing from command payload" })
    }

internal inline fun <T> parseFelicaCommandWithoutIdm(
    data: ByteArray,
    expectedCode: Short,
    minLength: Int? = null,
    maxLength: Int? = null,
    block: ByteCursor.() -> T,
): T =
    parseFelicaMessage(data, expectedCode, hasIdm = false, minLength, maxLength, "command") {
        block()
    }

internal inline fun <T> parseFelicaResponse(
    data: ByteArray,
    expectedCode: Short,
    hasIdm: Boolean = true,
    minLength: Int? = null,
    maxLength: Int? = null,
    block: ByteCursor.(idm: ByteArray?) -> T,
): T = parseFelicaMessage(data, expectedCode, hasIdm, minLength, maxLength, "response", block)

internal inline fun <T> parseFelicaResponseWithIdm(
    data: ByteArray,
    expectedCode: Short,
    minLength: Int? = null,
    maxLength: Int? = null,
    block: ByteCursor.(idm: ByteArray) -> T,
): T =
    parseFelicaResponse(data, expectedCode, hasIdm = true, minLength, maxLength) { idm ->
        block(requireNotNull(idm) { "IDM missing from response payload" })
    }

internal inline fun <T> parseFelicaResponseWithoutIdm(
    data: ByteArray,
    expectedCode: Short,
    minLength: Int? = null,
    maxLength: Int? = null,
    block: ByteCursor.() -> T,
): T =
    parseFelicaMessage(data, expectedCode, hasIdm = false, minLength, maxLength, "response") {
        block()
    }

private inline fun <T> parseFelicaMessage(
    data: ByteArray,
    expectedCode: Short,
    hasIdm: Boolean,
    minLength: Int?,
    maxLength: Int?,
    label: String,
    block: ByteCursor.(idm: ByteArray?) -> T,
): T {
    minLength?.let { min ->
        require(data.size >= min) {
            "${label.replaceFirstChar { if (it.isLowerCase()) it.titlecaseChar() else it }} data too short: ${data.size} bytes, minimum $min required"
        }
    }

    maxLength?.let { max ->
        require(data.size <= max) {
            "${label.replaceFirstChar { if (it.isLowerCase()) it.titlecaseChar() else it }} data too long: ${data.size} bytes, maximum $max allowed"
        }
    }

    val cursor = ByteCursor(data)
    val length = cursor.uByte()
    require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }

    val actualCode =
        if (expectedCode.requiresTwoBytes()) cursor.uShort().toShort() else cursor.byte().toShort()
    require(actualCode == expectedCode) {
        "Invalid $label code: expected $expectedCode, got $actualCode"
    }

    val idm = if (hasIdm) cursor.bytes(8) else null
    return cursor.block(idm)
}

private fun Short.requiresTwoBytes(): Boolean = (toInt() and 0xFF00) != 0
