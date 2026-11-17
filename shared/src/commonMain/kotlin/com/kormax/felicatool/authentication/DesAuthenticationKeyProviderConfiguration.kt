package com.kormax.felicatool.authentication

import com.kormax.felicatool.felica.KeyVersion
import com.kormax.felicatool.security.DESEDE_KEY_SIZE
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
    val components: List<DesKeyComponent>,
) : AuthenticationCredentialSummary

data class DesKeyComponent(val name: String, val storage: KeyStorage)

internal class DesAuthenticationKeyProviderConfiguration(
    id: String,
    displayName: String,
    isEnabled: Boolean,
    val keys: MutableList<DesAuthenticationKeyReference> = mutableListOf(),
) : LocalAuthenticationProviderConfiguration(id, displayName, isEnabled) {

    override val type = AuthenticationProviderType.DES_AUTHENTICATION

    override val protectedKeys
        get() = keys.flatMap { it.keys.all }

    override fun snapshot() =
        DesAuthenticationKeyProviderConfiguration(
            id,
            displayName,
            isEnabled,
            keys.toMutableList(),
        )

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
            it.keys.all.minOf(ProtectedKeyReference::storage),
            when (val keys = it.keys) {
                is DesKeyReferences.Composite ->
                    listOf(
                        DesKeyComponent("ala", keys.ala.storage),
                        DesKeyComponent("lbl", keys.lbl.storage),
                    )
                is DesKeyReferences.Components ->
                    listOf(
                        DesKeyComponent("a", keys.a.storage),
                        DesKeyComponent("l", keys.l.storage),
                        DesKeyComponent("b", keys.b.storage),
                    )
            },
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

    override suspend fun prepareCredentialImport(
        data: ByteArray,
        context: LocalCredentialContext,
    ): PreparedCredentialImport {
        val keyStore = context.keyStore
        return when (val file = DesAuthenticationKeyCodec.decodeImportFile(data)) {
            is DesKeyImportFile.Encrypted -> {
                val plaintext =
                    keyStore.decryptWrapped(file.recipient, file.key, file.iv, file.data)
                try {
                    decodeDecryptedFile(plaintext).use { inner ->
                        PreparedCredentialImport(plaintext, requiredStorage(inner, keyStore))
                    }
                } catch (error: Throwable) {
                    plaintext.fill(0)
                    throw error
                }
            }
            is DesKeyImportFile.Keys ->
                file.use { PreparedCredentialImport(data.copyOf(), requiredStorage(it, keyStore)) }
        }
    }

    private suspend fun requiredStorage(
        file: DesKeyImportFile.Keys,
        keyStore: ProtectedDesKeyStore,
    ): KeyStorage? =
        file.recipient
            ?.let { keyStore.recipientStorage(it) }
            ?.takeIf { file.keys.none { it.keys is ImportedDesKeys.Raw } }

    override suspend fun importCredentials(
        data: ByteArray,
        storage: KeyStorage,
        context: LocalCredentialContext,
        configurations: AuthenticationProviderConfigurations,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): CredentialImportResult {
        val keyStore = context.keyStore
        val file = DesAuthenticationKeyCodec.decodeImportFile(data)
        require(file is DesKeyImportFile.Keys) {
            file.close()
            "Encrypted key files must be prepared before import"
        }
        try {
            val recipientStorage = file.recipient?.let { keyStore.recipientStorage(it) }
            val imported = file.keys
            val total = imported.size
            var moved = 0
            var alreadyStored = 0
            var repeated = 0
            var refreshed = 0
            val importedIds = mutableSetOf<String>()
            val salt = configurations.fingerprintSalt(context.randomSource)
            onProgress(0, total)
            imported.forEachIndexed { position, key ->
                val target =
                    if (key.keys is ImportedDesKeys.Raw) storage else checkNotNull(recipientStorage)
                val protected = protect(key.keys, file.recipient, target, context)
                val fingerprint =
                    try {
                        fingerprint(protected, keyStore, salt)
                    } catch (error: Throwable) {
                        protected.all.forEach { runCatching { keyStore.delete(it.id) } }
                        throw error
                    }
                val providers =
                    configurations.providers.filterIsInstance<
                        DesAuthenticationKeyProviderConfiguration,
                    >()
                fun DesAuthenticationKeyReference.isSameKeyIn(storage: KeyStorage) =
                    this.fingerprint == fingerprint && keys.all.all { it.storage == storage }
                val current =
                    providers.flatMap { it.keys }.firstOrNull { it.isSameKeyIn(target) }?.keys
                val refresh =
                    current is DesKeyReferences.Components &&
                        protected is DesKeyReferences.Composite
                val set =
                    if (refresh) {
                        providers.forEach { provider ->
                            provider.keys.replaceAll {
                                if (it.isSameKeyIn(target)) it.withKeys(protected) else it
                            }
                        }
                        protected
                    } else if (current != null) {
                        protected.all.forEach { runCatching { keyStore.delete(it.id) } }
                        current
                    } else {
                        protected
                    }
                val existing = keys.indexOfFirst {
                    it.fingerprint == fingerprint && it.hasSameIdentity(key)
                }
                if (existing >= 0) {
                    val stored = keys[existing]
                    val duplicate = stored.id in importedIds
                    if (duplicate || stored.keys.all.all { it.storage == target }) {
                        when {
                            duplicate -> repeated++
                            refresh -> refreshed++
                            else -> alreadyStored++
                        }
                        importedIds += stored.id
                        onProgress(position + 1, total)
                        return@forEachIndexed
                    }
                    moved++
                }
                val id = if (existing < 0) context.newId() else keys[existing].id
                val stored =
                    DesAuthenticationKeyReference(id, key.scope, key.versions, fingerprint, set)
                importedIds += id
                if (existing < 0) keys += stored else keys[existing] = stored
                onProgress(position + 1, total)
            }
            return CredentialImportResult(
                total - moved - alreadyStored - repeated - refreshed,
                moved,
                alreadyStored,
                repeated,
                refreshed,
            )
        } finally {
            file.close()
        }
    }

    private suspend fun fingerprint(
        keys: DesKeyReferences,
        keyStore: ProtectedDesKeyStore,
        block: ByteArray,
    ): String {
        val encrypted = keys.encrypt(keyStore, block)
        val decrypted = keys.decrypt(keyStore, block)
        val material = encrypted + decrypted
        return try {
            sha256(material).toHexString()
        } finally {
            encrypted.fill(0)
            decrypted.fill(0)
            material.fill(0)
        }
    }

    private fun decodeDecryptedFile(plaintext: ByteArray): DesKeyImportFile.Keys {
        val inner = DesAuthenticationKeyCodec.decodeImportFile(plaintext)
        require(inner is DesKeyImportFile.Keys) {
            inner.close()
            "Encrypted key file must contain keys"
        }
        return inner
    }

    private suspend fun protect(
        material: ImportedDesKeys,
        recipient: KeyImportRecipient?,
        storage: KeyStorage,
        context: LocalCredentialContext,
    ): DesKeyReferences {
        val keyStore = context.keyStore
        val created = mutableListOf<ProtectedKeyReference>()
        suspend fun wrapped(key: ByteArray, use: DesKeyUse) =
            keyStore
                .importWrapped(context.newId(), checkNotNull(recipient), key, use)
                .also(created::add)
        try {
            return when (material) {
                is ImportedDesKeys.Raw ->
                    DesKeyReferences.Composite(
                        keyStore
                            .protect(context.newId(), material.ala, DesKeyUse.ENCRYPT, storage)
                            .also(created::add),
                        keyStore
                            .protect(context.newId(), material.lbl, DesKeyUse.DECRYPT, storage)
                            .also(created::add),
                    )
                is ImportedDesKeys.WrappedComposite ->
                    DesKeyReferences.Composite(
                        wrapped(material.ala, DesKeyUse.ENCRYPT),
                        wrapped(material.lbl, DesKeyUse.DECRYPT),
                    )
                is ImportedDesKeys.WrappedComponents ->
                    DesKeyReferences.Components(
                        wrapped(material.a, DesKeyUse.ENCRYPT),
                        wrapped(material.l, DesKeyUse.DECRYPT),
                        wrapped(material.b, DesKeyUse.ENCRYPT),
                    )
            }
        } catch (error: Throwable) {
            created.forEach { runCatching { keyStore.delete(it.id) } }
            throw error
        }
    }

    override fun deleteCredentials(ids: Set<String>) {
        require(keys.removeAll { it.id in ids }) { "Authentication credentials do not exist" }
    }

    override fun authenticationProvider(
        context: AuthenticationProviderContext,
    ): AuthenticationProvider =
        DesAuthenticationKeyProvider(
            AuthenticationProviderDescriptor(id, displayName),
            keys.toList(),
            context.keyStore,
            context.cryptography,
            context.randomSource,
        )
}

