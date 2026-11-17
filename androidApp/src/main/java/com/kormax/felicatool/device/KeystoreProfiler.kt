package com.kormax.felicatool.device

import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.security.keystore.SecureKeyImportUnavailableException
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.WrappedKeyEntry
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.SecretKeySpec
import kotlin.system.measureNanoTime
import org.bouncycastle.asn1.ASN1Enumerated
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject

private const val PROVIDER = "AndroidKeyStore"
private const val PREFIX = "felica-tool-key-keystore-capability-detection."
private const val SAMPLE_COUNT = 9
private const val MAX_TESTED_OCCUPANCY = 128
private const val PURPOSES = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
private val profiledAlgorithms = listOf(KeyAlgorithm.DESEDE)
private const val ECB = KeyProperties.BLOCK_MODE_ECB
private const val NO_PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
private val sampleBlock = ByteArray(16) { it.toByte() }

internal class KeystoreProfiler(private val pm: PackageManager) {
    private val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
    private val random = SecureRandom()

    fun measure(progress: (String) -> Unit): List<BackendMeasurement> =
        KeystoreBackend.entries
            .filter {
                it != KeystoreBackend.STRONGBOX ||
                    pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
            }
            .map { measure(it, progress) }

    fun measure(backend: KeystoreBackend, progress: (String) -> Unit = {}): BackendMeasurement {
        clearDetectionKeys()
        try {
            progress("${backend.label} · Key attestation")
            var version: Int? = null
            val attested = checkSupport {
                version = ASN1Integer.getInstance(attest(backend).getObjectAt(2)).intValueExact()
            }
            store.deleteEntry(PREFIX + "attest")

            progress("${backend.label} · Device ID attestation")
            var ids = emptyList<AttestationId>()
            if (attested != Support.NO)
                checkSupport {
                    val record = attest(backend, includeDeviceProperties = true)
                    val hardware =
                        ASN1Sequence.getInstance(record.getObjectAt(7)).map {
                            ASN1TaggedObject.getInstance(it).tagNo
                        }
                    ids = AttestationId.entries.filter { it.tag in hardware }
                }
            store.deleteEntry(PREFIX + "attest")

            progress("${backend.label} · Wrapping key")
            var pair: KeyPair? = null
            val canWrap = checkSupport {
                val spec = SecureKeyWrapper.wrappingKeySpec(PREFIX + "wrap", backend).build()
                pair = keyPair("RSA", spec, backend)
            }
            val algorithms = profiledAlgorithms.associateWith { algorithm ->
                progress("${backend.label} · ${algorithm.label}")
                measureAlgorithm(backend, algorithm, pair, canWrap)
            }
            clearDetectionKeys()
            progress("${backend.label} · Maximum simultaneous occupancy")
            var occupancy: Int? = null
            checkSupport { occupancy = measureConcurrentOccupancy(backend) }
            return BackendMeasurement(
                backend,
                implementation(backend),
                attested,
                version,
                ids,
                algorithms,
                occupancy,
            )
        } finally {
            clearDetectionKeys()
        }
    }

    private fun implementation(backend: KeystoreBackend): String? {
        if (backend == KeystoreBackend.STRONGBOX) {
            if (Build.VERSION.SDK_INT < 37) return null
            return listOf(Build.STRONGBOX_MANUFACTURER, Build.STRONGBOX_MODEL)
                .filter { it.isNotBlank() && it != "unsupported" && it != Build.UNKNOWN }
                .joinToString(" ")
                .ifEmpty { null }
        }
        return systemProperty("ro.hardware.keystore")
    }

    private fun clearDetectionKeys() {
        store.aliases().toList().filter { it.startsWith(PREFIX) }.forEach(store::deleteEntry)
    }

