package com.kormax.felicatool.felica

/**
 * Data class representing the specification version information returned by Request Specification
 * Version command
 */
data class SpecificationVersion(
    /** Format Version (1 byte, fixed value 00h) */
    val formatVersion: Byte = 0x00,

    /** Basic Version (2 bytes, Little Endian, BCD notation) */
    val basicVersion: OptionVersion,

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
) {
    init {
        require(formatVersion == 0x00.toByte()) { "Format version must be 00h" }
    }

    companion object {
        /** Parse SpecificationVersion from raw bytes */
        fun fromByteArray(data: ByteArray, offset: Int = 0): SpecificationVersion {
            require(data.size >= offset + 1 + 2 + 1) {
                "Data too short for SpecificationVersion header"
            }

            var currentOffset = offset

            // Format Version (1 byte)
            val formatVersion = data[currentOffset++]

            // Basic Version (2 bytes, Little Endian)
            val basicVersionBytes = data.sliceArray(currentOffset until currentOffset + 2)
            val basicVersion = OptionVersion.fromByteArray(basicVersionBytes)
            currentOffset += 2

            // Number of options (1 byte)
            val numberOfOptions = data[currentOffset++].toInt() and 0xFF

            // Check if we have enough data for the options
            require(data.size >= currentOffset + numberOfOptions * 2) {
                "Data too short for $numberOfOptions options"
            }

            // Parse options based on their positions in the standard format
            var desOptionVersion: OptionVersion? = null
            var specialOptionVersion: OptionVersion? = null
            var extendedOverlapOptionVersion: OptionVersion? = null
            var valueLimitedPurseServiceOptionVersion: OptionVersion? = null
            var communicationWithMacOptionVersion: OptionVersion? = null

            for (i in 0 until numberOfOptions) {
                if (currentOffset + 2 <= data.size) {
                    val optionBytes = data.sliceArray(currentOffset until currentOffset + 2)
                    val optionVersion = OptionVersion.fromByteArray(optionBytes)

                    // Assign all option versions (0.0.0 is valid)
                    when (i) {
                        0 -> desOptionVersion = optionVersion // DES option
                        1 -> specialOptionVersion = optionVersion // Special option
                        2 -> extendedOverlapOptionVersion = optionVersion // Extended Overlap option
                        3 ->
                            valueLimitedPurseServiceOptionVersion =
                                optionVersion // Value-Limited Purse Service option
                        4 ->
                            communicationWithMacOptionVersion =
                                optionVersion // Communication with MAC option
                    // Additional options can be added here if needed
                    }
                    currentOffset += 2
                }
            }

            return SpecificationVersion(
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

    /** Convert to byte array */
    fun toByteArray(): ByteArray {
        val data = mutableListOf<Byte>()

        // Format Version (1 byte)
        data.add(formatVersion)

        // Basic Version (2 bytes, Little Endian)
        data.addAll(basicVersion.toByteArray().toList())

        // Always 5 option slots in FeliCa specification
        val optionCount = 5

        // Number of options (1 byte) - always 5
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

        return data.toByteArray()
    }
}
