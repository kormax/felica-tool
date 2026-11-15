package com.kormax.felicatool.authentication

import com.kormax.felicatool.logging.ScanLog
import com.kormax.felicatool.security.DesCryptography
import com.kormax.felicatool.security.SecureRandomSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "FeliCaAuth"

class AuthenticationProviders(
    storage: SecureAuthenticationProviderStorage,
    keyStore: ProtectedDesKeyStore,
    private val newId: () -> String,
    cryptography: DesCryptography,
    randomSource: SecureRandomSource,
) : AuthenticationProviderSettings, LocalAuthenticationCredentials, AuthenticationProviderRegistry {
    private val store = AuthenticationProviderStore(storage, keyStore)
    private val context = LocalCredentialContext(keyStore, newId)
    private val providerContext =
        AuthenticationProviderContext(keyStore, cryptography, randomSource)

    override suspend fun summaries() = store.read { it.providers.map { it.summary() } }

    override suspend fun summary(providerId: String) = store.read {
        it.providers.firstOrNull { it.id == providerId }?.summary()
    }

    override suspend fun createProvider(type: AuthenticationProviderType, displayName: String) =
        displayName.normalizedName().let { name ->
            store.update { configurations ->
                newId().also { configurations.add(type.create(it, name)) }
            }
        }

    override suspend fun renameProvider(providerId: String, displayName: String) =
        displayName.normalizedName().let { name ->
            store.update { it.require(providerId).displayName = name }
        }

    override suspend fun setProviderEnabled(providerId: String, enabled: Boolean) = store.update {
        it.require(providerId).isEnabled = enabled
    }

    override suspend fun reorderProviders(providerIds: List<String>) = store.update {
        it.reorder(providerIds)
    }

    override suspend fun deleteProvider(providerId: String) = store.update { it.remove(providerId) }

    override suspend fun unreadableProviderCount() = store.read { it.unreadableProviders.size }

    override suspend fun deleteUnreadableProviders() = store.deleteUnreadable()

    override suspend fun deleteAllProviders() = store.deleteAll()

    override suspend fun credentials(providerId: String) = store.read {
        it.requireLocal(providerId).credentialSummaries()
    }

    override suspend fun keyStorages(providerId: String) =
        store.read { it.requireLocal(providerId) }.keyStorages(context)

    override suspend fun validateCredentials(providerId: String, data: ByteArray) {
        val provider = store.read { it.requireLocal(providerId) }
        withContext(Dispatchers.Default) { provider.validateCredentials(data) }
    }

    override suspend fun importCredentials(
        providerId: String,
        data: ByteArray,
        storage: KeyStorage,
        onProgress: (processed: Int, total: Int) -> Unit,
    ) = store.update {
        it.requireLocal(providerId).importCredentials(data, storage, context, onProgress)
    }

    override suspend fun providers(): List<AuthenticationProvider> =
        store
            .read { configurations -> configurations.providers.filter { it.isEnabled } }
            .mapNotNull { configuration ->
                try {
                    configuration.authenticationProvider(providerContext)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    ScanLog.e(
                        TAG,
                        "${configuration.displayName}: provider could not be prepared",
                        error,
                    )
                    null
                }
            }

    override suspend fun deleteCredentials(providerId: String, credentialIds: Set<String>) =
        store.update {
            it.requireLocal(providerId).deleteCredentials(credentialIds)
        }

    private fun AuthenticationProviderConfigurations.requireLocal(providerId: String) =
        require(providerId) as? LocalAuthenticationProviderConfiguration
            ?: throw IllegalArgumentException("Authentication provider has no local credentials")

    private fun String.normalizedName() =
        trim().also { require(it.isNotEmpty()) { "Authentication provider name cannot be blank" } }
}
