package com.kormax.felicatool.authentication

import android.content.Context
import android.content.pm.PackageManager
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import com.kormax.felicatool.device.KeyAlgorithm
import com.kormax.felicatool.device.KeystoreBackend
import com.kormax.felicatool.device.KeystoreCapabilities
import com.kormax.felicatool.device.MeasurementState
import com.kormax.felicatool.device.Support
import com.kormax.felicatool.security.SecretBytes
import java.security.KeyStore
import java.security.ProviderException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AndroidProtectedDesKeyStore(context: Context) : ProtectedDesKeyStore {
    private val app = context.applicationContext

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
                require(bytes.size == 24) { "DESede key must be exactly 24 bytes" }
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

    override suspend fun delete(referenceId: String) =
        withContext(Dispatchers.IO) { keyStore().deleteEntry(ALIAS_PREFIX + referenceId) }

    override suspend fun deleteAll(except: Set<String>) =
        withContext(Dispatchers.IO) {
            val keyStore = keyStore()
            keyStore
                .aliases()
                .toList()
                .filter { it.startsWith(ALIAS_PREFIX) && it.removePrefix(ALIAS_PREFIX) !in except }
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
            doFinal(ByteArray(8))
        }

    private fun protection(use: DesKeyUse, backend: KeystoreBackend?): KeyProtection =
        KeyProtection.Builder(use.purpose)
            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(false)
            .setUserAuthenticationRequired(false)
            .setIsStrongBoxBacked(backend == KeystoreBackend.STRONGBOX)
            .build()

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
        const val ALIAS_PREFIX = "felica-tool-des-authentication-key-"
    }
}
