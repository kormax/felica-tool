package com.kormax.felicatool.util

import com.kormax.felicatool.service.CardScanContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Resolves the manufacturing date encoded in a FeliCa card's primary IDM. */
object ManufacturingDateResolver {
    private val FELICA_EPOCH = LocalDate(2000, 1, 1)

    // FeliCa AES was announced in June 2011; allow earlier dates for stock manufactured in advance.
    private val AES_MINIMUM_DATE = LocalDate(2010, 1, 1)

    /**
     * Resolves a manufacturing date using the current time and build date as the upper bound. Both
     * timestamps are epoch milliseconds and are interpreted as UTC calendar dates.
     */
    @OptIn(ExperimentalTime::class)
    fun resolve(
        context: CardScanContext,
        currentTimeMillis: Long,
        buildDateEpochMillis: Long,
    ): LocalDate? {
        val daysSince2000 = context.primaryIdm?.readManufacturingDayOffset() ?: return null
        if (daysSince2000 < 0) return null

        val minimumDate = if (context.isAesCard()) AES_MINIMUM_DATE else FELICA_EPOCH
        val manufacturingDate = FELICA_EPOCH.plus(daysSince2000, DateTimeUnit.DAY)
        val maximumDate =
            Instant.fromEpochMilliseconds(maxOf(currentTimeMillis, buildDateEpochMillis))
                .toLocalDateTime(TimeZone.UTC)
                .date

        return manufacturingDate.takeIf { it in minimumDate..maximumDate }
    }

    private fun ByteArray.readManufacturingDayOffset(): Int? {
        if (size < 6) return null

        val unsignedValue = (this[4].toInt() and 0xFF) or ((this[5].toInt() and 0xFF) shl 8)
        return unsignedValue.toShort().toInt()
    }

    private fun CardScanContext.isAesCard(): Boolean {
        return systemScanContexts.any { system ->
            system.encryptionIdentifier != null || system.nodeAesKeyVersions.isNotEmpty()
        }
    }
}
