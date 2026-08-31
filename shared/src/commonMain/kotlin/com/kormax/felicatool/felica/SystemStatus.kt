package com.kormax.felicatool.felica

sealed interface SystemStatus {
    val formatVersion: Byte

    fun toByteArray(): ByteArray

    data class V0(
        val desSystemInitializationCommandsDisabled: Boolean,
        val desNodeIssuanceCommandsDisabled: Boolean,
        val rfuD0Bit2: Boolean,
        val desAuthenticationStrictAreaListValidation: Boolean,
        val rfuD0Bit4: Boolean,
        val rfuD0Bit5: Boolean,
        val rfuD0Bit6: Boolean,
        val rfuD0Bit7: Boolean,

        /**
         * Set on observed Cyberne-like systems, including System Code 0003 and SAPICA (865E). Its
         * exact meaning is unknown.
         */
        val unknownFlag: Boolean,
        val rfuD1Bit1: Boolean,
        val rfuD1Bit2: Boolean,
        val rfuD1Bit3: Boolean,
        val rfuD1Bit4: Boolean,
        val rfuD1Bit5: Boolean,
        val rfuD1Bit6: Boolean,
        val rfuD1Bit7: Boolean,
        val extra: ByteArray = byteArrayOf(),
    ) : SystemStatus {
        override val formatVersion: Byte
            get() = FORMAT_VERSION

        override fun toByteArray(): ByteArray =
            byteArrayOf(
                formatVersion,
                encodeD0(),
                encodeD1(),
            ) + extra

        override fun equals(other: Any?): Boolean =
            this === other || (other is V0 && toByteArray().contentEquals(other.toByteArray()))

        override fun hashCode(): Int = toByteArray().contentHashCode()

        private fun encodeD0(): Byte {
            var value = 0

            if (desSystemInitializationCommandsDisabled) value = value or 0x01
            if (desNodeIssuanceCommandsDisabled) value = value or 0x02
            if (rfuD0Bit2) value = value or 0x04
            if (!desAuthenticationStrictAreaListValidation) value = value or 0x08
            if (rfuD0Bit4) value = value or 0x10
            if (rfuD0Bit5) value = value or 0x20
            if (rfuD0Bit6) value = value or 0x40
            if (rfuD0Bit7) value = value or 0x80

            return value.toByte()
        }

        private fun encodeD1(): Byte {
            var value = 0

            if (unknownFlag) value = value or 0x01
            if (rfuD1Bit1) value = value or 0x02
            if (rfuD1Bit2) value = value or 0x04
            if (rfuD1Bit3) value = value or 0x08
            if (rfuD1Bit4) value = value or 0x10
            if (rfuD1Bit5) value = value or 0x20
            if (rfuD1Bit6) value = value or 0x40
            if (rfuD1Bit7) value = value or 0x80

            return value.toByte()
        }

        companion object {
            const val FORMAT_VERSION: Byte = 0x00

            fun fromByteArray(bytes: ByteArray): V0 {
                require(bytes.size >= 3) {
                    "System Status format version 0 requires at least 3 bytes"
                }
                require(bytes[0] == FORMAT_VERSION) {
                    "Expected System Status format version 0"
                }

                val d0 = bytes[1].toInt() and 0xFF
                val d1 = bytes[2].toInt() and 0xFF

                return V0(
                    desSystemInitializationCommandsDisabled = d0 and 0x01 != 0,
                    desNodeIssuanceCommandsDisabled = d0 and 0x02 != 0,
                    rfuD0Bit2 = d0 and 0x04 != 0,
                    desAuthenticationStrictAreaListValidation = d0 and 0x08 == 0,
                    rfuD0Bit4 = d0 and 0x10 != 0,
                    rfuD0Bit5 = d0 and 0x20 != 0,
                    rfuD0Bit6 = d0 and 0x40 != 0,
                    rfuD0Bit7 = d0 and 0x80 != 0,
                    unknownFlag = d1 and 0x01 != 0,
                    rfuD1Bit1 = d1 and 0x02 != 0,
                    rfuD1Bit2 = d1 and 0x04 != 0,
                    rfuD1Bit3 = d1 and 0x08 != 0,
                    rfuD1Bit4 = d1 and 0x10 != 0,
                    rfuD1Bit5 = d1 and 0x20 != 0,
                    rfuD1Bit6 = d1 and 0x40 != 0,
                    rfuD1Bit7 = d1 and 0x80 != 0,
                    extra = bytes.copyOfRange(3, bytes.size),
                )
            }
        }
    }

    data class UnsupportedVersion(
        override val formatVersion: Byte,
        val unknownData: ByteArray,
    ) : SystemStatus {
        override fun toByteArray(): ByteArray = byteArrayOf(formatVersion) + unknownData

        override fun equals(other: Any?): Boolean =
            this === other ||
                (other is UnsupportedVersion && toByteArray().contentEquals(other.toByteArray()))

        override fun hashCode(): Int = toByteArray().contentHashCode()
    }

    companion object {
        fun fromByteArray(bytes: ByteArray): SystemStatus {
            require(bytes.isNotEmpty()) { "System Status must contain a format version" }

            return when (bytes[0]) {
                V0.FORMAT_VERSION -> V0.fromByteArray(bytes)
                else ->
                    UnsupportedVersion(
                        formatVersion = bytes[0],
                        unknownData = bytes.copyOfRange(1, bytes.size),
                    )
            }
        }
    }
}
