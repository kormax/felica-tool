@file:OptIn(ExperimentalSerializationApi::class)

package com.kormax.felicatool.authentication

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyInfo
import android.util.Log
import com.kormax.felicatool.BuildConfig
import com.kormax.felicatool.device.BackendMeasurement
import com.kormax.felicatool.device.KeyAlgorithm
import com.kormax.felicatool.device.KeystoreBackend
import com.kormax.felicatool.device.KeystoreCapabilities
import com.kormax.felicatool.device.MeasurementState
import com.kormax.felicatool.device.SecureKeyWrapper
import com.kormax.felicatool.device.Support
import com.kormax.felicatool.device.androidApiVersion
import com.kormax.felicatool.device.systemProperty
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence

internal data class KeyImportIdentity(
    val alias: String,
    val backend: KeystoreBackend,
    val id: String,
    val certificates: List<X509Certificate>,
    val context: ByteArray,
    val created: Instant,
) {
    val request: String
        get() =
            encodeKeyImportRequest(
                ANDROID_KEYMINT_RSA_OAEP_AES_GCM,
                certificates.map { it.encoded },
                context,
            )

    companion object {
        const val ANDROID_KEYMINT_RSA_OAEP_AES_GCM = "android-keymint-rsa2048-oaep-sha256-aes256gcm"
    }
}

@Serializable
internal class KeyImportRequest(
    val format: String,
    val version: Int,
    val method: String,
    val certificates: List<ByteArray>,
    val context: ByteArray,
)

@Serializable
internal class KeyImportContext(
    val generated: String,
    val challenge: ByteArray,
    val device: Device,
    val soc: Soc? = null,
    val android: AndroidVersion,
    val keystore: Keystore,
    val app: App,
) {
    @Serializable
    class Device(
        val manufacturer: String,
        val brand: String,
        val model: String,
        val device: String,
        val product: String,
        val hardware: String,
        val platform: String? = null,
        val sku: String? = null,
        @SerialName("odm_sku") val odmSku: String? = null,
        @SerialName("odm_manufacturer") val odmManufacturer: String? = null,
        @SerialName("odm_brand") val odmBrand: String? = null,
        @SerialName("odm_model") val odmModel: String? = null,
        @SerialName("odm_device") val odmDevice: String? = null,
        @SerialName("odm_name") val odmName: String? = null,
    )

    @Serializable class Soc(val manufacturer: String, val model: String)

    @Serializable
    class AndroidVersion(
        val release: String,
        val api: String,
        @SerialName("security_patch") val securityPatch: String,
        @SerialName("initial_sdk") val initialSdk: Int? = null,
        @SerialName("vendor_api") val vendorApi: Int? = null,
        @SerialName("board_first_api") val boardFirstApi: Int? = null,
        @SerialName("board_api") val boardApi: Int? = null,
    )

    @Serializable
    class Keystore(
        val backend: String,
        val implementation: String? = null,
        @SerialName("feature_version") val featureVersion: Int? = null,
        @SerialName("concurrent_occupancy") val concurrentOccupancy: Int? = null,
        val algorithms: Map<String, Algorithm>? = null,
    )

    @Serializable
    class Algorithm(
        val generation: Boolean?,
        @SerialName("raw_import") val rawImport: Boolean?,
        @SerialName("wrapped_import") val wrappedImport: Boolean?,
    )

    @Serializable
    class App(
        val version: String,
        @SerialName("version_code") val versionCode: Int,
        val debug: Boolean,
        val fingerprint: String? = null,
        val installer: String? = null,
    )
}

internal val keyImportRequestCbor = Cbor {
    alwaysUseByteString = true
    useDefiniteLengthEncoding = true
}

@Serializable private class ContextSummary(val generated: String)

private val contextSummaryCbor = Cbor { ignoreUnknownKeys = true }

internal const val KEY_IMPORT_REQUEST_PREFIX = "FTKI1:"

