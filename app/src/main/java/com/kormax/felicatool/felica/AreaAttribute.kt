package com.kormax.felicatool.felica

/** Represents an Area Attribute in the FeliCa file system. */
sealed class AreaAttribute(
    open val value: Int,
    open val canCreateSubArea: Boolean,
    open val pinRequired: Boolean,
) {
    open val isUnknown: Boolean
        get() = false

    data object CanCreateSubArea :
        AreaAttribute(0b000000, true, false) // Area that can create Sub-Area

    data object CannotCreateSubArea :
        AreaAttribute(0b000001, false, false) // Area that cannot create Sub-Area

    data object CanCreateSubAreaWithPin :
        AreaAttribute(0b100000, true, true) // Area that can create Sub-Area with PIN access

    data object CannotCreateSubAreaWithPin :
        AreaAttribute(0b100001, false, true) // Area that cannot create Sub-Area with PIN access

    // First bit for end code of root area is 0
    data object EndRootArea : AreaAttribute(0b111110, false, false)

    data object EndSubArea : AreaAttribute(0b111111, false, false)

    /** Captures an attribute value that is not yet recognized by this client. */
    data class Unknown(override val value: Int) : AreaAttribute(value, false, false) {
        override val isUnknown: Boolean
            get() = true
    }

    companion object {
        val CAN_CREATE_SUB_AREA = CanCreateSubArea
        val CANNOT_CREATE_SUB_AREA = CannotCreateSubArea
        val CAN_CREATE_SUB_AREA_WITH_PIN = CanCreateSubAreaWithPin
        val CANNOT_CREATE_SUB_AREA_WITH_PIN = CannotCreateSubAreaWithPin
        val END_ROOT_AREA = EndRootArea
        val END_SUB_AREA = EndSubArea

        fun fromValue(value: Int): AreaAttribute =
            when (value) {
                CAN_CREATE_SUB_AREA.value -> CAN_CREATE_SUB_AREA
                CANNOT_CREATE_SUB_AREA.value -> CANNOT_CREATE_SUB_AREA
                CAN_CREATE_SUB_AREA_WITH_PIN.value -> CAN_CREATE_SUB_AREA_WITH_PIN
                CANNOT_CREATE_SUB_AREA_WITH_PIN.value -> CANNOT_CREATE_SUB_AREA_WITH_PIN
                END_ROOT_AREA.value -> END_ROOT_AREA
                END_SUB_AREA.value -> END_SUB_AREA
                else -> Unknown(value)
            }
    }
}
