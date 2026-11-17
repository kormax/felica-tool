package com.kormax.felicatool.authentication

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.security.keystore.WrappedKeyEntry
import com.kormax.felicatool.device.KeyAlgorithm
import com.kormax.felicatool.device.KeystoreBackend
import com.kormax.felicatool.device.KeystoreCapabilities
import com.kormax.felicatool.device.MeasurementState
import com.kormax.felicatool.device.Support
import com.kormax.felicatool.security.DESEDE_KEY_SIZE
import com.kormax.felicatool.security.DES_BLOCK_SIZE
import com.kormax.felicatool.security.SecretBytes
import java.security.KeyStore
import java.security.MessageDigest
import java.security.ProviderException
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AndroidProtectedDesKeyStore(context: Context) : ProtectedDesKeyStore {
    private val app = context.applicationContext
    private val identities = AndroidKeyImportIdentities(app)
    @Volatile private var resolvedIdentity: Pair<KeyImportRecipient, KeyImportIdentity>? = null

    override suspend fun storages(): List<KeyStorageOption> {
        KeystoreCapabilities.start(app)
        val state = KeystoreCapabilities.measurements.first { it !is MeasurementState.Measuring }
        val measured = (state as? MeasurementState.Ready)?.profile?.backends
        val hardware =
            KeystoreBackend.entries.reversed().map { backend ->
                val measurement = measured?.firstOrNull { it.backend == backend }
                val desede = measurement?.algorithms?.get(KeyAlgorithm.DESEDE)
                val available =
                    if (measured != null) {
                        val support = desede?.rawImport?.support
                        support != null && support != Support.NO
                    } else {
                        backend != KeystoreBackend.STRONGBOX ||
                            app.packageManager.hasSystemFeature(
                                PackageManager.FEATURE_STRONGBOX_KEYSTORE,
                            )
                    }
                val samples = desede?.let { it.encryptionNanos + it.decryptionNanos }.orEmpty()
                KeyStorageOption(
                    backend.storage,
                    available,
                    isPresent = available || measurement != null,
                    operationTime = samples.takeIf { it.isNotEmpty() }?.average()?.nanoseconds,
                )
            }
        return if (hardware.any { it.isAvailable }) hardware
        else hardware + KeyStorageOption(KeyStorage.SOFTWARE, true)
    }

    override suspend fun protect(
        referenceId: String,
        key: SecretBytes,
        use: DesKeyUse,
        storage: KeyStorage,
    ): ProtectedKeyReference =
        withContext(Dispatchers.IO) {
            val backend = KeystoreBackend.entries.firstOrNull { it.storage == storage }
            val bytes = key.copyToByteArray()
            val alias = ALIAS_PREFIX + referenceId
            val keyStore = keyStore()
            try {
                require(bytes.size == DESEDE_KEY_SIZE) {
                    "DESede key must be exactly $DESEDE_KEY_SIZE bytes"
                }
                val source = SecretKeySpec(bytes, "DESede")
                keyStore.setEntry(alias, KeyStore.SecretKeyEntry(source), protection(use, backend))
                val imported = checkNotNull(keyStore.getKey(alias, null) as? SecretKey)
                verify(imported, backend, use, source)
                ProtectedKeyReference(referenceId, storage)
            } catch (error: Exception) {
                runCatching { keyStore.deleteEntry(alias) }
                throw error
            } finally {
                bytes.fill(0)
            }
        }

    override suspend fun recipientStorage(recipient: KeyImportRecipient): KeyStorage =
        identityFor(recipient).backend.storage

    override suspend fun importWrapped(
        referenceId: String,
        recipient: KeyImportRecipient,
        wrappedKey: ByteArray,
        use: DesKeyUse,
    ): ProtectedKeyReference {
        val identity = identityFor(recipient)
        return withContext(Dispatchers.IO) {
            val alias = ALIAS_PREFIX + referenceId
            val keyStore = keyStore()
            try {
                importWrappedEntry(keyStore, alias, wrappedKey, identity.alias)
                val imported = checkNotNull(keyStore.getKey(alias, null) as? SecretKey)
                verifyWrapped(imported, identity.backend, use)
                ProtectedKeyReference(referenceId, identity.backend.storage)
            } catch (error: Exception) {
                runCatching { keyStore.deleteEntry(alias) }
                throw error
            }
        }
    }

    override suspend fun decryptWrapped(
        recipient: KeyImportRecipient,
        wrappedKey: ByteArray,
        iv: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val identity = identityFor(recipient)
        return withContext(Dispatchers.IO) {
            val alias = CONTENT_KEY_ALIAS_PREFIX + UUID.randomUUID()
            val keyStore = keyStore()
            try {
                importWrappedEntry(keyStore, alias, wrappedKey, identity.alias)
                val key = checkNotNull(keyStore.getKey(alias, null) as? SecretKey)
                val info =
                    SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEY_STORE)
                        .getKeySpec(key, KeyInfo::class.java) as KeyInfo
                require(
                    key.algorithm == KeyProperties.KEY_ALGORITHM_AES &&
                        info.keySize == 256 &&
                        info.purposes and KeyProperties.PURPOSE_DECRYPT != 0 &&
                        KeyProperties.BLOCK_MODE_GCM in info.blockModes,
                ) {
                    "Content key must be an AES-256-GCM key that can decrypt"
                }
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                    doFinal(ciphertext)
                }
            } finally {
                runCatching { keyStore.deleteEntry(alias) }
            }
        }
    }

    private suspend fun identityFor(recipient: KeyImportRecipient): KeyImportIdentity {
        resolvedIdentity?.let { (resolved, identity) ->
            if (resolved === recipient) return identity
        }
        require(recipient.method == KeyImportIdentity.ANDROID_KEYMINT_RSA_OAEP_AES_GCM) {
            "Key file uses unsupported wrapping method '${recipient.method}'"
        }
        val salt = recipient.salt
        val hash = recipient.hash
        for (backend in identities.backends()) {
            for (identity in identities.identities(backend)) {
                val candidate =
                    MessageDigest.getInstance("SHA-256").run {
                        update(salt)
                        digest(identity.certificates.first().publicKey.encoded)
                    }
                if (MessageDigest.isEqual(candidate, hash)) {
                    resolvedIdentity = recipient to identity
                    return identity
                }
            }
        }
        throw IllegalArgumentException(
            "Key file is addressed to an import identity this device doesn't have",
        )
    }

    private fun importWrappedEntry(
        keyStore: KeyStore,
        alias: String,
        wrappedKey: ByteArray,
        wrappingAlias: String,
    ) {
        val parameters =
            KeyGenParameterSpec.Builder(wrappingAlias, KeyProperties.PURPOSE_WRAP_KEY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        keyStore.setEntry(
            alias,
            WrappedKeyEntry(wrappedKey, wrappingAlias, "RSA/ECB/OAEPPadding", parameters),
            null,
        )
    }

    private fun verifyWrapped(key: SecretKey, backend: KeystoreBackend, use: DesKeyUse) {
        check(key.encoded == null) { "Imported key is extractable" }
        val info =
            SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEY_STORE)
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
        require(
            key.algorithm == KeyAlgorithm.DESEDE.algorithm &&
                info.securityLevel == backend.level &&
                info.purposes and use.purpose != 0 &&
                KeyProperties.BLOCK_MODE_ECB in info.blockModes &&
                KeyProperties.ENCRYPTION_PADDING_NONE in info.encryptionPaddings &&
                !info.isUserAuthenticationRequired,
        ) {
            "Wrapped key must be a DESede ECB key without padding that can " +
                "${use.name.lowercase()} in ${backend.label}"
        }
    }

    override suspend fun encrypt(referenceId: String, block: ByteArray): ByteArray =
        transform(referenceId, Cipher.ENCRYPT_MODE, block)

    override suspend fun decrypt(referenceId: String, block: ByteArray): ByteArray =
        transform(referenceId, Cipher.DECRYPT_MODE, block)

    private suspend fun transform(referenceId: String, mode: Int, block: ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            require(block.size == DES_BLOCK_SIZE) {
                "DESede block must be exactly $DES_BLOCK_SIZE bytes"
            }
            val key =
                operationKeyStore.getKey(ALIAS_PREFIX + referenceId, null) as? SecretKey
                    ?: throw IllegalStateException("Protected key is missing")
            Cipher.getInstance("DESede/ECB/NoPadding").run {
                init(mode, key)
                doFinal(block)
            }
        }

    override suspend fun delete(referenceId: String) =
        withContext(Dispatchers.IO) { keyStore().deleteEntry(ALIAS_PREFIX + referenceId) }

    override suspend fun deleteAll(except: Set<String>) =
        withContext(Dispatchers.IO) {
            val keyStore = keyStore()
            keyStore
                .aliases()
                .toList()
                .filter {
                    it.startsWith(CONTENT_KEY_ALIAS_PREFIX) ||
                        it.startsWith(ALIAS_PREFIX) && it.removePrefix(ALIAS_PREFIX) !in except
                }
                .forEach(keyStore::deleteEntry)
        }

    private fun verify(key: SecretKey, backend: KeystoreBackend?, use: DesKeyUse, raw: SecretKey) {
        check(key.encoded == null) { "Imported key is extractable" }
        val info =
            try {
                SecretKeyFactory.getInstance(key.algorithm, ANDROID_KEY_STORE)
                    .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            } catch (_: ProviderException) {
                null
            }
        if (info != null) {
            check(
                info.securityLevel == (backend?.level ?: KeyProperties.SECURITY_LEVEL_SOFTWARE) &&
                    info.purposes == use.purpose &&
                    !info.isUserAuthenticationRequired,
            ) {
                "Key was not imported into ${backend?.label ?: "the software keystore"} as expected"
            }
        }
        val mode = if (use == DesKeyUse.ENCRYPT) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE
        check(transform(key, mode).contentEquals(transform(raw, mode))) {
            "Imported key did not match its raw value"
        }
        val opposite = if (mode == Cipher.ENCRYPT_MODE) Cipher.DECRYPT_MODE else Cipher.ENCRYPT_MODE
        check(runCatching { transform(key, opposite) }.isFailure) {
            "Imported key permits more than ${use.name.lowercase()}"
        }
    }

    private fun transform(key: SecretKey, mode: Int): ByteArray =
        Cipher.getInstance("DESede/ECB/NoPadding").run {
            init(mode, key)
            doFinal(ByteArray(DES_BLOCK_SIZE))
        }

    private fun protection(use: DesKeyUse, backend: KeystoreBackend?): KeyProtection =
        KeyProtection.Builder(use.purpose)
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(false)
            .setUserAuthenticationRequired(false)
            .setIsStrongBoxBacked(backend == KeystoreBackend.STRONGBOX)
            .build()

    private val operationKeyStore by lazy { keyStore() }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private val DesKeyUse.purpose
        get() =
            when (this) {
                DesKeyUse.ENCRYPT -> KeyProperties.PURPOSE_ENCRYPT
                DesKeyUse.DECRYPT -> KeyProperties.PURPOSE_DECRYPT
            }

    private val KeystoreBackend.storage
        get() =
            when (this) {
                KeystoreBackend.TEE -> KeyStorage.TRUSTED_EXECUTION_ENVIRONMENT
                KeystoreBackend.STRONGBOX -> KeyStorage.DEDICATED_SECURE_MODULE
            }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val ALIAS_PREFIX = "felica-tool-key-des-"
        const val CONTENT_KEY_ALIAS_PREFIX = "felica-tool-key-import-content-"
        const val GCM_TAG_BITS = 128
    }
}