internal fun encodeKeyImportRequest(
    method: String,
    certificates: List<ByteArray>,
    context: ByteArray,
): String {
    val encoded =
        keyImportRequestCbor.encodeToByteArray(
            KeyImportRequest.serializer(),
            KeyImportRequest("felica-tool-key-import-identity", 1, method, certificates, context),
        )
    val compressed =
        ByteArrayOutputStream().use { output ->
            DeflaterOutputStream(output).use { it.write(encoded) }
            output.toByteArray()
        }
    return KEY_IMPORT_REQUEST_PREFIX +
        Base64.getUrlEncoder().withoutPadding().encodeToString(compressed)
}

internal class AndroidKeyImportIdentities(context: Context) {

    private val app = context.applicationContext
    private val contexts = app.getSharedPreferences(CONTEXTS_PREFERENCES, Context.MODE_PRIVATE)

    suspend fun backends(): List<KeystoreBackend> {
        KeystoreCapabilities.start(app)
        val state = KeystoreCapabilities.measurements.first { it !is MeasurementState.Measuring }
        val measured = (state as? MeasurementState.Ready)?.profile?.backends
        return KeystoreBackend.entries.reversed().filter { backend ->
            if (measured != null) {
                val support =
                    measured
                        .firstOrNull { it.backend == backend }
                        ?.algorithms
                        ?.get(KeyAlgorithm.DESEDE)
                        ?.wrappedImport
                        ?.support
                support != null && support != Support.NO
            } else {
                backend != KeystoreBackend.STRONGBOX ||
                    app.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            }
        }
    }

    suspend fun hasHardwareKeystore(): Boolean {
        KeystoreCapabilities.start(app)
        val state = KeystoreCapabilities.measurements.first { it !is MeasurementState.Measuring }
        val measured = (state as? MeasurementState.Ready)?.profile?.backends ?: return true
        return measured.any { backend ->
            val desede = backend.algorithms[KeyAlgorithm.DESEDE]
            desede?.generation == Support.YES || desede?.rawImport?.support == Support.YES
        }
    }

    suspend fun identity(backend: KeystoreBackend): KeyImportIdentity? =
        identities(backend).firstOrNull()

    suspend fun identities(backend: KeystoreBackend): List<KeyImportIdentity> =
        withContext(Dispatchers.IO) {
            val store = keyStore()
            generations(store, backend).mapNotNull {
                try {
                    describe(store, backend, alias(backend, it))
                } catch (error: Exception) {
                    Log.w(
                        "KeyImportIdentities",
                        "Import identity ${alias(backend, it)} is unusable",
                        error,
                    )
                    null
                }
            }
        }

    suspend fun generationForgetsIdentity(backend: KeystoreBackend): Boolean =
        withContext(Dispatchers.IO) { generations(keyStore(), backend).size >= RETAINED_IDENTITIES }

    suspend fun generate(backend: KeystoreBackend, challenge: ByteArray?): KeyImportIdentity {
        require((challenge?.size ?: 0) <= MAX_CHALLENGE_BYTES) {
            "Challenge must be at most $MAX_CHALLENGE_BYTES bytes"
        }
        KeystoreCapabilities.start(app)
        val state = KeystoreCapabilities.measurements.first { it !is MeasurementState.Measuring }
        val measurement =
            (state as? MeasurementState.Ready)?.profile?.backends?.firstOrNull {
                it.backend == backend
            }
        val attestsDeviceIds = measurement?.attestedIds?.isNotEmpty()
        return withContext(Dispatchers.IO) {
            val context =
                keyImportRequestCbor.encodeToByteArray(
                    KeyImportContext.serializer(),
                    context(
                        backend,
                        measurement,
                        challenge
                            ?: ByteArray(DEFAULT_CHALLENGE_BYTES).also(SecureRandom()::nextBytes),
                    ),
                )
            val attestationChallenge = MessageDigest.getInstance("SHA-256").digest(context)
            val store = keyStore()
            val existing = generations(store, backend)
            val alias = alias(backend, (existing.maxOrNull() ?: 0) + 1)
            val identity =
                try {
                    check(
                        contexts
                            .edit()
                            .putString(alias, Base64.getEncoder().encodeToString(context))
                            .commit(),
                    ) {
                        "Import identity context could not be saved"
                    }
                    if (attestsDeviceIds != null) {
                        createWrappingKey(alias, backend, attestsDeviceIds, attestationChallenge)
                    } else {
                        try {
                            createWrappingKey(alias, backend, true, attestationChallenge)
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            store.deleteEntry(alias)
                            createWrappingKey(alias, backend, false, attestationChallenge)
                        }
                    }
                    describe(store, backend, alias)
                } catch (error: Throwable) {
                    runCatching { forget(store, alias) }
                    throw error
                }
            existing.drop(RETAINED_IDENTITIES - 1).forEach { forget(store, alias(backend, it)) }
            identity
        }
    }