    private fun measureAlgorithm(
        backend: KeystoreBackend,
        algorithm: KeyAlgorithm,
        wrapper: KeyPair?,
        canWrap: Support,
    ): AlgorithmMeasurement {
        val alias = PREFIX + "sample"
        val gen = mutableListOf<Long>()
        var benchmarkKey: SecretKey? = null
        val generation = checkSupport {
            val generator = generator(alias, algorithm, backend)
            lateinit var key: SecretKey
            repeat(SAMPLE_COUNT) {
                gen += measureNanoTime { key = generator.generateKey() }
            }
            checkGeneratedKey(key, algorithm.bits, backend)
            checkRoundTrip(key)
            benchmarkKey = key
        }
        val raw = ByteArray(if (algorithm == KeyAlgorithm.DESEDE) 24 else algorithm.bits / 8)
        random.nextBytes(raw)
        val source = SecretKeySpec(raw, algorithm.algorithm)
        val knownAnswer =
            try {
                crypt(source, Cipher.ENCRYPT_MODE, sampleBlock)
            } catch (e: Exception) {
                Log.w(
                    "KeystoreProfiler",
                    "Could not compute ${algorithm.label} reference ciphertext",
                    e,
                )
                null
            }
        fun measureImport(alias: String, import: () -> Unit): ImportMeasurement {
            if (knownAnswer == null) return ImportMeasurement(Support.INCONCLUSIVE)
            var info: KeyInfo? = null
            var infoError: String? = null
            val support = checkSupport {
                import()
                val key = store.getKey(alias, null) as SecretKey
                check(key.encoded == null && checkRoundTrip(key).contentEquals(knownAnswer))
                info =
                    try {
                        keyInfo(key)
                    } catch (e: java.security.ProviderException) {
                        infoError = e.message ?: e.javaClass.simpleName
                        null
                    }
                info?.let {
                    check(it.securityLevel == backend.level) {
                        "Imported key is outside ${backend.label}"
                    }
                }
                if (benchmarkKey == null) benchmarkKey = key
            }
            return ImportMeasurement(
                support,
                info?.securityLevel,
                info?.origin,
                info?.keySize,
                infoError,
            )
        }

        val importAlias = PREFIX + "raw"
        val rawImport =
            measureImport(importAlias) {
                val protection =
                    KeyProtection.Builder(PURPOSES)
                        .setBlockModes(ECB)
                        .setEncryptionPaddings(NO_PADDING)
                        .setRandomizedEncryptionRequired(false)
                        .setIsStrongBoxBacked(backend == KeystoreBackend.STRONGBOX)
                        .build()
                store.setEntry(importAlias, KeyStore.SecretKeyEntry(source), protection)
            }
        val wrappedAlias = PREFIX + "wrapped"
        val wrappedImport =
            if (wrapper == null) ImportMeasurement(canWrap)
            else
                measureImport(wrappedAlias) {
                    val publicKey =
                        KeyFactory.getInstance("RSA")
                            .generatePublic(X509EncodedKeySpec(wrapper.public.encoded))
                    val entry =
                        WrappedKeyEntry(
                            SecureKeyWrapper.wrap(publicKey, algorithm, raw),
                            PREFIX + "wrap",
                            "RSA/ECB/OAEPPadding",
                            KeyGenParameterSpec.Builder(
                                    PREFIX + "wrap",
                                    KeyProperties.PURPOSE_WRAP_KEY,
                                )
                                .setDigests(KeyProperties.DIGEST_SHA256)
                                .build(),
                        )
                    store.setEntry(wrappedAlias, entry, null)
                }
        val encrypt = mutableListOf<Long>()
        val decrypt = mutableListOf<Long>()
        benchmarkKey?.let { key ->
            val timing = checkSupport {
                repeat(SAMPLE_COUNT) {
                    val ciphertext: ByteArray
                    val plaintext: ByteArray
                    encrypt += measureNanoTime {
                        ciphertext = crypt(key, Cipher.ENCRYPT_MODE, sampleBlock)
                    }
                    decrypt += measureNanoTime {
                        plaintext = crypt(key, Cipher.DECRYPT_MODE, ciphertext)
                    }
                    check(plaintext.contentEquals(sampleBlock))
                }
            }
            if (timing != Support.YES) {
                encrypt.clear()
                decrypt.clear()
            }
        }
        return AlgorithmMeasurement(
            generation,
            rawImport,
            wrappedImport,
            if (generation == Support.YES) gen else emptyList(),
            encrypt,
            decrypt,
        )
    }

