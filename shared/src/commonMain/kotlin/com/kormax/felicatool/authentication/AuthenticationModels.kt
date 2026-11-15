package com.kormax.felicatool.authentication

import com.kormax.felicatool.felica.Authentication1Des
import com.kormax.felicatool.felica.Authentication2Des
import com.kormax.felicatool.felica.EncryptedCommand
import com.kormax.felicatool.felica.EncryptedResponse
import com.kormax.felicatool.felica.KeyVersion

enum class AuthenticationScheme {
    DES,
}

enum class AuthenticationNodeKind {
    SYSTEM,
    AREA,
    AUTHENTICATION_REQUIRED_SERVICE,
    AUTHENTICATION_NOT_REQUIRED_SERVICE,
}

data class NodeProfile(
    val code: String,
    val kind: AuthenticationNodeKind,
    val keyVersion: KeyVersion?,
    val areaPath: List<String>,
) {
    val contributesUserKey: Boolean
        get() = kind != AuthenticationNodeKind.AUTHENTICATION_NOT_REQUIRED_SERVICE
}

data class SystemProfile(
    val systemCode: String,
    val idm: String,
    val rootAreaCode: String,
    val nodes: List<NodeProfile>,
    val behavior: DesAuthenticationBehavior,
) {
    fun node(code: String): NodeProfile? = nodes.firstOrNull { it.code == code }
}

sealed interface AuthenticationGoal {
    data object GetSystemAuthenticationInfo : AuthenticationGoal
}

data class AuthenticationQuery(
    val profile: SystemProfile,
    val goal: AuthenticationGoal,
    val exactScope: DesAuthenticationScope? = null,
)

data class AuthenticationOffer(
    val id: String,
    val providerId: String,
    val scheme: AuthenticationScheme,
    val scope: DesAuthenticationScope,
    val coveredNodes: Set<String>,
    val extraNodes: Set<String>,
    val hasExactIdmCredential: Boolean,
) {
    val transmittedCodeCount: Int
        get() = scope.areaCodes.size + scope.nodeCodes.size
}

class SystemAuthenticationInfo(
    val issueId: ByteArray,
    val issueParameter: ByteArray,
)

sealed interface AuthenticationOutcome {
    class Success(
        val information: SystemAuthenticationInfo,
        val channel: SecureChannel,
    ) : AuthenticationOutcome

    data object CredentialMiss : AuthenticationOutcome

    data object NoResponse : AuthenticationOutcome

    data class HardFailure(val reason: String) : AuthenticationOutcome
}

interface OfferBoundAuthenticationTransport {
    val systemCode: String
    val idm: String
    val scope: DesAuthenticationScope

    suspend fun authentication1(
        challenge1A: ByteArray,
        attempts: Int = 3,
    ): Authentication1Des.Response?

    suspend fun authentication2(
        challenge2B: ByteArray,
        attempts: Int = 3,
    ): Authentication2Des.Response.Encrypted?

    suspend fun <T : EncryptedResponse> transceiveSecure(
        command: EncryptedCommand<T>,
        attempts: Int = 3,
    ): T?

    suspend fun recoverMode0()
}

data class AuthenticationProviderDescriptor(
    val id: String,
    val displayName: String,
)

interface AuthenticationProvider {
    val descriptor: AuthenticationProviderDescriptor

    suspend fun openSession(profiles: List<SystemProfile>): AuthenticationProviderSession?
}

interface AuthenticationProviderSession {
    val descriptor: AuthenticationProviderDescriptor

    suspend fun offers(query: AuthenticationQuery): List<AuthenticationOffer>

    suspend fun authenticate(
        offer: AuthenticationOffer,
        transport: OfferBoundAuthenticationTransport,
    ): AuthenticationOutcome

    suspend fun close()
}

interface AuthenticationProviderRegistry {
    suspend fun providers(): List<AuthenticationProvider>
}

internal fun DesAuthenticationScope.signature(): String = buildString {
    append(systemCode)
    append(':')
    areaCodes.joinTo(this, ",")
    append('|')
    nodeCodes.joinTo(this, ",")
}
