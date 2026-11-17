package com.kormax.felicatool.util

import com.kormax.felicatool.shared.resources.Res
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class ByteOrder {
    LE,
    BE,
}

enum class IssueDateFormat {
    /** 16-bit BE field: 6-bit year since 2000, 4-bit month, 5-bit day. */
    PACKED,
    /** 16-bit count of days since 2000-01-01. */
    EPOCH,
    /** Century, year, month, day, one BCD byte each. */
    BCD,
    NONE,
}

enum class IssuerSource {
    IDI,
    PMI,
}

data class IssuerDefinition(
    val system: String,
    val prefix: String?,
    val code: String?,
    val format: IssueDateFormat,
    val source: IssuerSource?,
    val serialGroup: Int?,
    val serialSupported: Boolean,
    val dateOffsetOverride: Int?,
    val dateOrderOverride: ByteOrder?,
    val counterOrderOverride: ByteOrder?,
    val counterWidthOverride: Int?,
) {
    private val isEpoch: Boolean
        get() = format == IssueDateFormat.EPOCH

    val dateOffset: Int
        get() = dateOffsetOverride ?: if (format == IssueDateFormat.BCD) 0 else 4

    val dateOrder: ByteOrder
        get() = dateOrderOverride ?: ByteOrder.LE

    val counterOrder: ByteOrder
        get() = counterOrderOverride ?: if (isEpoch) ByteOrder.LE else ByteOrder.BE

    val counterWidth: Int
        get() = counterWidthOverride ?: if (isEpoch) 4 else 5

    val carriesSerial: Boolean
        get() = serialSupported && (source != null || isEpoch || format == IssueDateFormat.PACKED)

    internal fun specificity(systemCode: String): Int? =
        if (system.endsWith("*")) {
            system.dropLast(1).takeIf { systemCode.startsWith(it) }?.length
        } else {
            Int.MAX_VALUE.takeIf { system == systemCode }
        }
}

object IssuerRegistry {
    private val json = Json { ignoreUnknownKeys = true }
    private val initializationMutex = Mutex()
    private var initialized = false
    private var definitions: List<IssuerDefinition> = emptyList()
    private var codesByPrefix: Map<String, String> = emptyMap()

    suspend fun ensureReady() {
        if (initialized) {
            return
        }

        initializationMutex.withLock {
            if (!initialized) {
                ensureInitialized(Res.readBytes("files/issuers.json").decodeToString())
            }
        }
    }

    fun ensureInitialized(jsonText: String) {
        if (initialized) {
            return
        }

        definitions = parseDefinitions(jsonText)
        codesByPrefix =
            definitions
                .mapNotNull { definition ->
                    definition.prefix?.let { prefix -> definition.code?.let { prefix to it } }
                }
                .toMap()
        initialized = true
    }

    fun definitionsFor(systemCode: String, idiPrefix: String?): List<IssuerDefinition> {
        val system = systemCode.uppercase()
        val prefix = idiPrefix?.uppercase()
        val best =
            definitions.mapNotNull { it.specificity(system) }.maxOrNull() ?: return emptyList()
        return definitions.filter {
            it.specificity(system) == best && (it.prefix == null || it.prefix == prefix)
        }
    }

    fun codeForPrefix(idiPrefix: String): String? = codesByPrefix[idiPrefix.uppercase()]

    private fun parseDefinitions(jsonText: String): List<IssuerDefinition> =
        json.parseToJsonElement(jsonText).jsonArray.map { element ->
            val row = element.jsonObject
            IssuerDefinition(
                system = row.text("system") ?: "*",
                prefix = row.text("prefix"),
                code = row.text("code"),
                format = row.enum<IssueDateFormat>("format") ?: IssueDateFormat.NONE,
                source = row.enum<IssuerSource>("source"),
                serialGroup = row.number("serial_group")?.takeIf { it > 0 },
                serialSupported = row["serial"]?.jsonPrimitive?.booleanOrNull ?: true,
                dateOffsetOverride = row.number("date_offset"),
                dateOrderOverride = row.enum<ByteOrder>("date_endian"),
                counterOrderOverride = row.enum<ByteOrder>("counter_endian"),
                counterWidthOverride = row.number("counter_width")?.takeIf { it in 1..16 },
            )
        }

    private fun JsonObject.text(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private inline fun <reified T : Enum<T>> JsonObject.enum(key: String): T? =
        text(key)?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
}
