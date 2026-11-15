package com.kormax.felicatool.authentication

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object AuthenticationProviderConfigurationCodec {
    private val json = Json

    fun encode(configurations: AuthenticationProviderConfigurations): ByteArray =
        json
            .encodeToString(
                JsonElement.serializer(),
                JsonObject(
                    mapOf(
                        "format_version" to JsonPrimitive(FORMAT_VERSION),
                        "providers" to
                            JsonArray(
                                configurations.providers.map(::encodeProvider) +
                                    configurations.unreadableProviders.map(
                                        json::parseToJsonElement,
                                    ),
                            ),
                    ),
                ),
            )
            .encodeToByteArray()

    fun decode(bytes: ByteArray): AuthenticationProviderConfigurations {
        if (bytes.isEmpty()) return AuthenticationProviderConfigurations()
        val root = decodeJson(bytes).jsonObject
        require(root.requiredInt("format_version") == FORMAT_VERSION) {
            "Unsupported authentication provider configuration version"
        }
        val decoded = mutableListOf<AuthenticationProviderConfiguration>()
        val unreadable = mutableListOf<String>()
        for (element in root.requiredArray("providers")) {
            runCatching { decodeProvider(element.jsonObject) }
                .onSuccess { provider ->
                    if (decoded.none { it.id == provider.id }) decoded += provider
                    else unreadable += element.toString()
                }
                .onFailure { unreadable += element.toString() }
        }
        return AuthenticationProviderConfigurations(decoded, unreadable)
    }

    fun strings(raw: String): List<String> {
        fun collect(element: JsonElement): List<String> =
            when (element) {
                is JsonObject -> element.values.flatMap(::collect)
                is JsonArray -> element.flatMap(::collect)
                is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
            }
        return collect(json.parseToJsonElement(raw))
    }

    private fun encodeProvider(provider: AuthenticationProviderConfiguration) =
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(provider.id),
                "display_name" to JsonPrimitive(provider.displayName),
                "enabled" to JsonPrimitive(provider.isEnabled),
                "type" to JsonPrimitive(provider.type.name),
            ) + provider.encodeFields(),
        )

    private fun decodeProvider(root: JsonObject): AuthenticationProviderConfiguration {
        val type = root.requiredString("type")
        return (AuthenticationProviderType.entries.firstOrNull { it.name == type }
                ?: throw IllegalArgumentException("Unknown authentication provider type '$type'"))
            .decode(
                root.requiredString("id"),
                root.requiredString("display_name"),
                root.requiredBoolean("enabled"),
                root,
            )
    }

    private const val FORMAT_VERSION = 1
}

internal fun decodeJson(bytes: ByteArray): JsonElement =
    Json.parseToJsonElement(bytes.decodeToString())

internal inline fun <T> decodeList(elements: JsonArray, decode: (JsonObject) -> T): List<T> {
    val result = mutableListOf<T>()
    try {
        elements.forEachIndexed { index, element ->
            result +=
                try {
                    decode(element.jsonObject)
                } catch (error: IllegalArgumentException) {
                    throw IllegalArgumentException("Entry ${index + 1}: ${error.message}", error)
                }
        }
        return result
    } catch (error: Throwable) {
        result.forEach { (it as? AutoCloseable)?.close() }
        throw error
    }
}

internal fun encodeProtectedKeyReference(reference: ProtectedKeyReference): JsonObject =
    JsonObject(
        mapOf(
            "id" to JsonPrimitive(reference.id),
            "storage" to JsonPrimitive(reference.storage.name),
        ),
    )

internal fun JsonObject.toProtectedKeyReference(): ProtectedKeyReference {
    val storage = requiredString("storage")
    return ProtectedKeyReference(
        requiredString("id"),
        KeyStorage.entries.firstOrNull { it.name == storage }
            ?: throw IllegalArgumentException("Unknown key storage '$storage'"),
    )
}

internal fun JsonObject.requiredString(name: String): String =
    this[name]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Missing '$name'")

internal fun JsonObject.requiredInt(name: String): Int =
    this[name]?.jsonPrimitive?.intOrNull
        ?: throw IllegalArgumentException("Missing or invalid '$name'")

internal fun JsonObject.requiredBoolean(name: String): Boolean =
    this[name]?.jsonPrimitive?.booleanOrNull
        ?: throw IllegalArgumentException("Missing or invalid '$name'")

internal fun JsonObject.requiredArray(name: String): JsonArray =
    this[name]?.jsonArray ?: throw IllegalArgumentException("Missing '$name'")

internal fun JsonObject.requiredObject(name: String): JsonObject =
    this[name]?.jsonObject ?: throw IllegalArgumentException("Missing '$name'")

internal fun JsonObject.requiredHex(name: String, size: Int): String =
    requiredString(name).requireHex("'$name'", size)

internal fun JsonObject.requiredHexList(name: String, size: Int): List<String> =
    requiredArray(name).map { element ->
        element.jsonPrimitive.content.requireHex("'$name' entry", size)
    }

internal fun String.requireHex(name: String, size: Int): String =
    uppercase()
        .also { it.hexBytes(name) }
        .also { require(it.length == size * 2) { "$name must contain exactly $size bytes" } }

internal fun String.hexBytes(name: String): ByteArray =
    try {
        hexToByteArray()
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("$name is not valid hex")
    }
