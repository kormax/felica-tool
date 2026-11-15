package com.kormax.felicatool.security

class DesSecureSession(
    transactionId: ByteArray,
    sessionKey: SecretBytes,
    private val cryptography: DesCryptography,
) : AutoCloseable {
    private val transactionId = transactionId.copyOf()
    private val key = sessionKey.copy()
    private var transactionNumber: Int? = null

    init {
        require(transactionId.size == TRANSACTION_ID_SIZE) {
            "Transaction ID must be exactly $TRANSACTION_ID_SIZE bytes"
        }
        require(sessionKey.size == DES_KEY_SIZE) {
            "DES session key must be exactly $DES_KEY_SIZE bytes"
        }
    }

    fun seal(commandCode: Int, payload: ByteArray): ByteArray {
        val current = checkNotNull(transactionNumber) { "Secure session is not established" }
        require(current < 0xFFFF) { "Secure session transaction number is exhausted" }
        val next = current + 1
        val body = ByteArray(HEADER_SIZE + payload.size)
        body[0] = (next and 0xFF).toByte()
        body[1] = ((next shr 8) and 0xFF).toByte()
        transactionId.copyInto(body, 2)
        payload.copyInto(body, HEADER_SIZE)
        transactionNumber = next
        return try {
            encrypt(commandCode, body)
        } finally {
            body.fill(0)
        }
    }

    fun open(responseCode: Int, blob: ByteArray): ByteArray {
        val body = decrypt(responseCode, blob)
        try {
            require(body.size >= HEADER_SIZE) {
                "Secure response is missing its transaction header"
            }
            val receivedId = body.copyOfRange(2, HEADER_SIZE)
            val matches =
                try {
                    constantTimeEquals(receivedId, transactionId)
                } finally {
                    receivedId.fill(0)
                }
            require(matches) { "Secure response transaction ID does not match the session" }
            val received = (body[0].toInt() and 0xFF) or ((body[1].toInt() and 0xFF) shl 8)
            transactionNumber?.let {
                require(received > it) { "Secure response transaction number did not advance" }
            }
            transactionNumber = received
            return body.copyOfRange(HEADER_SIZE, body.size)
        } finally {
            body.fill(0)
        }
    }

    override fun close() {
        key.close()
        transactionId.fill(0)
    }

    private fun encrypt(code: Int, body: ByteArray): ByteArray {
        val padded = pkcs7Pad(body)
        val securedSize = padded.size + DES_BLOCK_SIZE
        val secured = padded + mac(macHeader(securedSize, code), padded)
        padded.fill(0)
        return desCbc(secured, encrypt = true).also { secured.fill(0) }
    }

    private fun decrypt(code: Int, blob: ByteArray): ByteArray {
        require(blob.isNotEmpty() && blob.size % DES_BLOCK_SIZE == 0) {
            "Secure response must be DES-block aligned"
        }
        val decrypted = desCbc(blob, encrypt = false)
        try {
            require(decrypted.size >= DES_BLOCK_SIZE * 2) { "Secure response is too short" }
            verifyMac(code, decrypted)
            return decrypted.copyOfRange(0, decrypted.size - DES_BLOCK_SIZE)
        } finally {
            decrypted.fill(0)
        }
    }

    private fun macHeader(frameLength: Int, code: Int): ByteArray =
        ByteArray(DES_BLOCK_SIZE).also {
            it[0] = (frameLength + FRAME_LENGTH_PREFIX).toByte()
            it[1] = code.toByte()
        }

    private fun mac(header: ByteArray, padded: ByteArray): ByteArray {
        var running = header
        var offset = 0
        while (offset < padded.size) {
            running = desBlock(padded, offset, running, encrypt = true)
            offset += DES_BLOCK_SIZE
        }
        return running
    }

    private fun verifyMac(code: Int, decrypted: ByteArray) {
        var running = decrypted.copyOfRange(decrypted.size - DES_BLOCK_SIZE, decrypted.size)
        var offset = decrypted.size - DES_BLOCK_SIZE * 2
        while (offset >= 0) {
            running = desBlock(decrypted, offset, running, encrypt = false)
            offset -= DES_BLOCK_SIZE
        }
        require(constantTimeEquals(running, macHeader(decrypted.size, code))) {
            "Secure response MAC is invalid"
        }
    }

    private fun desBlock(
        keySource: ByteArray,
        keyOffset: Int,
        data: ByteArray,
        encrypt: Boolean,
    ): ByteArray {
        val keyBytes = keySource.copyOfRange(keyOffset, keyOffset + DES_BLOCK_SIZE)
        val keySecret = SecretBytes.fromByteArray(keyBytes)
        val dataSecret = SecretBytes.fromByteArray(data)
        keyBytes.fill(0)
        return try {
            val result =
                if (encrypt) cryptography.encryptDesBlock(keySecret, dataSecret)
                else cryptography.decryptDesBlock(keySecret, dataSecret)
            try {
                result.copyToByteArray()
            } finally {
                result.close()
            }
        } finally {
            keySecret.close()
            dataSecret.close()
        }
    }

    private fun desCbc(data: ByteArray, encrypt: Boolean): ByteArray {
        val input = SecretBytes.fromByteArray(data)
        return try {
            val result =
                if (encrypt) cryptography.encryptDesCbc(key, input)
                else cryptography.decryptDesCbc(key, input)
            try {
                result.copyToByteArray()
            } finally {
                result.close()
            }
        } finally {
            input.close()
        }
    }

    private fun pkcs7Pad(data: ByteArray): ByteArray {
        val padLength = DES_BLOCK_SIZE - (data.size % DES_BLOCK_SIZE)
        return data + ByteArray(padLength) { padLength.toByte() }
    }

    private fun constantTimeEquals(left: ByteArray, right: ByteArray): Boolean {
        val leftSecret = SecretBytes.fromByteArray(left)
        val rightSecret = SecretBytes.fromByteArray(right)
        return try {
            leftSecret.constantTimeEquals(rightSecret)
        } finally {
            leftSecret.close()
            rightSecret.close()
        }
    }

    private companion object {
        const val HEADER_SIZE = 8
        const val TRANSACTION_ID_SIZE = 6
        const val FRAME_LENGTH_PREFIX = 2
    }
}
