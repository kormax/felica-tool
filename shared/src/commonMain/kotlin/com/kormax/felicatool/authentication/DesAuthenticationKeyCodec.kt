package com.kormax.felicatool.authentication

import com.kormax.felicatool.felica.KeyVersion
import com.kormax.felicatool.security.SecretBytes
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

internal object DesAuthenticationKeyCodec {
    fun decodeImportedKeys(bytes: ByteArray): List<ImportedDesAuthenticationKey> =
        decodeList(
            records(decodeJson(bytes)).also {
                require(it.isNotEmpty()) { "Authentication key file contains no keys" }
            },
            ::decodeImportedKey,
        )

    fun decodeReferences(credentials: JsonArray) = decodeList(credentials, ::decodeReference)

    fun encodeReference(key: DesAuthenticationKeyReference): JsonObject =
        JsonObject(
            mapOf(
                "id" to JsonPrimitive(key.id),
                "system_code" to JsonPrimitive(key.scope.systemCode),
                "idm" to JsonPrimitive(key.scope.idm),
                "area_codes" to JsonArray(key.scope.areaCodes.map(::JsonPrimitive)),
                "node_codes" to JsonArray(key.scope.nodeCodes.map(::JsonPrimitive)),
                "key_versions" to
                    JsonObject(
                        key.versions.mapValues {
                            JsonPrimitive(it.value.toHexString().uppercase())
                        },
                    ),
                "fingerprint" to JsonPrimitive(key.fingerprint),
                "ala" to encodeProtectedKeyReference(key.ala),
                "lbl" to encodeProtectedKeyReference(key.lbl),
            ),
        )

    private fun decodeReference(root: JsonObject): DesAuthenticationKeyReference =
        DesAuthenticationKeyReference(
            root.requiredString("id"),
            DesAuthenticationScope(
                root.requiredHex("system_code", 2),
                root.requiredHex("idm", 8),
                root.requiredHexList("area_codes", 2),
                root.requiredHexList("node_codes", 2),
            ),
            root
                .requiredObject("key_versions")
                .map { (code, value) ->
                    code.requireHex("Version code '$code'", 2) to
                        KeyVersion.fromByteArray(
                            value.jsonPrimitive.content
                                .requireHex("Key version", 2)
                                .hexToByteArray(),
                        )
                }
                .toMap(),
            root.requiredString("fingerprint"),
            root.requiredObject("ala").toProtectedKeyReference(),
            root.requiredObject("lbl").toProtectedKeyReference(),
        )

    private fun records(root: JsonElement): JsonArray {
        if (root is JsonArray) return root
        val records = (root as? JsonObject)?.get("keys")
        require(records is JsonArray) {
            "Authentication key file must be an array or an object with a 'keys' array"
        }
        return records
    }

    private fun decodeImportedKey(root: JsonObject): ImportedDesAuthenticationKey {
        val scope =
            DesAuthenticationScope(
                root.requiredHex("system", 2),
                root.requiredHex("idm", 8),
                root.requiredHexList("areas", 2),
                root.requiredHexList("nodes", 2),
            )
        require(scope.areaCodes.distinct().size == scope.areaCodes.size) {
            "'areas' contains duplicates"
        }
        require(scope.nodeCodes.distinct().size == scope.nodeCodes.size) {
            "'nodes' contains duplicates"
        }
        val versionCodes = root.requiredObject("versions")
        val versions =
            versionCodes
                .map { (code, value) ->
                    code.requireHex("Version code '$code'", 2) to
                        KeyVersion.fromInt(
                            value.jsonPrimitive.intOrNull
                                ?: throw IllegalArgumentException(
                                    "Missing or invalid 'versions.$code'",
                                ),
                        )
                }
                .toMap()
        require(versions.size == versionCodes.size) { "'versions' contains duplicates" }
        val composite = root.containsKey("ala") || root.containsKey("lbl")
        require(!composite || listOf("a", "l", "b").none(root::containsKey)) {
            "Specify ala/lbl or a/l/b"
        }
        val parts = mutableListOf<ByteArray>()
        fun key(name: String, size: Int) = root.requiredKey(name, size).also(parts::add)
        return try {
            val ala: ByteArray
            val lbl: ByteArray
            if (composite) {
                ala = key("ala", 24)
                lbl = key("lbl", 24)
            } else {
                val a = key("a", 8)
                val l = key("l", 8)
                val b = key("b", 8)
                ala = concatenate(a, l, a).also(parts::add)
                lbl = concatenate(l, b, l).also(parts::add)
            }
            SecretBytes.fromByteArray(ala).use { alaSecret ->
                SecretBytes.fromByteArray(lbl).use { lblSecret ->
                    ImportedDesAuthenticationKey(scope, versions, alaSecret, lblSecret)
                }
            }
        } finally {
            parts.forEach { it.fill(0) }
        }
    }

    private fun JsonObject.requiredKey(name: String, size: Int): ByteArray {
        val bytes = requiredString(name).hexBytes("'$name'")
        require(bytes.size == size) {
            bytes.fill(0)
            "'$name' must contain exactly $size bytes"
        }
        return bytes
    }

    private fun concatenate(vararg parts: ByteArray): ByteArray {
        val result = ByteArray(parts.sumOf { it.size })
        var offset = 0
        for (part in parts) {
            part.copyInto(result, offset)
            offset += part.size
        }
        return result
    }
}