internal sealed interface DesKeyReferences {
    val all: List<ProtectedKeyReference>

    suspend fun encrypt(keyStore: ProtectedDesKeyStore, block: ByteArray): ByteArray

    suspend fun decrypt(keyStore: ProtectedDesKeyStore, block: ByteArray): ByteArray

    class Composite(val ala: ProtectedKeyReference, val lbl: ProtectedKeyReference) :
        DesKeyReferences {
        override val all
            get() = listOf(ala, lbl)

        override suspend fun encrypt(keyStore: ProtectedDesKeyStore, block: ByteArray) =
            keyStore.encrypt(ala.id, block)

        override suspend fun decrypt(keyStore: ProtectedDesKeyStore, block: ByteArray) =
            keyStore.decrypt(lbl.id, block)
    }

    class Components(
        val a: ProtectedKeyReference,
        val l: ProtectedKeyReference,
        val b: ProtectedKeyReference,
    ) : DesKeyReferences {
        override val all
            get() = listOf(a, l, b)

        override suspend fun encrypt(keyStore: ProtectedDesKeyStore, block: ByteArray) =
            chain(
                block,
                { keyStore.encrypt(a.id, it) },
                { keyStore.decrypt(l.id, it) },
                { keyStore.encrypt(a.id, it) },
            )

        override suspend fun decrypt(keyStore: ProtectedDesKeyStore, block: ByteArray) =
            chain(
                block,
                { keyStore.decrypt(l.id, it) },
                { keyStore.encrypt(b.id, it) },
                { keyStore.decrypt(l.id, it) },
            )

        private suspend fun chain(
            block: ByteArray,
            vararg steps: suspend (ByteArray) -> ByteArray,
        ): ByteArray =
            steps.fold(block) { input, step ->
                try {
                    step(input)
                } finally {
                    if (input !== block) input.fill(0)
                }
            }
    }
}