    private fun generator(
        alias: String,
        algorithm: KeyAlgorithm,
        backend: KeystoreBackend,
    ): KeyGenerator {
        val spec =
            KeyGenParameterSpec.Builder(alias, PURPOSES)
                .setKeySize(algorithm.bits)
                .setBlockModes(ECB)
                .setEncryptionPaddings(NO_PADDING)
                .setRandomizedEncryptionRequired(false)
                .setIsStrongBoxBacked(backend == KeystoreBackend.STRONGBOX)
                .build()
        return KeyGenerator.getInstance(algorithm.algorithm, PROVIDER).apply { init(spec) }
    }

    private fun keyInfo(key: SecretKey) =
        SecretKeyFactory.getInstance(key.algorithm, PROVIDER).getKeySpec(key, KeyInfo::class.java)
            as KeyInfo

    private fun checkGeneratedKey(
        key: SecretKey,
        bits: Int,
        backend: KeystoreBackend,
    ) {
        val info = keyInfo(key)
        check(
            info.securityLevel == backend.level &&
                info.origin == KeyProperties.ORIGIN_GENERATED &&
                info.keySize == bits,
        )
    }

    private fun checkRoundTrip(key: SecretKey): ByteArray {
        val ciphertext = crypt(key, Cipher.ENCRYPT_MODE, sampleBlock)
        check(crypt(key, Cipher.DECRYPT_MODE, ciphertext).contentEquals(sampleBlock))
        return ciphertext
    }

    private fun crypt(key: SecretKey, mode: Int, data: ByteArray): ByteArray =
        Cipher.getInstance("${key.algorithm}/$ECB/$NO_PADDING").run {
            init(mode, key)
            doFinal(data)
        }

    private fun keyPair(
        algorithm: String,
        spec: KeyGenParameterSpec,
        backend: KeystoreBackend,
    ): KeyPair {
        val pair =
            KeyPairGenerator.getInstance(algorithm, PROVIDER).run {
                initialize(spec)
                generateKeyPair()
            }
        val info =
            KeyFactory.getInstance(algorithm, PROVIDER)
                .getKeySpec(pair.private, KeyInfo::class.java)
        check(info.securityLevel == backend.level)
        return pair
    }

    private fun attest(
        backend: KeystoreBackend,
        includeDeviceProperties: Boolean = false,
    ): ASN1Sequence {
        val alias = PREFIX + "attest"
        val challenge = ByteArray(32).also(random::nextBytes)
        val spec =
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setIsStrongBoxBacked(backend == KeystoreBackend.STRONGBOX)
                .setAttestationChallenge(challenge)
                .setDevicePropertiesAttestationIncluded(includeDeviceProperties)
                .build()
        val pair = keyPair("EC", spec, backend)
        val chain = store.getCertificateChain(alias).map { it as X509Certificate }
        check(chain.size >= 2 && chain.first().publicKey.encoded.contentEquals(pair.public.encoded))
        chain.zipWithNext().forEach { (child, issuer) -> child.verify(issuer.publicKey) }
        val ext =
            chain.asReversed().firstNotNullOf { it.getExtensionValue("1.3.6.1.4.1.11129.2.1.17") }
        val record = ASN1Sequence.getInstance(ASN1OctetString.getInstance(ext).octets)
        check(ASN1OctetString.getInstance(record.getObjectAt(4)).octets.contentEquals(challenge))
        check(ASN1Enumerated.getInstance(record.getObjectAt(1)).intValueExact() == backend.level)
        check(ASN1Enumerated.getInstance(record.getObjectAt(3)).intValueExact() == backend.level)
        return record
    }

