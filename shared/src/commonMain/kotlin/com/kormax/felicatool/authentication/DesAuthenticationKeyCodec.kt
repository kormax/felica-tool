package com.kormax.felicatool.authentication

import com.kormax.felicatool.felica.KeyVersion
import com.kormax.felicatool.security.DESEDE_KEY_SIZE
import com.kormax.felicatool.security.DES_KEY_SIZE
import com.kormax.felicatool.security.SecretBytes
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object DesAuthenticationKeyCodec {
    private const val MIN_SALT_BYTES = 16

    fun decodeImportFile(bytes: ByteArray): DesKeyImportFile {
        val root = decodeJson(bytes)
        val recipient = (root as? JsonObject)?.get("recipient")?.jsonObject?.toRecipient()
        if (root is JsonObject && root.containsKey("encrypted")) {
            require(!root.containsKey("keys")) { "Specify either 'keys' or 'encrypted'" }
            val encrypted = root.requiredObject("encrypted")
            return DesKeyImportFile.Encrypted(
                requireNotNull(recipient) { "Encrypted key file must name its 'recipient'" },
                encrypted.requiredObject("key").wrappedData("encrypted.key"),
                encrypted.requiredString("iv").base64Bytes("'iv'"),
                encrypted.requiredString("data").base64Bytes("'data'"),
            )
        }
        val keys =
            decodeList(
                records(root).also {
                    require(it.isNotEmpty()) { "Authentication key file contains no keys" }
                },
                ::decodeImportedKey,
            )
        if (recipient == null && keys.any { it.keys !is ImportedDesKeys.Raw }) {
            keys.forEach(ImportedDesAuthenticationKey::close)
            throw IllegalArgumentException("Wrapped keys require a 'recipient'")
        }
        return DesKeyImportFile.Keys(recipient, keys)
    }

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
            ) +
                when (val keys = key.keys) {
                    is DesKeyReferences.Composite ->
                        mapOf(
                            "ala" to encodeProtectedKeyReference(keys.ala),
                            "lbl" to encodeProtectedKeyReference(keys.lbl),
                        )
                    is DesKeyReferences.Components ->
                        mapOf(
                            "a" to encodeProtectedKeyReference(keys.a),
                            "l" to encodeProtectedKeyReference(keys.l),
                            "b" to encodeProtectedKeyReference(keys.b),
                        )
                },
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
            if (root.containsKey("ala")) {
                DesKeyReferences.Composite(
                    root.requiredObject("ala").toProtectedKeyReference(),
                    root.requiredObject("lbl").toProtectedKeyReference(),
                )
            } else {
                DesKeyReferences.Components(
                    root.requiredObject("a").toProtectedKeyReference(),
                    root.requiredObject("l").toProtectedKeyReference(),
                    root.requiredObject("b").toProtectedKeyReference(),
                )
            },
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
        val names = if (composite) listOf("ala", "lbl") else listOf("a", "l", "b")
        names.forEach { require(root.containsKey(it)) { "Missing '$it'" } }
        val wrapped = names.map { root[it] is JsonObject }
        require(wrapped.all { it } || wrapped.none { it }) {
            "Keys in a record must be all wrapped or all raw"
        }
        if (wrapped.first()) {
            val data = names.map { root.requiredObject(it).wrappedData("'$it'") }
            val keys =
                if (composite) ImportedDesKeys.WrappedComposite(data[0], data[1])
                else ImportedDesKeys.WrappedComponents(data[0], data[1], data[2])
            return ImportedDesAuthenticationKey(scope, versions, keys)
        }
        val parts = mutableListOf<ByteArray>()
        fun key(name: String, size: Int) = root.requiredKey(name, size).also(parts::add)
        return try {
            val ala: ByteArray
            val lbl: ByteArray
            if (composite) {
                ala = key("ala", DESEDE_KEY_SIZE)
                lbl = key("lbl", DESEDE_KEY_SIZE)
            } else {
                val a = key("a", DES_KEY_SIZE)
                val l = key("l", DES_KEY_SIZE)
                val b = key("b", DES_KEY_SIZE)
                ala = concatenate(a, l, a).also(parts::add)
                lbl = concatenate(l, b, l).also(parts::add)
            }
            SecretBytes.fromByteArray(ala).use { alaSecret ->
                SecretBytes.fromByteArray(lbl).use { lblSecret ->
                    ImportedDesAuthenticationKey(
                        scope,
                        versions,
                        ImportedDesKeys.Raw(alaSecret, lblSecret),
                    )
                }
            }
        } finally {
            parts.forEach { it.fill(0) }
        }
    }

    private fun JsonObject.toRecipient(): KeyImportRecipient {
        val identity = requiredObject("identity")
        return KeyImportRecipient(
            requiredString("method"),
            identity.requiredString("salt").hexBytes("'recipient.identity.salt'").also {
                require(it.size >= MIN_SALT_BYTES) {
                    "'recipient.identity.salt' must be at least $MIN_SALT_BYTES bytes"
                }
            },
            identity.requiredHex("hash", 32).hexToByteArray(),
        )
    }

    private fun JsonObject.wrappedData(name: String): ByteArray {
        require(requiredString("type") == "wrapped") { "$name must be of type 'wrapped'" }
        return requiredString("data").base64Bytes("$name.data")
    }

    private fun String.base64Bytes(name: String): ByteArray =
        try {
            Base64.Default.decode(this)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("$name must be base64", error)
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
