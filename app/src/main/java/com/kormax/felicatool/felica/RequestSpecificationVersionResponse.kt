package com.kormax.felicatool.felica

/**
 * Request Specification Version response received from FeliCa cards
 *
 * Contains the card's OS version information including basic version and option versions.
 */
class RequestSpecificationVersionResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /** Status Flag1 - indicates success or error location */
    override val statusFlag1: Byte,

    /** Status Flag2 - indicates detailed error information */
    override val statusFlag2: Byte,

    /** Format Version (1 byte, fixed value 00h) Only present if statusFlag1 == 0x00 */
    val formatVersion: Byte? = null,

    /** Basic Version (2 bytes, Little Endian, BCD notation) */
    val basicVersion: OptionVersion? = null,

    /** DES option version (2 bytes, Little Endian, BCD notation) */
    val desOptionVersion: OptionVersion? = null,

    /** Reserved (2 bytes, Little Endian, BCD notation) */
    val specialOptionVersion: OptionVersion? = null,

    /** Extended Overlap option version (2 bytes, Little Endian, BCD notation) */
    val extendedOverlapOptionVersion: OptionVersion? = null,

    /** Value-Limited Purse Service option version (2 bytes, Little Endian, BCD notation) */
    val valueLimitedPurseServiceOptionVersion: OptionVersion? = null,

    /** Communication with MAC option version (2 bytes, Little Endian, BCD notation) */
    val communicationWithMacOptionVersion: OptionVersion? = null,
) : FelicaResponseWithIdm(idm), WithStatusFlags {

    init {
        // Validate option version dependencies
        require(specialOptionVersion == null || desOptionVersion != null) {
            "If specialOptionVersion is specified, desOptionVersion cannot be null"
        }
        require(
            extendedOverlapOptionVersion == null ||
                (desOptionVersion != null && specialOptionVersion != null)
        ) {
            "If extendedOverlapOptionVersion is specified, both desOptionVersion and specialOptionVersion cannot be null"
        }
        require(
            valueLimitedPurseServiceOptionVersion == null ||
                (desOptionVersion != null &&
                    specialOptionVersion != null &&
                    extendedOverlapOptionVersion != null)
        ) {
            "If valueLimitedPurseServiceOptionVersion is specified, desOptionVersion, specialOptionVersion, and extendedOverlapOptionVersion cannot be null"
        }
        require(
            communicationWithMacOptionVersion == null ||
                (desOptionVersion != null &&
                    specialOptionVersion != null &&
                    extendedOverlapOptionVersion != null &&
                    valueLimitedPurseServiceOptionVersion != null)
        ) {
            "If communicationWithMacOptionVersion is specified, all previous option versions cannot be null"
        }
    }

    /** Converts the response to a byte array */
    override fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Length (1 byte) - will be calculated at the end
        data.add(0x00) // placeholder

        // Response code (1 byte)
        data.add(RESPONSE_CODE)

        // IDM (8 bytes)
        data.addAll(idm.toList())

        // Status Flag1 (1 byte)
        data.add(statusFlag1)

        // Status Flag2 (1 byte)
        data.add(statusFlag2)

        // Specification Version fields (if successful)
        if (statusFlag1 == 0x00.toByte() && basicVersion != null && formatVersion != null) {
            // Format Version (1 byte)
            data.add(formatVersion)

            // Basic Version (2 bytes, Little Endian)
            data.addAll(basicVersion.toByteArray().toList())

            // Always 5 option slots in FeliCa specification
            val optionCount = 5
            data.add(optionCount.toByte())

            // Option Version List (2 bytes each, Little Endian) - all 5 slots
            // DES option
            desOptionVersion?.let { data.addAll(it.toByteArray().toList()) }
                ?: data.addAll(byteArrayOf(0x00, 0x00).toList())
            // Special/Reserved option
            specialOptionVersion?.let { data.addAll(it.toByteArray().toList()) }
                ?: data.addAll(byteArrayOf(0x00, 0x00).toList())
            // Extended Overlap option
            extendedOverlapOptionVersion?.let { data.addAll(it.toByteArray().toList()) }
                ?: data.addAll(byteArrayOf(0x00, 0x00).toList())
            // Value-Limited Purse Service option
            valueLimitedPurseServiceOptionVersion?.let { data.addAll(it.toByteArray().toList()) }
                ?: data.addAll(byteArrayOf(0x00, 0x00).toList())
            // Communication with MAC option
            communicationWithMacOptionVersion?.let { data.addAll(it.toByteArray().toList()) }
                ?: data.addAll(byteArrayOf(0x00, 0x00).toList())
        }

        // Update length
        val length = data.size
        data[0] = length.toByte()

        return data.toByteArray()
    }

    companion object {
        const val RESPONSE_CODE: Byte = 0x3D
        const val MIN_LENGTH = FelicaResponseWithIdm.BASE_LENGTH + 2 // + status_flags(2)

        /** Parse a Request Specification Version response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestSpecificationVersionResponse {
            require(data.size >= MIN_LENGTH) {
                "Response data too short: ${data.size} bytes, minimum $MIN_LENGTH required"
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

            // Status Flag1 (1 byte)
            val statusFlag1 = data[offset++]

            // Status Flag2 (1 byte)
            val statusFlag2 = data[offset++]

            // Parse specification version fields if successful and data remains
            var formatVersion: Byte? = null
            var basicVersion: OptionVersion? = null
            var desOptionVersion: OptionVersion? = null
            var specialOptionVersion: OptionVersion? = null
            var extendedOverlapOptionVersion: OptionVersion? = null
            var valueLimitedPurseServiceOptionVersion: OptionVersion? = null
            var communicationWithMacOptionVersion: OptionVersion? = null

            if (statusFlag1 == 0x00.toByte() && offset < data.size) {
                // Format Version (1 byte)
                formatVersion = data[offset++]

                // Basic Version (2 bytes, Little Endian)
                if (offset + 2 <= data.size) {
                    val basicVersionBytes = data.sliceArray(offset until offset + 2)
                    basicVersion = OptionVersion.fromByteArray(basicVersionBytes)
                    offset += 2
                }

                // Number of options (1 byte)
                if (offset < data.size) {
                    val numberOfOptions = data[offset++].toInt() and 0xFF

                    // Parse options based on their positions in the standard format
                    for (i in 0 until numberOfOptions) {
                        if (offset + 2 <= data.size) {
                            val optionBytes = data.sliceArray(offset until offset + 2)
                            val optionVersion = OptionVersion.fromByteArray(optionBytes)

                            // Only assign non-missing options
                            when (i) {
                                0 -> desOptionVersion = optionVersion // DES option
                                1 -> specialOptionVersion = optionVersion // Special option
                                2 ->
                                    extendedOverlapOptionVersion =
                                        optionVersion // Extended Overlap option
                                3 ->
                                    valueLimitedPurseServiceOptionVersion =
                                        optionVersion // Value-Limited Purse Service option
                                4 ->
                                    communicationWithMacOptionVersion =
                                        optionVersion // Communication with MAC option
                            }
                            offset += 2
                        }
                    }
                }
            }

            return RequestSpecificationVersionResponse(
                idm,
                statusFlag1,
                statusFlag2,
                formatVersion,
                basicVersion,
                desOptionVersion,
                specialOptionVersion,
                extendedOverlapOptionVersion,
                valueLimitedPurseServiceOptionVersion,
                communicationWithMacOptionVersion,
            )
        }
    }
}