    private fun measureConcurrentOccupancy(backend: KeystoreBackend): Int {
        val keys = linkedMapOf<SecretKey, ByteArray>()
        for (limit in listOf(4, 8, 16, 32, 64, MAX_TESTED_OCCUPANCY)) {
            val requested = minOf(limit + 1, MAX_TESTED_OCCUPANCY)
            while (keys.size < requested) {
                val spec =
                    KeyGenParameterSpec.Builder(
                            PREFIX + "slot." + keys.size,
                            KeyProperties.PURPOSE_SIGN,
                        )
                        .setKeySize(256)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setIsStrongBoxBacked(backend == KeystoreBackend.STRONGBOX)
                        .build()
                val key =
                    KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, PROVIDER)
                        .run {
                            init(spec)
                            generateKey()
                        }
                checkGeneratedKey(key, 256, backend)
                keys[key] = initializedMac(key).doFinal(sampleBlock)
            }
            val completed = finishBatch(keys)
            if (completed < requested) {
                check(finishBatch(keys) == completed) {
                    "Key occupancy varied between batches"
                }
                return completed
            }
        }
        return MAX_TESTED_OCCUPANCY
    }

    private fun initializedMac(key: SecretKey): Mac =
        Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256).apply { init(key) }

    private fun finishBatch(keys: Map<SecretKey, ByteArray>): Int {
        val held = mutableListOf<Pair<Mac, ByteArray>>()
        var completed = 0
        var failure: Exception? = null
        try {
            for ((key, expected) in keys) {
                held += initializedMac(key) to expected
            }
        } finally {
            for ((mac, expected) in held.asReversed()) {
                try {
                    check(mac.doFinal(sampleBlock).contentEquals(expected))
                    completed++
                } catch (e: Exception) {
                    val pruned =
                        generateSequence<Throwable>(e) { it.cause }
                            .any {
                                (Build.VERSION.SDK_INT >= 33 &&
                                    it is android.security.KeyStoreException &&
                                    it.numericErrorCode ==
                                        android.security.KeyStoreException
                                            .ERROR_KEY_OPERATION_EXPIRED) ||
                                    it.message?.contains("Pruned", ignoreCase = true) == true
                            }
                    if (!pruned) failure = e
                }
            }
        }
        failure?.let { throw it }
        return completed
    }

    private inline fun checkSupport(operation: () -> Unit): Support =
        try {
            operation()
            Support.YES
        } catch (e: Exception) {
            val causes = generateSequence<Throwable>(e) { it.cause }.toList()
            val keystoreError =
                if (Build.VERSION.SDK_INT >= 33)
                    causes.filterIsInstance<android.security.KeyStoreException>().firstOrNull()
                else null
            when {
                Build.VERSION.SDK_INT >= 33 && keystoreError?.isTransientFailure == true ->
                    Support.INCONCLUSIVE
                causes.any {
                    it is NoSuchAlgorithmException ||
                        it is SecureKeyImportUnavailableException ||
                        it is StrongBoxUnavailableException
                } -> Support.NO
                Build.VERSION.SDK_INT >= 33 &&
                    keystoreError?.numericErrorCode in
                        setOf(
                            android.security.KeyStoreException.ERROR_UNIMPLEMENTED,
                            android.security.KeyStoreException.ERROR_ID_ATTESTATION_FAILURE,
                        ) -> Support.NO
                else -> Support.INCONCLUSIVE
            }.also {
                if (it == Support.INCONCLUSIVE)
                    Log.w("KeystoreProfiler", "Inconclusive hardware measurement", e)
            }
        }
}

internal fun systemProperty(name: String): String? =
    try {
        val process = ProcessBuilder("/system/bin/getprop", name).start()
        process.inputStream
            .bufferedReader()
            .use { it.readText().trim() }
            .takeIf { process.waitFor() == 0 && it.isNotEmpty() && it != Build.UNKNOWN }
    } catch (_: java.io.IOException) {
        null
    }
