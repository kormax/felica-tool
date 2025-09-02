package com.kormax.felicatool.felica

/** Container information returned by Get Container Issue Information command */
data class ContainerInformation(
    /** Format version and carrier information (5 bytes) */
    val formatVersionCarrierInformation: ByteArray,

    /** Mobile phone model information (11 bytes) */
    val mobilePhoneModelInformation: ByteArray,
) {
    init {
        require(formatVersionCarrierInformation.size == 5) {
            "Format version carrier information must be exactly 5 bytes"
        }
        require(mobilePhoneModelInformation.size == 11) {
            "Mobile phone model information must be exactly 11 bytes"
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ContainerInformation

        if (!formatVersionCarrierInformation.contentEquals(other.formatVersionCarrierInformation))
            return false
        if (!mobilePhoneModelInformation.contentEquals(other.mobilePhoneModelInformation))
            return false

        return true
    }

    override fun hashCode(): Int {
        var result = formatVersionCarrierInformation.contentHashCode()
        result = 31 * result + mobilePhoneModelInformation.contentHashCode()
        return result
    }
}