    private fun createWrappingKey(
        alias: String,
        backend: KeystoreBackend,
        includeDeviceProperties: Boolean,
        challenge: ByteArray,
    ) {
        val spec =
            SecureKeyWrapper.wrappingKeySpec(alias, backend)
                .setAttestationChallenge(challenge)
                .setDevicePropertiesAttestationIncluded(includeDeviceProperties)
                .build()
        val pair =
            KeyPairGenerator.getInstance("RSA", PROVIDER).run {
                initialize(spec)
                generateKeyPair()
            }
        val info =
            KeyFactory.getInstance("RSA", PROVIDER).getKeySpec(pair.private, KeyInfo::class.java)
        check(info.securityLevel == backend.level) {
            "Import identity was not created in ${backend.label}"
        }
    }

    private fun describe(
        store: KeyStore,
        backend: KeystoreBackend,
        alias: String,
    ): KeyImportIdentity {
        val chain =
            checkNotNull(store.getCertificateChain(alias)) {
                    "Import identity is unavailable"
                }
                .map { it as X509Certificate }
        check(chain.size >= 2) { "Import identity has no attestation chain" }
        chain.zipWithNext().forEach { (child, issuer) -> child.verify(issuer.publicKey) }
        val extension =
            checkNotNull(chain.first().getExtensionValue(ATTESTATION_OID)) {
                "Import identity has no key attestation"
            }
        val record = ASN1Sequence.getInstance(ASN1OctetString.getInstance(extension).octets)
        check(ASN1Enumerated.getInstance(record.getObjectAt(1)).intValueExact() == backend.level) {
            "Import identity is not attested by ${backend.label}"
        }
        val context =
            Base64.getDecoder()
                .decode(
                    checkNotNull(contexts.getString(alias, null)) {
                        "Import identity has no context"
                    },
                )
        check(
            MessageDigest.isEqual(
                ASN1OctetString.getInstance(record.getObjectAt(4)).octets,
                MessageDigest.getInstance("SHA-256").digest(context),
            ),
        ) {
            "Import identity context doesn't match its attestation"
        }
        return KeyImportIdentity(
            alias = alias,
            backend = backend,
            id =
                MessageDigest.getInstance("SHA-256")
                    .digest(chain.first().publicKey.encoded)
                    .toHexString(),
            certificates = chain,
            context = context,
            created =
                Instant.parse(
                    contextSummaryCbor
                        .decodeFromByteArray(ContextSummary.serializer(), context)
                        .generated,
                ),
        )
    }

    suspend fun delete(backend: KeystoreBackend) =
        withContext(Dispatchers.IO) {
            val store = keyStore()
            generations(store, backend).forEach { forget(store, alias(backend, it)) }
        }

