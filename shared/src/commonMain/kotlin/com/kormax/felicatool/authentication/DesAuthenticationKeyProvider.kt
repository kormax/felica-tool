package com.kormax.felicatool.authentication

import com.kormax.felicatool.logging.ScanLog
import com.kormax.felicatool.security.DesCryptography
import com.kormax.felicatool.security.SecretBytes
import com.kormax.felicatool.security.SecureRandomSource

private const val TAG = "FeliCaAuth"

internal class DesAuthenticationKeyProvider(
    override val descriptor: AuthenticationProviderDescriptor,
    private val keys: List<DesAuthenticationKeyReference>,
    private val keyStore: ProtectedDesKeyStore,
    cryptography: DesCryptography,
    randomSource: SecureRandomSource,
) : BaseDesAuthenticationProvider(descriptor, cryptography, randomSource), AuthenticationProvider {
    override suspend fun openSession(
        profiles: List<SystemProfile>,
    ): AuthenticationProviderSession? {
        val relevantKeys =
            keys.withIndex().filter { (index, key) ->
                val rejections =
                    profiles
                        .filter { it.systemCode == key.scope.systemCode }
                        .map { key.profileRejection(it) }
                if (rejections.isNotEmpty() && rejections.all { it != null }) {
                    ScanLog.w(
                        TAG,
                        "${descriptor.displayName}: skipped key ${index + 1}: ${rejections.first()}",
                    )
                }
                rejections.any { it == null }
            }
        if (relevantKeys.isEmpty()) {
            ScanLog.i(TAG, "${descriptor.displayName}: no matching keys")
            return null
        }
        ScanLog.i(
            TAG,
            "${descriptor.displayName}: prepared ${relevantKeys.size} matching key(s) " +
                "for ${profiles.size} discovered system(s)",
        )
        return Session(relevantKeys, profiles)
    }

    private inner class Session(
        private val keys: List<IndexedValue<DesAuthenticationKeyReference>>,
        private val profiles: List<SystemProfile>,
    ) : AuthenticationProviderSession {
        override val descriptor: AuthenticationProviderDescriptor =
            this@DesAuthenticationKeyProvider.descriptor

        private val probedProfiles = mutableMapOf<Pair<String, String>, SystemProfile>()

        override suspend fun offers(query: AuthenticationQuery): List<AuthenticationOffer> {
            if (!supports(query.profile) || !query.profile.behavior.supported) return emptyList()
            probedProfiles[query.profile.systemCode to query.profile.idm] = query.profile
            val offers = matchingOffers(query, keys)
            ScanLog.i(TAG, "${descriptor.displayName}: ${offers.size} eligible offer(s)")
            return offers
        }

        override suspend fun authenticate(
            offer: AuthenticationOffer,
            transport: OfferBoundAuthenticationTransport,
        ): AuthenticationOutcome {
            val profile = profileFor(offer.scope) ?: return AuthenticationOutcome.CredentialMiss
            return this@DesAuthenticationKeyProvider.authenticate(offer, transport) {
                resolveContexts(it, profile, keys)
            }
        }

        override suspend fun close() = Unit

        private fun supports(profile: SystemProfile): Boolean = profiles.any {
            it.systemCode == profile.systemCode && it.idm == profile.idm
        }

        private fun profileFor(scope: DesAuthenticationScope): SystemProfile? =
            probedProfiles[scope.systemCode to scope.idm]
                ?: profiles.firstOrNull { it.systemCode == scope.systemCode && it.idm == scope.idm }
    }

    private fun resolveContexts(
        offer: AuthenticationOffer,
        profile: SystemProfile,
        keys: List<IndexedValue<DesAuthenticationKeyReference>>,
    ): List<DesAuthenticationContext> {
        val records = keys.filter {
            it.value.scope == offer.scope && it.value.profileRejection(profile) == null
        }
        ScanLog.i(
            TAG,
            "${descriptor.displayName}: resolving ${records.size} key alternative(s)",
        )
        return records.map { KeystoreDesAuthenticationContext(keyStore, it.value) }
    }

    private fun matchingOffers(
        query: AuthenticationQuery,
        keys: List<IndexedValue<DesAuthenticationKeyReference>>,
    ): List<AuthenticationOffer> {
        val profile = query.profile
        val diagnose = query.exactScope == null
        val requestedNodes = (query.goal as? AuthenticationGoal.ReadWithEncryption)?.requestedNodes
        return keys
            .asSequence()
            .filter { it.value.scope.systemCode == profile.systemCode }
            .filter { (index, key) ->
                val rejection = key.profileRejection(profile)
                if (diagnose && rejection != null) {
                    ScanLog.w(
                        TAG,
                        "${descriptor.displayName}: rejected key ${index + 1}: $rejection",
                    )
                }
                rejection == null
            }
            .groupBy { it.value.scope }
            .mapNotNull { (scope, records) ->
                if (query.exactScope != null && query.exactScope != scope) return@mapNotNull null
                val behaviorRejection = profile.behavior.rejectionReason(scope, profile)
                if (behaviorRejection != null) {
                    if (diagnose) {
                        ScanLog.w(
                            TAG,
                            "${descriptor.displayName}: rejected scope ${scope.signature()}: " +
                                behaviorRejection,
                        )
                    }
                    return@mapNotNull null
                }
                val requested = requestedNodes ?: scope.nodeCodes.toSet()
                val covered = scope.nodeCodes.toSet().intersect(requested)
                if (requestedNodes != null && covered.isEmpty()) return@mapNotNull null
                AuthenticationOffer(
                    id = "${descriptor.id}:${scope.signature()}",
                    providerId = descriptor.id,
                    scheme = AuthenticationScheme.DES,
                    scope = scope,
                    coveredNodes = covered,
                    extraNodes = scope.nodeCodes.toSet() - requested,
                    hasExactIdmCredential = records.any { it.value.scope.idm == profile.idm },
                )
            }
            .sortedWith(
                compareByDescending<AuthenticationOffer> { it.hasExactIdmCredential }
                    .thenBy { it.transmittedCodeCount },
            )
    }

    private fun DesAuthenticationKeyReference.profileRejection(
        profile: SystemProfile,
    ): String? {
        if (scope.idm != profile.idm) return "IDm ${scope.idm} does not match card ${profile.idm}"
        val missingAreas = scope.areaCodes.distinct().filter { profile.node(it) == null }
        if (missingAreas.isNotEmpty()) return "area codes were not discovered: $missingAreas"
        val missingNodes = scope.nodeCodes.distinct().filter { profile.node(it) == null }
        if (missingNodes.isNotEmpty()) return "node codes were not discovered: $missingNodes"

        val requiredCodes = buildSet {
            add(SYSTEM_NODE_CODE)
            addAll(scope.areaCodes)
            scope.nodeCodes.forEach { code ->
                if (profile.node(code)?.contributesUserKey == true) add(code)
            }
        }
        val missingVersions = requiredCodes - versions.keys
        if (missingVersions.isNotEmpty()) {
            return "versions are missing required codes: $missingVersions"
        }
        val mismatches = requiredCodes.mapNotNull { code ->
            val expectedVersion = versions.getValue(code)
            val actual = profile.node(code)?.keyVersion
            if (actual == expectedVersion) null
            else "$code expected ${expectedVersion.toInt()}, card ${actual?.toInt() ?: "missing"}"
        }
        if (mismatches.isNotEmpty()) return "key-version mismatch: ${mismatches.joinToString()}"
        return null
    }

    private class KeystoreDesAuthenticationContext(
        private val keyStore: ProtectedDesKeyStore,
        private val key: DesAuthenticationKeyReference,
    ) : DesAuthenticationContext {
        override suspend fun encryptChallenge1A(random1: SecretBytes): ByteArray = encrypt(random1)

        override suspend fun verifyChallenge1B(
            random1: SecretBytes,
            challenge1B: ByteArray,
        ): Boolean {
            val bytes = keyStore.decrypt(key.lbl.id, challenge1B)
            val decrypted = SecretBytes.fromByteArray(bytes)
            bytes.fill(0)
            return try {
                decrypted.constantTimeEquals(random1)
            } finally {
                decrypted.close()
            }
        }

        override suspend fun decryptChallenge2A(challenge2A: ByteArray): SecretBytes {
            val decrypted = keyStore.decrypt(key.lbl.id, challenge2A)
            return try {
                SecretBytes.fromByteArray(decrypted)
            } finally {
                decrypted.fill(0)
            }
        }

        override suspend fun encryptChallenge2B(random2: SecretBytes): ByteArray = encrypt(random2)

        override fun close() = Unit

        private suspend fun encrypt(block: SecretBytes): ByteArray {
            val bytes = block.copyToByteArray()
            return try {
                keyStore.encrypt(key.ala.id, bytes)
            } finally {
                bytes.fill(0)
            }
        }
    }

    internal companion object {
        const val SYSTEM_NODE_CODE = "FFFF"
    }
}
