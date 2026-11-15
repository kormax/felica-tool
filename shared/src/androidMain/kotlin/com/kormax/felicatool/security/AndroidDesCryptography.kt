package com.kormax.felicatool.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class AndroidDesCryptography : DesCryptography {
    override fun encryptDesBlock(key: SecretBytes, plaintext: SecretBytes): SecretBytes =
        runCipher("DES/ECB/NoPadding", Cipher.ENCRYPT_MODE, key, plaintext)

    override fun decryptDesBlock(key: SecretBytes, ciphertext: SecretBytes): SecretBytes =
        runCipher("DES/ECB/NoPadding", Cipher.DECRYPT_MODE, key, ciphertext)

    override fun encryptDesCbc(key: SecretBytes, plaintext: SecretBytes): SecretBytes =
        runCipher(
            "DES/CBC/NoPadding",
            Cipher.ENCRYPT_MODE,
            key,
            plaintext,
            IvParameterSpec(ByteArray(DES_BLOCK_SIZE)),
        )

    override fun decryptDesCbc(key: SecretBytes, ciphertext: SecretBytes): SecretBytes =
        runCipher(
            "DES/CBC/NoPadding",
            Cipher.DECRYPT_MODE,
            key,
            ciphertext,
            IvParameterSpec(ByteArray(DES_BLOCK_SIZE)),
        )

    private fun runCipher(
        transformation: String,
        mode: Int,
        key: SecretBytes,
        input: SecretBytes,
        iv: IvParameterSpec? = null,
    ): SecretBytes {
        val keyBytes = key.copyToByteArray()
        val inputBytes = input.copyToByteArray()
        try {
            require(keyBytes.size == DES_BLOCK_SIZE) { "DES key must be exactly 8 bytes" }
            require(inputBytes.isNotEmpty() && inputBytes.size % DES_BLOCK_SIZE == 0) {
                "DES input must be a non-empty multiple of 8 bytes"
            }
            val cipher = Cipher.getInstance(transformation)
            val spec = SecretKeySpec(keyBytes, "DES")
            if (iv == null) cipher.init(mode, spec) else cipher.init(mode, spec, iv)
            val output = cipher.doFinal(inputBytes)
            return try {
                SecretBytes.fromByteArray(output)
            } finally {
                output.fill(0)
            }
        } finally {
            inputBytes.fill(0)
            keyBytes.fill(0)
        }
    }
}

class AndroidSecureRandomSource : SecureRandomSource {
    private val random = SecureRandom()

    override fun nextSecretBytes(size: Int): SecretBytes {
        val bytes = ByteArray(size).also(random::nextBytes)
        return try {
            SecretBytes.fromByteArray(bytes)
        } finally {
            bytes.fill(0)
        }
    }
}
