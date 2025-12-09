package com.kormax.felicatool.felica

import com.kormax.felicatool.annotation.SealedEnum

/** Represents an Area Attribute in the FeliCa file system. */
@SealedEnum
sealed class AreaAttribute(
    open val value: Int,
    open val canCreateSubArea: Boolean,
    open val pinRequired: Boolean,
) {
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
    data class Unknown(override val value: Int) : AreaAttribute(value, false, false)

    companion object
}
