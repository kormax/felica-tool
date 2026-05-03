package com.kormax.felicatool.felica

/**
 * Interface representing different types of node properties that can be retrieved from FeliCa cards
 * using the Get Node Property command.
 *
 * Each implementation represents a specific type of property (Value-Limited Purse Service or
 * Communication-with-MAC-enabled Service) and provides methods to serialize/deserialize the
 * property data.
 */
interface NodeProperty {
    /**
     * Convert the property to a byte array representation
     *
     * @return The property data as bytes
     */
    fun toByteArray(): ByteArray

    /**
     * Get the size of the property in bytes
     *
     * @return The number of bytes this property occupies
     */
    val sizeBytes: Int

    /**
     * Get the type of this node property
     *
     * @return The NodePropertyType enum value
     */
    val type: NodePropertyType
}
