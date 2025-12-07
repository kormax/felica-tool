package com.kormax.felicatool.felica

/** Represents a Service Attribute in the FeliCa file system. */
sealed class ServiceAttribute(
    open val value: Int,
    open val type: ServiceType,
    open val mode: ServiceMode,
    open val authenticationRequired: Boolean,
    open val pinRequired: Boolean,
) {
    init {
        if (this !is Unknown) register(this)
    }

    open val isUnknown: Boolean
        get() = false

    data object RandomRwWithKey :
        ServiceAttribute(0b001000, ServiceType.RANDOM, ServiceMode.READ_WRITE, true, false)

    data object RandomRwWithoutKey :
        ServiceAttribute(0b001001, ServiceType.RANDOM, ServiceMode.READ_WRITE, false, false)

    data object RandomRoWithKey :
        ServiceAttribute(0b001010, ServiceType.RANDOM, ServiceMode.READ_ONLY, true, false)

    data object RandomRoWithoutKey :
        ServiceAttribute(0b001011, ServiceType.RANDOM, ServiceMode.READ_ONLY, false, false)

    data object CyclicRwWithKey :
        ServiceAttribute(0b001100, ServiceType.CYCLIC, ServiceMode.READ_WRITE, true, false)

    data object CyclicRwWithoutKey :
        ServiceAttribute(0b001101, ServiceType.CYCLIC, ServiceMode.READ_WRITE, false, false)

    data object CyclicRoWithKey :
        ServiceAttribute(0b001110, ServiceType.CYCLIC, ServiceMode.READ_ONLY, true, false)

    data object CyclicRoWithoutKey :
        ServiceAttribute(0b001111, ServiceType.CYCLIC, ServiceMode.READ_ONLY, false, false)

    data object PurseRwWithKey :
        ServiceAttribute(0b010000, ServiceType.PURSE, ServiceMode.READ_WRITE, true, false)

    data object PurseRwWithoutKey :
        ServiceAttribute(0b010001, ServiceType.PURSE, ServiceMode.READ_WRITE, false, false)

    data object PurseCashbackWithKey :
        ServiceAttribute(0b010010, ServiceType.PURSE, ServiceMode.CASHBACK, true, false)

    data object PurseCashbackWithoutKey :
        ServiceAttribute(0b010011, ServiceType.PURSE, ServiceMode.CASHBACK, false, false)

    data object PurseDecrementWithKey :
        ServiceAttribute(0b010100, ServiceType.PURSE, ServiceMode.DECREMENT, true, false)

    data object PurseDecrementWithoutKey :
        ServiceAttribute(0b010101, ServiceType.PURSE, ServiceMode.DECREMENT, false, false)

    data object PurseRoWithKey :
        ServiceAttribute(0b010110, ServiceType.PURSE, ServiceMode.READ_ONLY, true, false)

    data object PurseRoWithoutKey :
        ServiceAttribute(0b010111, ServiceType.PURSE, ServiceMode.READ_ONLY, false, false)

    data object RandomRwWithKeyWithPin :
        ServiceAttribute(0b101000, ServiceType.RANDOM, ServiceMode.READ_WRITE, true, true)

    data object RandomRwWithoutKeyWithPin :
        ServiceAttribute(0b101001, ServiceType.RANDOM, ServiceMode.READ_WRITE, false, true)

    data object RandomRoWithKeyWithPin :
        ServiceAttribute(0b101010, ServiceType.RANDOM, ServiceMode.READ_ONLY, true, true)

    data object RandomRoWithoutKeyWithPin :
        ServiceAttribute(0b101011, ServiceType.RANDOM, ServiceMode.READ_ONLY, false, true)

    data object CyclicRwWithKeyWithPin :
        ServiceAttribute(0b101100, ServiceType.CYCLIC, ServiceMode.READ_WRITE, true, true)

    data object CyclicRwWithoutKeyWithPin :
        ServiceAttribute(0b101101, ServiceType.CYCLIC, ServiceMode.READ_WRITE, false, true)

    data object CyclicRoWithKeyWithPin :
        ServiceAttribute(0b101110, ServiceType.CYCLIC, ServiceMode.READ_ONLY, true, true)

    data object CyclicRoWithoutKeyWithPin :
        ServiceAttribute(0b101111, ServiceType.CYCLIC, ServiceMode.READ_ONLY, false, true)

    data object PurseRwWithKeyWithPin :
        ServiceAttribute(0b110000, ServiceType.PURSE, ServiceMode.READ_WRITE, true, true)

    data object PurseRwWithoutKeyWithPin :
        ServiceAttribute(0b110001, ServiceType.PURSE, ServiceMode.READ_WRITE, false, true)

    data object PurseCashbackWithKeyWithPin :
        ServiceAttribute(0b110010, ServiceType.PURSE, ServiceMode.CASHBACK, true, true)

    data object PurseCashbackWithoutKeyWithPin :
        ServiceAttribute(0b110011, ServiceType.PURSE, ServiceMode.CASHBACK, false, true)

    data object PurseDecrementWithKeyWithPin :
        ServiceAttribute(0b110100, ServiceType.PURSE, ServiceMode.DECREMENT, true, true)

    data object PurseDecrementWithoutKeyWithPin :
        ServiceAttribute(0b110101, ServiceType.PURSE, ServiceMode.DECREMENT, false, true)

    data object PurseRoWithKeyWithPin :
        ServiceAttribute(0b110110, ServiceType.PURSE, ServiceMode.READ_ONLY, true, true)

    data object PurseRoWithoutKeyWithPin :
        ServiceAttribute(0b110111, ServiceType.PURSE, ServiceMode.READ_ONLY, false, true)

    /** Captures an attribute value that is not yet recognized by this client. */
    data class Unknown(override val value: Int) :
        ServiceAttribute(value, ServiceType.UNKNOWN, ServiceMode.UNKNOWN, false, false) {
        override val isUnknown: Boolean
            get() = true
    }

    companion object {
        private val knownByValue = mutableMapOf<Int, ServiceAttribute>()

        private fun register(attr: ServiceAttribute) {
            knownByValue[attr.value] = attr
        }

        fun fromValue(value: Int): ServiceAttribute = knownByValue[value] ?: Unknown(value)
    }
}
