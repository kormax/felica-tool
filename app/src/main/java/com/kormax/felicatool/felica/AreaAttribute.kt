package com.kormax.felicatool.felica

/** Enum representing the Area Attribute in the FeliCa file system. */
enum class AreaAttribute(val value: Int, val canCreateSubArea: Boolean, val pinRequired: Boolean) {
    CAN_CREATE_SUB_AREA(0b000000, true, false), // Area that can create Sub-Area
    CANNOT_CREATE_SUB_AREA(0b000001, false, false), // Area that cannot create Sub-Area
    CAN_CREATE_SUB_AREA_WITH_PIN(
        0b100000,
        true,
        true,
    ), // Area that can create Sub-Area with PIN access
    CANNOT_CREATE_SUB_AREA_WITH_PIN(
        0b100001,
        false,
        true,
    ), // Area that cannot create Sub-Area with PIN access

    // First bit for end code of root area is 0
    END_ROOT_AREA(0b111110, false, false),
    END_SUB_AREA(0b111111, false, false),
}
