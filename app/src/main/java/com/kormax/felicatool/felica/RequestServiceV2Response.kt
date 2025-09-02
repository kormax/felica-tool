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

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val baseLength = FelicaResponseWithIdm.BASE_LENGTH + 2 // + status flags

        // For error responses, only include base data and status flags
        if (statusFlag1 != 0x00.toByte()) {
            val data = ByteArray(baseLength)
            var offset = 0

            data[offset++] = baseLength.toByte()
            data[offset++] = RESPONSE_CODE
            idm.copyInto(data, offset)
            offset += 8
            data[offset++] = statusFlag1
            data[offset++] = statusFlag2

            return data
        }

        // Success response
        val numberOfNodes = aesKeyVersions.size
        val hasDesKeys = desKeyVersions.isNotEmpty()
        val totalLength =
            baseLength + 1 + 1 + (numberOfNodes * 2) + if (hasDesKeys) (numberOfNodes * 2) else 0

        val data = ByteArray(totalLength)
        var offset = 0

        // Length (1 byte)
        data[offset++] = totalLength.toByte()

        // Response code (1 byte)
        data[offset++] = RESPONSE_CODE

        // IDM (8 bytes)
        idm.copyInto(data, offset)
        offset += 8

        // Status flags (2 bytes)
        data[offset++] = statusFlag1
        data[offset++] = statusFlag2

        // Encryption identifier (1 byte)
        data[offset++] = encryptionIdentifier!!.value.toByte()

        // Number of nodes (1 byte)
        data[offset++] = numberOfNodes.toByte()

        // AES key versions (2 bytes each, Little Endian)
        aesKeyVersions.forEach { keyVersion ->
            keyVersion.toByteArray().copyInto(data, offset)
            offset += 2
        }

        // DES key versions (2 bytes each, Little Endian) - only if present
        if (hasDesKeys) {
            desKeyVersions.forEach { keyVersion ->
                keyVersion.toByteArray().copyInto(data, offset)
                offset += 2
            }
        }

        return data
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x33
        const val MIN_SUCCESS_LENGTH =
            FelicaResponseWithIdm.BASE_LENGTH +
                2 +
                1 +
                1 +
                2 // + status_flags(2) + enc_id(1) + num_nodes(1) + min 1 aes_key_version(2)
        const val MIN_ERROR_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 2 // + status_flags(2)

        /** Parse a Request Service v2 response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestServiceV2Response {
            require(data.size >= MIN_ERROR_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_ERROR_LENGTH required"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Response code (1 byte)
            val responseCode = data[offset]
            require(responseCode == RESPONSE_CODE) {
                "Invalid response code: expected $RESPONSE_CODE, got $responseCode"
            }
            offset++

            // IDM (8 bytes)
            val idm = data.sliceArray(offset until offset + 8)
            offset += 8

            // Status flags (2 bytes)
            val statusFlag1 = data[offset++]
            val statusFlag2 = data[offset++]

            // If status indicates error, return early
            if (statusFlag1 != 0x00.toByte()) {
                return RequestServiceV2Response(
                    idm,
                    statusFlag1,
                    statusFlag2,
                    null,
                    emptyArray<KeyVersion>(),
                    emptyArray<KeyVersion>(),
                )
            }

            // Success case - parse remaining fields
            require(data.size >= MIN_SUCCESS_LENGTH) {
                "Success response data too short: ${data.size} bytes, minimum $MIN_SUCCESS_LENGTH required"
            }

            // Encryption identifier (1 byte)
            val encryptionIdentifierValue = data[offset++].toInt() and 0xFF
            val encryptionIdentifier =
                EncryptionIdentifier.values().find { it.value == encryptionIdentifierValue }
                    ?: throw IllegalArgumentException(
                        "Unknown encryption identifier: 0x${encryptionIdentifierValue.toUByte().toString(16)}"
                    )

            // Number of nodes (1 byte)
            val numberOfNodes = data[offset++].toInt() and 0xFF
            require(numberOfNodes in 1..32) {
                "Number of nodes must be between 1 and 32, got $numberOfNodes"
            }

            // Calculate expected remaining data size
            val expectedAesSize = numberOfNodes * 2
            val expectedDesSize =
                if (encryptionIdentifier.desKeyType != DesKeyType.NONE) numberOfNodes * 2 else 0
            val expectedRemainingSize = expectedAesSize + expectedDesSize

            require(data.size >= offset + expectedRemainingSize) {
                "Data size insufficient for $numberOfNodes nodes with encryption type ${encryptionIdentifier.name}"
            }

            // AES key versions (2 bytes each, Little Endian)
            val aesKeyVersions = mutableListOf<KeyVersion>()
            repeat(numberOfNodes) {
                val keyVersionBytes = data.sliceArray(offset until offset + 2)
                aesKeyVersions.add(KeyVersion(keyVersionBytes))
                offset += 2
            }

            // DES key versions (2 bytes each, Little Endian) - only if supported
            val desKeyVersions =
                if (encryptionIdentifier.desKeyType != DesKeyType.NONE) {
                    val desVersions = mutableListOf<KeyVersion>()
                    repeat(numberOfNodes) {
                        val keyVersionBytes = data.sliceArray(offset until offset + 2)
                        desVersions.add(KeyVersion(keyVersionBytes))
                        offset += 2
                    }
                    desVersions.toTypedArray()
                } else {
                    emptyArray()
                }

            return RequestServiceV2Response(
                idm,
                statusFlag1,
                statusFlag2,
                encryptionIdentifier,
                aesKeyVersions.toTypedArray(),
                desKeyVersions,
            )
        }
    }
}
