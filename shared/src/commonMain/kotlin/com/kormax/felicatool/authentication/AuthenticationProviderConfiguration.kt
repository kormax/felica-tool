package com.kormax.felicatool.authentication

import com.kormax.felicatool.security.DesCryptography
import com.kormax.felicatool.security.SecureRandomSource
import kotlinx.serialization.json.JsonElement

data class AuthenticationProviderSummary(
    val id: String,
    val displayName: String,
    val type: AuthenticationProviderType,
    val isEnabled: Boolean,
)

data class CredentialImportResult(
    val imported: Int,
    val moved: Int,
    val alreadyStored: Int,
    val repeated: Int,
)

sealed interface AuthenticationCredentialSummary {
    val id: String
}

internal sealed class AuthenticationProviderConfiguration(
    val id: String,
    var displayName: String,
    var isEnabled: Boolean,
) {
    init {
        require(id.isNotBlank()) { "Authentication provider ID cannot be blank" }
        require(displayName.isNotBlank()) { "Authentication provider name cannot be blank" }
    }

    abstract val type: AuthenticationProviderType

    abstract fun snapshot(): AuthenticationProviderConfiguration

    abstract fun encodeFields(): Map<String, JsonElement>

    abstract fun authenticationProvider(
        context: AuthenticationProviderContext,
    ): AuthenticationProvider

    fun summary(): AuthenticationProviderSummary =
        AuthenticationProviderSummary(
            id = id,
            displayName = displayName,
            type = type,
            isEnabled = isEnabled,
        )
}

internal sealed class LocalAuthenticationProviderConfiguration(
    id: String,
    displayName: String,
    isEnabled: Boolean,
) : AuthenticationProviderConfiguration(id, displayName, isEnabled) {
    abstract val protectedKeys: List<ProtectedKeyReference>

    abstract fun credentialSummaries(): List<AuthenticationCredentialSummary>

    abstract suspend fun keyStorages(context: LocalCredentialContext): List<KeyStorageOption>

    abstract fun validateCredentials(data: ByteArray)

    abstract suspend fun importCredentials(
        data: ByteArray,
        storage: KeyStorage,
        context: LocalCredentialContext,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): CredentialImportResult

    abstract fun deleteCredentials(ids: Set<String>)
}

internal class LocalCredentialContext(val keyStore: ProtectedDesKeyStore, val newId: () -> String)

internal class AuthenticationProviderContext(
    val keyStore: ProtectedDesKeyStore,
    val cryptography: DesCryptography,
    val randomSource: SecureRandomSource,
)

internal class AuthenticationProviderConfigurations(
    private val mutableProviders: MutableList<AuthenticationProviderConfiguration> =
        mutableListOf(),
    val unreadableProviders: MutableList<String> = mutableListOf(),
) {
    val providers: List<AuthenticationProviderConfiguration>
        get() = mutableProviders

    val protectedKeys: Set<ProtectedKeyReference>
        get() =
            mutableProviders
                .filterIsInstance<LocalAuthenticationProviderConfiguration>()
                .flatMap { it.protectedKeys }
                .toSet()

    fun snapshot() =
        AuthenticationProviderConfigurations(
            mutableProviders.mapTo(mutableListOf()) { it.snapshot() },
            unreadableProviders.toMutableList(),
        )

    fun require(id: String): AuthenticationProviderConfiguration =
        mutableProviders.firstOrNull { it.id == id }
            ?: throw IllegalArgumentException("Authentication provider does not exist")

    fun add(provider: AuthenticationProviderConfiguration) {
        require(mutableProviders.none { it.id == provider.id }) {
            "Authentication provider already exists"
        }
        mutableProviders += provider
    }

    fun remove(id: String) {
        mutableProviders.remove(require(id))
    }

    fun reorder(ids: List<String>) {
        require(ids.size == ids.distinct().size) {
            "Authentication provider order contains duplicates"
        }
        val providersById = mutableProviders.associateBy { it.id }
        require(ids.toSet() == providersById.keys) {
            "Authentication provider order must contain every provider"
        }
        mutableProviders.clear()
        ids.mapTo(mutableProviders, providersById::getValue)
    }
}
