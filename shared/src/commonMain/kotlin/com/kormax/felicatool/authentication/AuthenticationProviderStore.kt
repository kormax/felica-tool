package com.kormax.felicatool.authentication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface SecureAuthenticationProviderStorage {
    suspend fun read(): ByteArray?

    suspend fun write(data: ByteArray)

    suspend fun delete()
}

internal class AuthenticationProviderStore(
    private val storage: SecureAuthenticationProviderStorage,
    private val keyStore: ProtectedDesKeyStore,
) {
    private val mutex = Mutex()
    private var cached: AuthenticationProviderConfigurations? = null
    private var isKeyStoreSwept = false

    suspend fun <T> read(block: (AuthenticationProviderConfigurations) -> T): T = mutex.withLock {
        withContext(Dispatchers.Default) { block(load()) }
    }

    suspend fun <T> update(block: suspend (AuthenticationProviderConfigurations) -> T): T =
        mutex.withLock {
            withContext(NonCancellable + Dispatchers.Default) {
                val configurations = load().snapshot()
                val previous = configurations.protectedKeys
                val result =
                    try {
                        block(configurations).also { persist(configurations) }
                    } catch (error: Throwable) {
                        (configurations.protectedKeys - previous).forEach {
                            runCatching { keyStore.delete(it.id) }
                        }
                        throw error
                    }
                cached = configurations
                (previous - configurations.protectedKeys).forEach {
                    runCatching { keyStore.delete(it.id) }
                }
                result
            }
        }

    suspend fun deleteUnreadable(): Unit = mutex.withLock {
        withContext(NonCancellable + Dispatchers.Default) {
            val configurations = load().snapshot()
            configurations.unreadableProviders.clear()
            persist(configurations)
            cached = configurations
            runCatching {
                keyStore.deleteAll(
                    except = configurations.protectedKeys.mapTo(mutableSetOf()) { it.id },
                )
            }
        }
    }

    suspend fun deleteAll(): Unit = mutex.withLock {
        withContext(NonCancellable + Dispatchers.Default) {
            storage.delete()
            cached = AuthenticationProviderConfigurations()
            runCatching { keyStore.deleteAll() }
        }
    }

    private suspend fun load(): AuthenticationProviderConfigurations {
        cached?.let {
            return it
        }
        val plaintext = storage.read()
        val configurations =
            if (plaintext == null) AuthenticationProviderConfigurations()
            else
                try {
                    AuthenticationProviderConfigurationCodec.decode(plaintext)
                } finally {
                    plaintext.fill(0)
                }
        if (!isKeyStoreSwept) {
            isKeyStoreSwept = true
            val referenced =
                configurations.protectedKeys.map { it.id } +
                    configurations.unreadableProviders.flatMap(
                        AuthenticationProviderConfigurationCodec::strings,
                    )
            runCatching { keyStore.deleteAll(except = referenced.toSet()) }
        }
        cached = configurations
        return configurations
    }

    private suspend fun persist(configurations: AuthenticationProviderConfigurations) {
        val plaintext = AuthenticationProviderConfigurationCodec.encode(configurations)
        try {
            storage.write(plaintext)
        } finally {
            plaintext.fill(0)
        }
    }
}
