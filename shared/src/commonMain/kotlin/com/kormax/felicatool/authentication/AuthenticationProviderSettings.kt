package com.kormax.felicatool.authentication

import kotlinx.serialization.json.JsonObject

enum class AuthenticationProviderType(val displayName: String) {
    DES_AUTHENTICATION("Local DES Authentication Key Provider") {
        override fun create(id: String, displayName: String) =
            DesAuthenticationKeyProviderConfiguration(id, displayName, isEnabled = true)

        override fun decode(id: String, displayName: String, isEnabled: Boolean, root: JsonObject) =
            DesAuthenticationKeyProviderConfiguration.decode(id, displayName, isEnabled, root)
    };

    internal abstract fun create(
        id: String,
        displayName: String,
    ): AuthenticationProviderConfiguration

    internal abstract fun decode(
        id: String,
        displayName: String,
        isEnabled: Boolean,
        root: JsonObject,
    ): AuthenticationProviderConfiguration
}

interface AuthenticationProviderSettings {
    suspend fun summaries(): List<AuthenticationProviderSummary>

    suspend fun summary(providerId: String): AuthenticationProviderSummary?

    suspend fun createProvider(type: AuthenticationProviderType, displayName: String): String

    suspend fun renameProvider(providerId: String, displayName: String)

    suspend fun setProviderEnabled(providerId: String, enabled: Boolean)

    suspend fun reorderProviders(providerIds: List<String>)

    suspend fun deleteProvider(providerId: String)

    suspend fun unreadableProviderCount(): Int

    suspend fun deleteUnreadableProviders()

    suspend fun deleteAllProviders()
}

interface LocalAuthenticationCredentials {
    suspend fun credentials(providerId: String): List<AuthenticationCredentialSummary>

    suspend fun keyStorages(providerId: String): List<KeyStorageOption>

    suspend fun prepareCredentialImport(
        providerId: String,
        data: ByteArray,
    ): PreparedCredentialImport

    suspend fun importCredentials(
        providerId: String,
        data: ByteArray,
        storage: KeyStorage,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): CredentialImportResult

    suspend fun deleteCredentials(providerId: String, credentialIds: Set<String>)
}