internal class DesAuthenticationKeyReference(
    val id: String,
    scope: DesAuthenticationScope,
    versions: Map<String, KeyVersion>,
    val fingerprint: String,
    val keys: DesKeyReferences,
) {
    val scope = scope.requireValid(versions)
    val versions = versions.toMap()

    fun withKeys(keys: DesKeyReferences) =
        DesAuthenticationKeyReference(id, scope, versions, fingerprint, keys)

    fun hasSameIdentity(other: ImportedDesAuthenticationKey) =
        scope == other.scope && versions == other.versions
}

internal sealed interface ImportedDesKeys : AutoCloseable {
    class Raw(ala: SecretBytes, lbl: SecretBytes) : ImportedDesKeys {
        val ala = ala.copy()
        val lbl = lbl.copy()

        init {
            require(this.ala.size == DESEDE_KEY_SIZE && this.lbl.size == DESEDE_KEY_SIZE) {
                "DES authentication keys must be exactly $DESEDE_KEY_SIZE bytes"
            }
        }

        override fun close() {
            ala.close()
            lbl.close()
        }
    }

    class WrappedComposite(val ala: ByteArray, val lbl: ByteArray) : ImportedDesKeys {
        override fun close() = Unit
    }

    class WrappedComponents(val a: ByteArray, val l: ByteArray, val b: ByteArray) :
        ImportedDesKeys {
        override fun close() = Unit
    }
}

internal class ImportedDesAuthenticationKey(
    scope: DesAuthenticationScope,
    versions: Map<String, KeyVersion>,
    val keys: ImportedDesKeys,
) : AutoCloseable {
    val scope = scope.requireValid(versions)
    val versions = versions.toMap()

    override fun close() = keys.close()
}

internal sealed interface DesKeyImportFile : AutoCloseable {
    class Keys(val recipient: KeyImportRecipient?, val keys: List<ImportedDesAuthenticationKey>) :
        DesKeyImportFile {
        override fun close() = keys.forEach(ImportedDesAuthenticationKey::close)
    }

    class Encrypted(
        val recipient: KeyImportRecipient,
        val key: ByteArray,
        val iv: ByteArray,
        val data: ByteArray,
    ) : DesKeyImportFile {
        override fun close() = Unit
    }
}

private fun DesAuthenticationScope.requireValid(
    versions: Map<String, KeyVersion>,
): DesAuthenticationScope {
    val missingVersions =
        (listOf(DesAuthenticationKeyProvider.SYSTEM_NODE_CODE) + areaCodes).filterNot(
            versions::containsKey,
        )
    require(missingVersions.isEmpty()) {
        "Key versions are missing for ${missingVersions.joinToString()}"
    }
    return copy(areaCodes = areaCodes.toList(), nodeCodes = nodeCodes.toList())
}
