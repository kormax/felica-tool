package com.kormax.felicatool.felica

/**
 * Request Service v2 response received from FeliCa cards
 *
 * Contains the Key Versions for the requested Node Codes for each supported encryption type. If a
 * Node doesn't exist or the Key is not assigned, the key version will be 0xFFFF.
 *
 * The response includes:
 * - Status flags indicating success/failure
 * - Encryption identifier showing which encryption types are supported
 * - AES key versions (always present if status is success)
 * - DES key versions (present only if the card supports DES encryption)
 */
class RequestServiceV2Response(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag1 (see section 4.5 "Status Flag") */
    override val statusFlag1: Byte,

    /** Status Flag2 (see section 4.5 "Status Flag") */
    override val statusFlag2: Byte,

    /**
     * Encryption identifier indicating which encryption types are supported Only present if Status
     * Flag1 = 0x00
     */
    val encryptionIdentifier: EncryptionIdentifier?,

    /**
     * Array of AES Key Versions for the requested nodes Each key version is 2 bytes in Little
     * Endian format. 0xFFFF indicates the node doesn't exist or AES key is not assigned. Empty
     * array if Status Flag1 != 0x00
     */
    val aesKeyVersions: Array<KeyVersion> = emptyArray(),

    /**
     * Array of DES Key Versions for the requested nodes Each key version is 2 bytes in Little
     * Endian format. 0xFFFF indicates the node doesn't exist or DES key is not assigned. Empty
     * array if Status Flag1 != 0x00 or card doesn't support DES encryption
     */
    val desKeyVersions: Array<KeyVersion> = emptyArray(),
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        // If status is success, validate key versions
        if (statusFlag1 == 0x00.toByte()) {
            require(encryptionIdentifier != null) {
                "Encryption identifier must be present for successful response"
            }
            require(aesKeyVersions.isNotEmpty()) {
                "At least one AES key version must be present for successful response"
            }
            require(aesKeyVersions.size <= 32) { "Maximum 32 AES key versions can be returned" }

            // DES key versions size validation
            if (desKeyVersions.isNotEmpty()) {
                require(desKeyVersions.size == aesKeyVersions.size) {
                    "DES and AES key version arrays must have same size"
                }
            }
        } else {
            // If status is error, these fields should be empty or null
            require(encryptionIdentifier == null) {
                "Encryption identifier should be null for error response"
            }
            require(aesKeyVersions.isEmpty()) {
                "AES key versions should be empty for error response"
            }
            require(desKeyVersions.isEmpty()) {
                "DES key versions should be empty for error response"
            }
        }
    }

    /** Indicates if the response indicates success */
    val success: Boolean
        get() = statusFlag1 == 0x00.toByte()

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity =
                if (statusFlag1 != 0x00.toByte()) {
                    MIN_SUCCESS_LENGTH
                    (aesKeyVersions.size * 2) + (desKeyVersions.size * 2)
                } else {
                    MIN_ERROR_LENGTH
                },
        ) {
            addByte(statusFlag1)
            addByte(statusFlag2)

            if (statusFlag1 == 0x00.toByte()) {
                addByte(encryptionIdentifier!!.value)
                addByte(aesKeyVersions.size)
                aesKeyVersions.forEach { addBytes(it.toByteArray()) }
                if (desKeyVersions.isNotEmpty()) {
                    desKeyVersions.forEach { addBytes(it.toByteArray()) }
                }
            }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x33
        const val MIN_SUCCESS_LENGTH = BASE_LENGTH + 2 + 1 + 1
        // + status_flags(2) + enc_id(1) + num_nodes(1)
        const val MIN_ERROR_LENGTH = BASE_LENGTH + 2 // + status_flags(2)

        /** Parse a Request Service v2 response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestServiceV2Response =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_ERROR_LENGTH) { idm ->
                val statusFlag1 = byte()
                val statusFlag2 = byte()

                if (statusFlag1 != 0x00.toByte()) {
                    return RequestServiceV2Response(
                        idm,
                        statusFlag1,
                        statusFlag2,
                        null,
                        emptyArray(),
                        emptyArray(),
                    )
                }

                val encryptionIdentifierValue = uByte()
                val encryptionIdentifier =
                    EncryptionIdentifier.values().find { it.value == encryptionIdentifierValue }
                        ?: throw IllegalArgumentException(
                            "Unknown encryption identifier: 0x${encryptionIdentifierValue.toString(16)}"
                        )

                val numberOfNodes = uByte()
                require(numberOfNodes in 1..32) {
                    "Number of nodes must be between 1 and 32, got $numberOfNodes"
                }

                val expectedAesSize = numberOfNodes * 2
                val expectedDesSize =
                    if (encryptionIdentifier.desKeyType != DesKeyType.NONE) numberOfNodes * 2 else 0
                val expectedRemainingSize = expectedAesSize + expectedDesSize

                require(remaining() >= expectedRemainingSize) {
                    "Data size insufficient for $numberOfNodes nodes with encryption type ${encryptionIdentifier.name}"
                }

                val aesKeyVersions = Array(numberOfNodes) { KeyVersion(bytes(2)) }

                val desKeyVersions =
                    if (encryptionIdentifier.desKeyType != DesKeyType.NONE) {
                        Array(numberOfNodes) { KeyVersion(bytes(2)) }
                    } else {
                        emptyArray()
                    }

                RequestServiceV2Response(
                    idm,
                    statusFlag1,
                    statusFlag2,
                    encryptionIdentifier,
                    aesKeyVersions,
                    desKeyVersions,
                )
            }
    }
}
