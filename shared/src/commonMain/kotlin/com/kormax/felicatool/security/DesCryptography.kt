package com.kormax.felicatool.security

const val DES_BLOCK_SIZE = 8
const val DES_KEY_SIZE = 8
const val DESEDE_KEY_SIZE = 24

interface DesCryptography {
    fun encryptDesBlock(key: SecretBytes, plaintext: SecretBytes): SecretBytes

    fun decryptDesBlock(key: SecretBytes, ciphertext: SecretBytes): SecretBytes

    fun encryptDesCbc(key: SecretBytes, plaintext: SecretBytes): SecretBytes

    fun decryptDesCbc(key: SecretBytes, ciphertext: SecretBytes): SecretBytes
}

interface SecureRandomSource {
    fun nextSecretBytes(size: Int): SecretBytes
}
