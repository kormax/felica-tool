package com.kormax.felicatool.device

import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.DERTaggedObject

internal object SecureKeyWrapper {
    fun wrap(publicKey: PublicKey, algorithm: KeyAlgorithm, keyMaterial: ByteArray): ByteArray {
        val keyDescription =
            DERSequence(
                arrayOf(
                    ASN1Integer(KEY_FORMAT_RAW),
                    authorizationList(algorithm),
                ),
            )
        val random = SecureRandom()
        val transportKey = ByteArray(AES_TRANSPORT_KEY_BYTES).also(random::nextBytes)
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        return try {
            val rsa = Cipher.getInstance("RSA/ECB/OAEPPadding")
            rsa.init(
                Cipher.ENCRYPT_MODE,
                publicKey,
                OAEPParameterSpec(
                    "SHA-256",
                    "MGF1",
                    MGF1ParameterSpec.SHA1,
                    PSource.PSpecified.DEFAULT,
                ),
            )
            val encryptedTransportKey = rsa.doFinal(transportKey)
            val aes = Cipher.getInstance("AES/GCM/NoPadding")
            aes.init(
                Cipher.ENCRYPT_MODE,
                SecretKeySpec(transportKey, "AES"),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            aes.updateAAD(keyDescription.encoded)
            val encryptedKeyWithTag = aes.doFinal(keyMaterial)
            val tagOffset = encryptedKeyWithTag.size - GCM_TAG_BYTES
            DERSequence(
                    arrayOf(
                        ASN1Integer(WRAPPER_VERSION),
                        DEROctetString(encryptedTransportKey),
                        DEROctetString(iv),
                        keyDescription,
                        DEROctetString(encryptedKeyWithTag.copyOfRange(0, tagOffset)),
                        DEROctetString(
                            encryptedKeyWithTag.copyOfRange(tagOffset, encryptedKeyWithTag.size),
                        ),
                    ),
                )
                .encoded
        } finally {
            transportKey.fill(0)
        }
    }

    private fun authorizationList(algorithm: KeyAlgorithm): DERSequence {
        val keymintAlgorithm =
            if (algorithm == KeyAlgorithm.DESEDE) ALGORITHM_TRIPLE_DES else ALGORITHM_AES
        return DERSequence(
            arrayOf(
                DERTaggedObject(
                    true,
                    TAG_PURPOSE,
                    DERSet(
                        arrayOf(
                            ASN1Integer(KEYMASTER_PURPOSE_ENCRYPT),
                            ASN1Integer(KEYMASTER_PURPOSE_DECRYPT),
                        ),
                    ),
                ),
                DERTaggedObject(true, TAG_ALGORITHM, ASN1Integer(keymintAlgorithm)),
                DERTaggedObject(true, TAG_KEY_SIZE, ASN1Integer(algorithm.bits.toLong())),
                DERTaggedObject(true, TAG_BLOCK_MODE, DERSet(ASN1Integer(BLOCK_MODE_ECB))),
                DERTaggedObject(true, TAG_PADDING, DERSet(ASN1Integer(PADDING_NONE))),
                DERTaggedObject(true, TAG_NO_AUTH_REQUIRED, DERNull.INSTANCE),
            ),
        )
    }

    private const val WRAPPER_VERSION = 0L
    private const val KEY_FORMAT_RAW = 3L
    private const val ALGORITHM_AES = 32L
    private const val ALGORITHM_TRIPLE_DES = 33L
    private const val KEYMASTER_PURPOSE_ENCRYPT = 0L
    private const val KEYMASTER_PURPOSE_DECRYPT = 1L
    private const val BLOCK_MODE_ECB = 1L
    private const val PADDING_NONE = 1L

    private const val TAG_PURPOSE = 1
    private const val TAG_ALGORITHM = 2
    private const val TAG_KEY_SIZE = 3
    private const val TAG_BLOCK_MODE = 4
    private const val TAG_PADDING = 6
    private const val TAG_NO_AUTH_REQUIRED = 503

    private const val AES_TRANSPORT_KEY_BYTES = 32
    private const val GCM_IV_BYTES = 12
    private const val GCM_TAG_BITS = 128
    private const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
}
