package com.kormax.felicatool.authentication

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidSecureAuthenticationProviderStorage(context: Context) :
    SecureAuthenticationProviderStorage {
    private val file = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))
    private val lock = Any()

    override suspend fun read(): ByteArray? =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val encrypted =
                    try {
                        file.readFully()
                    } catch (_: FileNotFoundException) {
                        return@synchronized null
                    }
                decrypt(encrypted)
            }
        }

    override suspend fun write(data: ByteArray) =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                val encrypted = encrypt(data)
                val output = file.startWrite()
                try {
                    output.write(encrypted)
                    output.flush()
                    file.finishWrite(output)
                } catch (error: Throwable) {
                    file.failWrite(output)
                    throw error
                }
            }
        }

    override suspend fun delete() =
        withContext(Dispatchers.IO) { synchronized(lock) { file.delete() } }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        return cipher.iv + ciphertext
    }

    private fun decrypt(encrypted: ByteArray): ByteArray {
        require(encrypted.size > IV_SIZE) { "Authentication provider file is truncated" }
        val iv = encrypted.copyOfRange(0, IV_SIZE)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return cipher.doFinal(encrypted, IV_SIZE, encrypted.size - IV_SIZE)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = keyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let {
            return it
        }
        return generateKey()
    }

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        val builder =
            KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private companion object {
        const val FILE_NAME = "authentication-providers.enc"
        const val KEY_ALIAS = "felica-tool-authentication-provider-storage"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val GCM_TAG_BITS = 128
    }
}
