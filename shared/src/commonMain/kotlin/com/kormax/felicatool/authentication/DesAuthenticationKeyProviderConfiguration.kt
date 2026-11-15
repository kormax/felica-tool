package com.kormax.felicatool.authentication

import com.kormax.felicatool.felica.KeyVersion
import com.kormax.felicatool.security.SecretBytes
import com.kormax.felicatool.security.sha256
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

data class DesAuthenticationKeySummary(
    override val id: String,
    val systemCode: String,
    val idm: String,
    val areaCodes: List<String>,
    val nodeCodes: List<String>,
    val keyVersions: Map<String, Int>,
    val storage: KeyStorage,
) : AuthenticationCredentialSummary

internal class DesAuthenticationKeyProviderConfiguration(
    id: String,
    displayName: String,
    isEnabled: Boolean,
    val keys: MutableList<DesAuthenticationKeyReference> = mutableListOf(),
) : LocalAuthenticationProviderConfiguration(id, displayName, isEnabled) {
    override val type = AuthenticationProviderType.DES_AUTHENTICATION

    override val protectedKeys
        get() = keys.flatMap { listOf(it.ala, it.lbl) }

    override fun snapshot() =
        DesAuthenticationKeyProviderConfiguration(id, displayName, isEnabled, keys.toMutableList())

    override fun encodeFields() =
        mapOf("credentials" to JsonArray(keys.map(DesAuthenticationKeyCodec::encodeReference)))

    override fun credentialSummaries() = keys.map {
        DesAuthenticationKeySummary(
            it.id,
            it.scope.systemCode,
            it.scope.idm,
            it.scope.areaCodes,
            it.scope.nodeCodes,
            it.versions.mapValues { (_, version) -> version.toInt() },
            minOf(it.ala.storage, it.lbl.storage),
        )
    }

    companion object {
        fun decode(id: String, displayName: String, isEnabled: Boolean, root: JsonObject) =
            DesAuthenticationKeyProviderConfiguration(
                id,
                displayName,
                isEnabled,
                DesAuthenticationKeyCodec.decodeReferences(root.requiredArray("credentials"))
                    .toMutableList(),
            )
    }

    override suspend fun keyStorages(context: LocalCredentialContext) = context.keyStore.storages()

    override fun validateCredentials(data: ByteArray) {
        DesAuthenticationKeyCodec.decodeImportedKeys(data)
            .forEach(ImportedDesAuthenticationKey::close)
    }

    override suspend fun importCredentials(
        data: ByteArray,
        storage: KeyStorage,
        context: LocalCredentialContext,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): CredentialImportResult {
        val keyStore = context.keyStore
        val imported = DesAuthenticationKeyCodec.decodeImportedKeys(data)
        try {
            val total = imported.size
            var moved = 0
            var alreadyStored = 0
            var repeated = 0
            val importedIds = mutableSetOf<String>()
            onProgress(0, total)
            imported.forEachIndexed { position, key ->
                val existing = keys.indexOfFirst {
                    it.fingerprint == key.fingerprint && it.hasSameIdentity(key)
                }
                if (existing >= 0) {
                    val stored = keys[existing]
                    if (stored.id in importedIds) {
                        repeated++
                        onProgress(position + 1, total)
                        return@forEachIndexed
                    }
                    if (stored.ala.storage == storage && stored.lbl.storage == storage) {
                        importedIds += stored.id
                        alreadyStored++
                        onProgress(position + 1, total)
                        return@forEachIndexed
                    }
                    moved++
                }
                val id = if (existing < 0) context.newId() else keys[existing].id
                val ala = keyStore.protect(context.newId(), key.ala, DesKeyUse.ENCRYPT, storage)
                val lbl =
                    try {
                        keyStore.protect(context.newId(), key.lbl, DesKeyUse.DECRYPT, storage)
                    } catch (error: Throwable) {
                        runCatching { keyStore.delete(ala.id) }
                        throw error
                    }
                val stored =
                    DesAuthenticationKeyReference(
                        id,
                        key.scope,
                        key.versions,
                        key.fingerprint,
                        ala,
                        lbl,
                    )
                importedIds += id
                if (existing < 0) keys += stored else keys[existing] = stored
                onProgress(position + 1, total)
            }
            return CredentialImportResult(
                total - moved - alreadyStored - repeated,
                moved,
                alreadyStored,
                repeated,
            )
        } finally {
            imported.forEach(ImportedDesAuthenticationKey::close)
        }
    }

    override fun deleteCredentials(ids: Set<String>) {
        require(keys.removeAll { it.id in ids }) { "Authentication credentials do not exist" }
    }
}

internal class DesAuthenticationKeyReference(
    val id: String,
    scope: DesAuthenticationScope,
    versions: Map<String, KeyVersion>,
    val fingerprint: String,
    val ala: ProtectedKeyReference,
    val lbl: ProtectedKeyReference,
) {
    val scope = scope.requireValid(versions)
    val versions = versions.toMap()

    fun hasSameIdentity(other: ImportedDesAuthenticationKey) =
        scope == other.scope && versions == other.versions
}

internal class ImportedDesAuthenticationKey(
    scope: DesAuthenticationScope,
    versions: Map<String, KeyVersion>,
    ala: SecretBytes,
    lbl: SecretBytes,
) : AutoCloseable {
    val scope = scope.requireValid(versions)
    val versions = versions.toMap()
    val ala = ala.copy()
    val lbl = lbl.copy()

    init {
        require(this.ala.size == 24 && this.lbl.size == 24) {
            "DES authentication keys must be exactly 24 bytes"
        }
    }

    val fingerprint: String = run {
        val alaBytes = this.ala.copyToByteArray()
        val lblBytes = this.lbl.copyToByteArray()
        val material = alaBytes + lblBytes
        try {
            sha256(material).toHexString()
        } finally {
            alaBytes.fill(0)
            lblBytes.fill(0)
            material.fill(0)
        }
    }

    override fun close() {
        ala.close()
        lbl.close()
    }
}

private fun DesAuthenticationScope.requireValid(
    versions: Map<String, KeyVersion>,
): DesAuthenticationScope {
    val missingVersions = (listOf("FFFF") + areaCodes + nodeCodes).filterNot(versions::containsKey)
    require(missingVersions.isEmpty()) {
        "Key versions are missing for ${missingVersions.joinToString()}"
    }
    return copy(areaCodes = areaCodes.toList(), nodeCodes = nodeCodes.toList())
}
