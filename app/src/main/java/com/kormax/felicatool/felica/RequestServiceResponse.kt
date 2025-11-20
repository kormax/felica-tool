package com.kormax.felicatool.felica

/**
 * Request Service response received from FeliCa cards
 *
 * Contains the Key Versions for the requested Node Codes. If a Node doesn't exist, the key version
 * will be 0xFFFF.
 */
class RequestServiceResponse(
    /** The card's IDM (8 bytes) - unique identifier */
    idm: ByteArray,

    /**
     * Array of Key Versions for the requested nodes Each key version is 2 bytes in Little Endian
     * format. 0xFFFF indicates the node doesn't exist.
     */
    val keyVersions: Array<KeyVersion>,
) : FelicaResponseWithIdm(idm) {

    init {
        require(keyVersions.isNotEmpty()) { "At least one key version must be present" }
        require(keyVersions.size <= 32) { "Maximum 32 key versions can be returned" }
    }

    override fun toByteArray(): ByteArray =
        buildFelicaMessage(
            RESPONSE_CODE,
            idm,
            capacity = BASE_LENGTH + 1 + (keyVersions.size * 2),
        ) {
            addByte(keyVersions.size)
            keyVersions.forEach { addBytes(it.toByteArray()) }
        }

    companion object {
        const val RESPONSE_CODE: Short = 0x03
        const val MIN_LENGTH = BASE_LENGTH + 1 + 2 // + number_of_nodes(1) + min 1 key_version(2)

        /** Parse a Request Service response from raw bytes */
        fun fromByteArray(data: ByteArray): RequestServiceResponse =
            parseFelicaResponseWithIdm(data, RESPONSE_CODE, minLength = MIN_LENGTH) { idm ->
                val numberOfNodes = uByte()
                require(numberOfNodes in 1..32) {
                    "Number of nodes must be between 1 and 32, got $numberOfNodes"
                }
                require(remaining() >= numberOfNodes * 2) {
                    "Data size insufficient for $numberOfNodes key versions"
                }

                val keyVersions = Array(numberOfNodes) { KeyVersion(bytes(2)) }

                RequestServiceResponse(idm, keyVersions)
            }
    }
}
