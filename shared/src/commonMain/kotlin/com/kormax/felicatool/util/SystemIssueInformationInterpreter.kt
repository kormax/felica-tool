package com.kormax.felicatool.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class SystemIssueInterpretation(
    val issuanceDate: LocalDate?,
    val serialNumber: String?,
)

object SystemIssueInformationInterpreter {
    private val FELICA_EPOCH = LocalDate(2000, 1, 1)

    fun interpret(
        systemCode: String,
        idi: ByteArray?,
        pmi: ByteArray?,
        manufacturingDate: LocalDate?,
        currentDate: LocalDate,
    ): SystemIssueInterpretation =
        SystemIssueInterpretation(
            issuanceDate = decodeIssuanceDate(systemCode, idi, manufacturingDate, currentDate),
            serialNumber = decodeSerialNumber(systemCode, idi, pmi),
        )

    fun decodeIssuanceDate(
        systemCode: String,
        idi: ByteArray?,
        manufacturingDate: LocalDate?,
        currentDate: LocalDate,
    ): LocalDate? {
        if (idi == null || idi.size < IDI_SIZE) return null

        return IssuerRegistry.definitionsFor(systemCode, idi.hex(0, 2))
            .asSequence()
            .mapNotNull { definition -> idi.decodeDate(definition)?.toLocalDateOrNull() }
            .firstOrNull { date -> date.isPlausible(manufacturingDate, currentDate) }
    }

    fun decodeSerialNumber(systemCode: String, idi: ByteArray?, pmi: ByteArray? = null): String? {
        if (idi == null || idi.size < IDI_SIZE) return null

        val prefix = idi.hex(0, 2)
        val definition = serialDefinition(systemCode, idi) ?: return null
        if (!definition.carriesSerial) return null
        definition.source?.let { source ->
            val bytes = if (source == IssuerSource.PMI) pmi else idi
            if (bytes == null || bytes.size < IDI_SIZE) return null
            return bytes.u32(NUMBER_OFFSET, definition.counterOrder).toString()
        }
        val code = definition.code ?: IssuerRegistry.codeForPrefix(prefix) ?: return null

        val date = idi.decodeDate(definition) ?: return null
        val counter = idi.u16(COUNTER_OFFSET, definition.counterOrder)
        return buildString {
            append(code)
            append(idi.hex(2, 4))
            append((date.year % 100).padded(2))
            append(date.month.padded(2))
            append(date.day.padded(2))
            append(counter.padded(definition.counterWidth))
        }
    }

    fun formatSerialNumberForDisplay(
        systemCode: String,
        idi: ByteArray?,
        serialNumber: String,
    ): String {
        if (idi == null || idi.size < IDI_SIZE) return serialNumber
        val firstGroupSize = serialDefinition(systemCode, idi)?.serialGroup ?: return serialNumber
        return serialNumber.grouped(firstGroupSize)
    }

    private fun serialDefinition(systemCode: String, idi: ByteArray): IssuerDefinition? {
        val rows = IssuerRegistry.definitionsFor(systemCode, idi.hex(0, 2))
        return rows.firstOrNull { it.code != null } ?: rows.firstOrNull { it.carriesSerial }
    }

    private data class EncodedDate(val year: Int, val month: Int, val day: Int) {
        fun toLocalDateOrNull(): LocalDate? =
            try {
                LocalDate(year, month, day)
            } catch (_: IllegalArgumentException) {
                null
            }
    }

    private fun ByteArray.decodeDate(definition: IssuerDefinition): EncodedDate? {
        val offset = definition.dateOffset
        if (
            offset < 0 || offset + (if (definition.format == IssueDateFormat.BCD) 4 else 2) > size
        ) {
            return null
        }
        return when (definition.format) {
            IssueDateFormat.PACKED ->
                u16(offset, ByteOrder.BE).let {
                    EncodedDate(2000 + ((it shr 9) and 0x3F), (it shr 5) and 0x0F, it and 0x1F)
                }
            IssueDateFormat.EPOCH -> runCatching {
                    FELICA_EPOCH.plus(u16(offset, definition.dateOrder), DateTimeUnit.DAY)
                }
                    .getOrNull()
                    ?.let { EncodedDate(it.year, it.monthNumber, it.dayOfMonth) }
            IssueDateFormat.BCD -> {
                val digits = (0 until 4).map { bcdAt(offset + it) ?: return null }
                EncodedDate(digits[0] * 100 + digits[1], digits[2], digits[3])
            }
            IssueDateFormat.NONE -> null
        }
    }

    private fun ByteArray.bcdAt(index: Int): Int? {
        val high = u8(index) shr 4
        val low = u8(index) and 0x0F
        return if (high <= 9 && low <= 9) high * 10 + low else null
    }

    private fun ByteArray.u16(offset: Int, order: ByteOrder): Int =
        when (order) {
            ByteOrder.LE -> u8(offset) or (u8(offset + 1) shl 8)
            ByteOrder.BE -> (u8(offset) shl 8) or u8(offset + 1)
        }

    private fun ByteArray.u32(offset: Int, order: ByteOrder): Long {
        val high = u16(if (order == ByteOrder.BE) offset else offset + 2, order).toLong()
        val low = u16(if (order == ByteOrder.BE) offset + 2 else offset, order).toLong()
        return (high shl 16) or low
    }

    private fun LocalDate.isPlausible(
        manufacturingDate: LocalDate?,
        currentDate: LocalDate,
    ): Boolean =
        this <= currentDate &&
            (manufacturingDate == null ||
                this >= manufacturingDate.plus(-MAX_ISSUANCE_DATE_LEAD_DAYS, DateTimeUnit.DAY))

    private fun ByteArray.u8(index: Int): Int = this[index].toInt() and 0xFF

    private fun ByteArray.hex(from: Int, until: Int): String =
        (from until until).joinToString("") { index ->
            u8(index).toString(16).uppercase().padStart(2, '0')
        }

    private fun Int.padded(width: Int): String = toString().padStart(width, '0')

    private fun String.grouped(firstGroupSize: Int): String =
        take(firstGroupSize) +
            drop(firstGroupSize).chunked(4).joinToString(prefix = " ", separator = " ")

    private const val IDI_SIZE = 8
    private const val COUNTER_OFFSET = 6
    private const val NUMBER_OFFSET = 4
    /** Issuance can predate the IC manufacture date by about a month. */
    private const val MAX_ISSUANCE_DATE_LEAD_DAYS = 31
}
