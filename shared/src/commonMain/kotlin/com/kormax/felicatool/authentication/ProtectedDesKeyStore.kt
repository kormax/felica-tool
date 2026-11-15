package com.kormax.felicatool.authentication

import com.kormax.felicatool.security.SecretBytes
import kotlin.time.Duration

enum class KeyStorage {
    SOFTWARE,
    TRUSTED_EXECUTION_ENVIRONMENT,
    DEDICATED_SECURE_MODULE,
}

enum class DesKeyUse {
    ENCRYPT,
    DECRYPT,
}

data class KeyStorageOption(
    val storage: KeyStorage,
    val isAvailable: Boolean,
    val isPresent: Boolean = isAvailable,
    val operationTime: Duration? = null,
)

data class ProtectedKeyReference(val id: String, val storage: KeyStorage)

interface ProtectedDesKeyStore {
    suspend fun storages(): List<KeyStorageOption>

    suspend fun protect(
        referenceId: String,
        key: SecretBytes,
        use: DesKeyUse,
        storage: KeyStorage,
    ): ProtectedKeyReference

    suspend fun encrypt(referenceId: String, block: ByteArray): ByteArray

    suspend fun decrypt(referenceId: String, block: ByteArray): ByteArray

    suspend fun delete(referenceId: String)

    suspend fun deleteAll(except: Set<String> = emptySet())
}