    private fun signingFingerprint(): String? =
        app.packageManager
            .getPackageInfo(app.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            .signingInfo
            ?.apkContentsSigners
            ?.firstOrNull()
            ?.let {
                MessageDigest.getInstance("SHA-256")
                    .digest(it.toByteArray())
                    .toHexString(HexFormat.UpperCase)
            }

    private fun keystoreFeatureVersion(backend: KeystoreBackend): Int? {
        val feature =
            if (backend == KeystoreBackend.STRONGBOX) PackageManager.FEATURE_STRONGBOX_KEYSTORE
            else PackageManager.FEATURE_HARDWARE_KEYSTORE
        return app.packageManager.systemAvailableFeatures
            .firstOrNull { it.name == feature }
            ?.version
            ?.takeIf { it > 0 }
    }

    private val Support.known: Boolean?
        get() =
            when (this) {
                Support.YES -> true
                Support.NO -> false
                Support.INCONCLUSIVE -> null
            }

    private fun forget(store: KeyStore, alias: String) {
        store.deleteEntry(alias)
        contexts.edit().remove(alias).commit()
    }

    private fun context(
        backend: KeystoreBackend,
        measurement: BackendMeasurement?,
        challenge: ByteArray,
    ) =
        KeyImportContext(
            generated = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            challenge = challenge,
            device =
                KeyImportContext.Device(
                    Build.MANUFACTURER,
                    Build.BRAND,
                    Build.MODEL,
                    Build.DEVICE,
                    Build.PRODUCT,
                    Build.HARDWARE,
                    systemProperty("ro.board.platform"),
                    Build.SKU.takeIf { it != Build.UNKNOWN },
                    Build.ODM_SKU.takeIf { it != Build.UNKNOWN },
                    systemProperty("ro.product.odm.manufacturer"),
                    systemProperty("ro.product.odm.brand"),
                    systemProperty("ro.product.odm.model"),
                    systemProperty("ro.product.odm.device"),
                    systemProperty("ro.product.odm.name"),
                ),
            soc =
                KeyImportContext.Soc(Build.SOC_MANUFACTURER, Build.SOC_MODEL).takeIf {
                    it.manufacturer != Build.UNKNOWN || it.model != Build.UNKNOWN
                },
            android =
                KeyImportContext.AndroidVersion(
                    Build.VERSION.RELEASE,
                    androidApiVersion,
                    Build.VERSION.SECURITY_PATCH,
                    systemProperty("ro.product.first_api_level")?.toIntOrNull(),
                    systemProperty("ro.vendor.api_level")?.toIntOrNull(),
                    systemProperty("ro.board.first_api_level")?.toIntOrNull(),
                    systemProperty("ro.board.api_level")?.toIntOrNull(),
                ),
            keystore =
                KeyImportContext.Keystore(
                    backend.name.lowercase(),
                    measurement?.implementation,
                    keystoreFeatureVersion(backend),
                    measurement?.concurrentOccupancy,
                    measurement
                        ?.algorithms
                        ?.map { (algorithm, support) ->
                            algorithm.name.lowercase() to
                                KeyImportContext.Algorithm(
                                    support.generation.known,
                                    support.rawImport.support.known,
                                    support.wrappedImport.support.known,
                                )
                        }
                        ?.toMap()
                        ?.takeIf { it.isNotEmpty() },
                ),
            app =
                KeyImportContext.App(
                    BuildConfig.VERSION_NAME,
                    BuildConfig.VERSION_CODE,
                    BuildConfig.DEBUG,
                    signingFingerprint(),
                    app.packageManager.getInstallSourceInfo(app.packageName).installingPackageName,
                ),
        )

    private fun generations(store: KeyStore, backend: KeystoreBackend): List<Int> {
        val prefix = aliasPrefix(backend)
        return store
            .aliases()
            .toList()
            .filter { it.startsWith(prefix) }
            .mapNotNull { it.removePrefix(prefix).toIntOrNull() }
            .sortedDescending()
    }

    private fun aliasPrefix(backend: KeystoreBackend) =
        ALIAS_PREFIX + backend.name.lowercase() + "-"

    private fun alias(backend: KeystoreBackend, generation: Int) = aliasPrefix(backend) + generation

    private fun keyStore() = KeyStore.getInstance(PROVIDER).apply { load(null) }

    companion object {
        const val MAX_CHALLENGE_BYTES = 128
        private const val PROVIDER = "AndroidKeyStore"
        private const val ALIAS_PREFIX = "felica-tool-key-import-identity-"
        private const val ATTESTATION_OID = "1.3.6.1.4.1.11129.2.1.17"
        private const val RETAINED_IDENTITIES = 3
        private const val CONTEXTS_PREFERENCES = "key_import_identities"
        private const val DEFAULT_CHALLENGE_BYTES = 16
    }
}
