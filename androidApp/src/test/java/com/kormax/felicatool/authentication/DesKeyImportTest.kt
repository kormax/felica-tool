package com.kormax.felicatool.authentication

import com.kormax.felicatool.security.AndroidDesCryptography
import com.kormax.felicatool.security.AndroidSecureRandomSource
import com.kormax.felicatool.security.SecretBytes
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DesKeyImportTest {
    private val a = "0123456789ABCDEF"
    private val l = "FEDCBA9876543210"
    private val b = "89ABCDEF01234567"

    private class FakeKeyStore : ProtectedDesKeyStore {
        val keys = mutableMapOf<String, Pair<ByteArray, KeyStorage>>()

        override suspend fun storages() =
            KeyStorage.entries.map { KeyStorageOption(it, isAvailable = true) }

        override suspend fun protect(
            referenceId: String,
            key: SecretBytes,
            use: DesKeyUse,
            storage: KeyStorage,
        ): ProtectedKeyReference {
            keys[referenceId] = key.copyToByteArray() to storage
            return ProtectedKeyReference(referenceId, storage)
        }

        override suspend fun recipientStorage(recipient: KeyImportRecipient) =
            KeyStorage.DEDICATED_SECURE_MODULE

        override suspend fun importWrapped(
            referenceId: String,
            recipient: KeyImportRecipient,
            wrappedKey: ByteArray,
            use: DesKeyUse,
        ): ProtectedKeyReference {
            keys[referenceId] = wrappedKey.copyOf() to KeyStorage.DEDICATED_SECURE_MODULE
            return ProtectedKeyReference(referenceId, KeyStorage.DEDICATED_SECURE_MODULE)
        }

        override suspend fun decryptWrapped(
            recipient: KeyImportRecipient,
            wrappedKey: ByteArray,
            iv: ByteArray,
            ciphertext: ByteArray,
        ): ByteArray =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(wrappedKey, "AES"),
                    GCMParameterSpec(128, iv),
                )
                doFinal(ciphertext)
            }

        override suspend fun encrypt(referenceId: String, block: ByteArray) =
            transform(referenceId, Cipher.ENCRYPT_MODE, block)

        override suspend fun decrypt(referenceId: String, block: ByteArray) =
            transform(referenceId, Cipher.DECRYPT_MODE, block)

        private fun transform(referenceId: String, mode: Int, block: ByteArray): ByteArray =
            Cipher.getInstance("DESede/ECB/NoPadding").run {
                init(mode, SecretKeySpec(keys.getValue(referenceId).first, "DESede"))
                doFinal(block)
            }

        override suspend fun delete(referenceId: String) {
            keys.remove(referenceId)
        }

        override suspend fun deleteAll(except: Set<String>) {
            keys.keys.retainAll(except)
        }
    }

    private class MemoryStorage : SecureAuthenticationProviderStorage {
        var data: ByteArray? = null

        override suspend fun read() = data?.copyOf()

        override suspend fun write(data: ByteArray) {
            this.data = data.copyOf()
        }

        override suspend fun delete() {
            data = null
        }
    }

    private val keyStore = FakeKeyStore()
    private val storage = MemoryStorage()
    private var nextId = 0
    private var providers = providers()

    private fun providers() =
        AuthenticationProviders(
            storage,
            keyStore,
            { "id-${nextId++}" },
            AndroidDesCryptography(),
            AndroidSecureRandomSource(),
        )

    private fun scope(idm: String) =
        """"system":"0003","idm":"$idm","areas":["0000"],"nodes":["090F"],""" +
            """"versions":{"FFFF":0,"0000":0}"""

    private fun composite(idm: String) = """{${scope(idm)},"ala":"$a$l$a","lbl":"$l$b$l"}"""

    private fun components(idm: String) = """{${scope(idm)},"a":"$a","l":"$l","b":"$b"}"""

    private fun wrapped(hex: String) =
        """{"type":"wrapped","data":"${Base64.getEncoder().encodeToString(hex.hexToByteArray())}"}"""

    private fun wrappedComponents(idm: String) =
        """{${scope(idm)},"a":${wrapped(a + a + a)},"l":${wrapped(l + l + l)},""" +
            """"b":${wrapped(b + b + b)}}"""

    private val recipient =
        """"recipient":{"method":"test","identity":{"salt":"${"00".repeat(16)}",""" +
            """"hash":"${"00".repeat(32)}"}}"""

    private fun file(vararg records: String, recipient: Boolean = false) =
        ("{" +
                (if (recipient) "${this.recipient}," else "") +
                """"keys":[${records.joinToString(",")}]}""")
            .encodeToByteArray()

    private fun import(
        provider: String,
        data: ByteArray,
        storage: KeyStorage = KeyStorage.TRUSTED_EXECUTION_ENVIRONMENT,
    ) = runBlocking { providers.importCredentials(provider, data, storage) { _, _ -> } }

    private fun provider() = runBlocking {
        providers.createProvider(AuthenticationProviderType.DES_AUTHENTICATION, "Test")
    }

    @Test
    fun testReimportIsAlreadyStored() {
        val provider = provider()
        import(provider, file(composite("0102030405060708")))
        val result = import(provider, file(composite("0102030405060708")))
        assertEquals(CredentialImportResult(0, 0, 1, 0, 0), result)
        assertEquals(2, keyStore.keys.size)
    }

    @Test
    fun testRepeatedRecordIsCounted() {
        val provider = provider()
        val result =
            import(provider, file(composite("0102030405060708"), components("0102030405060708")))
        assertEquals(CredentialImportResult(1, 0, 0, 1, 0), result)
        assertEquals(2, keyStore.keys.size)
    }

    @Test
    fun testWrappedComponentsMoveRawComposite() {
        val provider = provider()
        import(provider, file(composite("0102030405060708")))
        val result = import(provider, file(wrappedComponents("0102030405060708"), recipient = true))
        assertEquals(CredentialImportResult(0, 1, 0, 0, 0), result)
        assertEquals(
            setOf(KeyStorage.DEDICATED_SECURE_MODULE),
            keyStore.keys.values.map { it.second }.toSet(),
        )
        assertEquals(3, keyStore.keys.size)
    }

    @Test
    fun testIdenticalKeysAreSharedWithinBackend() {
        val first = provider()
        val second = provider()
        import(first, file(composite("0102030405060708")))
        import(second, file(components("1112131415161718")))
        assertEquals(2, keyStore.keys.size)
        runBlocking { providers.deleteProvider(first) }
        assertEquals(2, keyStore.keys.size)
        runBlocking { providers.deleteProvider(second) }
        assertEquals(0, keyStore.keys.size)
    }

    @Test
    fun testIdenticalKeysAreSeparateAcrossBackends() {
        val first = provider()
        val second = provider()
        import(first, file(composite("0102030405060708")))
        import(
            second,
            file(composite("0102030405060708")),
            KeyStorage.DEDICATED_SECURE_MODULE,
        )
        assertEquals(4, keyStore.keys.size)
    }

    @Test
    fun testComponentsAreRefreshedToComposite() {
        val first = provider()
        val second = provider()
        import(first, file(wrappedComponents("0102030405060708"), recipient = true))
        import(second, file(wrappedComponents("1112131415161718"), recipient = true))
        assertEquals(3, keyStore.keys.size)
        val result =
            import(
                first,
                file(composite("0102030405060708")),
                KeyStorage.DEDICATED_SECURE_MODULE,
            )
        assertEquals(CredentialImportResult(0, 0, 0, 0, 1), result)
        assertEquals(2, keyStore.keys.size)
        val stored = runBlocking {
            listOf(first, second)
                .flatMap { providers.credentials(it) }
                .map { summary ->
                    (summary as DesAuthenticationKeySummary).components.map { it.name }
                }
        }
        assertEquals(listOf(listOf("ala", "lbl"), listOf("ala", "lbl")), stored)
    }

    @Test
    fun testCompositeIsNotReplacedByComponents() {
        val provider = provider()
        import(
            provider,
            file(composite("0102030405060708")),
            KeyStorage.DEDICATED_SECURE_MODULE,
        )
        val result = import(provider, file(wrappedComponents("0102030405060708"), recipient = true))
        assertEquals(CredentialImportResult(0, 0, 1, 0, 0), result)
        assertEquals(2, keyStore.keys.size)
    }

    private fun encrypted(plaintext: ByteArray, extra: String = ""): ByteArray {
        val key = ByteArray(32) { it.toByte() }
        val iv = ByteArray(12) { (it + 1).toByte() }
        val ciphertext =
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
                doFinal(plaintext)
            }
        val encoder = Base64.getEncoder()
        return ("{$recipient,$extra\"encrypted\":{\"key\":{\"type\":\"wrapped\"," +
                "\"data\":\"${encoder.encodeToString(key)}\"}," +
                "\"iv\":\"${encoder.encodeToString(iv)}\"," +
                "\"data\":\"${encoder.encodeToString(ciphertext)}\"}}")
            .encodeToByteArray()
    }

    @Test
    fun testEncryptedRawKeysAskForStorage() {
        val provider = provider()
        val prepared = runBlocking {
            providers.prepareCredentialImport(
                provider,
                encrypted(file(composite("0102030405060708"))),
            )
        }
        assertNull(prepared.storage)
        assertEquals(CredentialImportResult(1, 0, 0, 0, 0), import(provider, prepared.data))
        assertEquals(
            setOf(KeyStorage.TRUSTED_EXECUTION_ENVIRONMENT),
            keyStore.keys.values.map { it.second }.toSet(),
        )
    }

    @Test
    fun testEncryptedWrappedKeysUseIdentityStorage() {
        val provider = provider()
        val prepared = runBlocking {
            providers.prepareCredentialImport(
                provider,
                encrypted(file(wrappedComponents("0102030405060708"), recipient = true)),
            )
        }
        assertEquals(KeyStorage.DEDICATED_SECURE_MODULE, prepared.storage)
        assertEquals(
            CredentialImportResult(1, 0, 0, 0, 0),
            import(provider, prepared.data, KeyStorage.DEDICATED_SECURE_MODULE),
        )
        assertEquals(3, keyStore.keys.size)
    }

    @Test
    fun testKeysAlongsideEncryptedAreRejected() {
        val provider = provider()
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                providers.prepareCredentialImport(
                    provider,
                    encrypted(file(composite("0102030405060708")), "\"keys\":[],"),
                )
            }
        }
    }

    @Test
    fun testSharedKeysSurviveForUnreadableProviders() {
        val first = provider()
        val second = provider()
        import(first, file(composite("0102030405060708")))
        import(second, file(composite("1112131415161718")))
        assertEquals(2, keyStore.keys.size)
        val root = Json.parseToJsonElement(checkNotNull(storage.data).decodeToString()).jsonObject
        val broken =
            JsonObject(
                root +
                    ("providers" to
                        JsonArray(
                            root.getValue("providers").jsonArray.map { element ->
                                val provider = element.jsonObject
                                if (provider.getValue("id").jsonPrimitive.content == second) {
                                    JsonObject(provider + ("type" to JsonPrimitive("UNKNOWN")))
                                } else {
                                    provider
                                }
                            },
                        )),
            )
        storage.data = broken.toString().encodeToByteArray()
        providers = providers()
        runBlocking {
            assertEquals(1, providers.unreadableProviderCount())
            providers.deleteProvider(first)
        }
        assertEquals(2, keyStore.keys.size)
    }
}
