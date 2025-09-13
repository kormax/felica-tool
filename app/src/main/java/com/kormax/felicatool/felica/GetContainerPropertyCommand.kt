package com.kormax.felicatool.felica

/**
 * Get Container Property command - retrieves specific container properties
 *
 * This command retrieves container property information without requiring an IDM. The index
 * parameter specifies which property to retrieve. For devices tested so far, the following
 * properties are known:
 * - Index 0x0000 returns 0x01
 * - Index 0x0100 returns 0x20
 */
class GetContainerPropertyCommand(
    /** Property index (2 bytes, little-endian encoded) */
    val property: Property = Property.PROPERTY_1
) : FelicaCommandWithoutIdm<GetContainerPropertyResponse>() {

    /** Alternative constructor with raw index value */
    constructor(index: Short) : this(Property.fromIndex(index))

    /** Convenience property to access the index value */
    val index: Short
        get() = property.index

    override val commandClass: CommandClass = Companion.COMMAND_CLASS

    override fun responseFromByteArray(data: ByteArray) =
        GetContainerPropertyResponse.fromByteArray(data)

    override fun toByteArray(): ByteArray {
        val data = ByteArray(COMMAND_LENGTH)
        var offset = 0

        // Length (1 byte)
        data[offset++] = COMMAND_LENGTH.toByte()

        // Command code (1 byte)
        data[offset++] = COMMAND_CODE.toByte()

        // Index (2 bytes, little-endian)
        val indexBytes = property.toByteArray()
        indexBytes.copyInto(data, offset)

        return data
    }

    companion object : CommandCompanion {
        override val COMMAND_CODE: Short = 0x2E
        override val COMMAND_CLASS: CommandClass = CommandClass.OTHER

        const val COMMAND_LENGTH: Int = FelicaCommandWithoutIdm.BASE_LENGTH + 2 // + index(2)

        /** Parse a Get Container Property command from raw bytes */
        fun fromByteArray(data: ByteArray): GetContainerPropertyCommand {
            require(data.size == COMMAND_LENGTH) {
                "Command data must be exactly $COMMAND_LENGTH bytes, got ${data.size}"
            }

            var offset = 0

            // Length (1 byte)
            val length = data[offset].toInt() and 0xFF
            require(length == data.size) { "Length mismatch: expected $length, got ${data.size}" }
            offset++

            // Command code (1 byte)
            val commandCode = data[offset]
            require(commandCode == COMMAND_CODE.toByte()) {
                "Invalid command code: expected $COMMAND_CODE, got $commandCode"
            }
            offset++

            // Index (2 bytes, little-endian)
            val property = Property.fromByteArray(data.sliceArray(offset until offset + 2))

            return GetContainerPropertyCommand(property)
        }
    }

    /**
     * Container Property enumeration for GetContainerProperty command
     *
     * Represents different property types that can be queried from the container.
     */
    sealed class Property(val index: Short) {
        val name: String
            get() = (this::class.simpleName ?: "Unknown").uppercase()

        /** Property 1 (index 0x00) - returns 0x01 */
        object PROPERTY_1 : Property(0x00)

        /** Property 2 (index 0x01) - returns 0x20 */
        object PROPERTY_2 : Property(0x01)

        /** Unknown property for other index values */
        data class UNKNOWN(val value: Short) : Property(value)

        fun toByteArray(): ByteArray =
            byteArrayOf(
                (index.toInt() and 0xFF).toByte(),
                ((index.toInt() shr 8) and 0xFF).toByte(),
            )

        companion object {
            /** Get Property from index value */
            fun fromIndex(index: Short): Property {
                return when (index) {
                    0x00.toShort() -> PROPERTY_1
                    0x01.toShort() -> PROPERTY_2
                    else -> UNKNOWN(index)
                }
            }

            fun fromByteArray(data: ByteArray): Property {
                require(data.size == 2) { "Index data must be exactly 2 bytes" }
                val index =
                    ((data[1].toInt() and 0xFF) shl 8 or (data[0].toInt() and 0xFF)).toShort()
                return fromIndex(index)
            }
        }
    }
}
