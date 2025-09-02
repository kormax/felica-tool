package com.kormax.felicatool.felica

/** Enum representing encryption identifiers in the FeliCa system. */
enum class EncryptionIdentifier(
    val value: Int,
    val aesKeyType: AesKeyType,
    val desKeyType: DesKeyType,
) {
    AES128(0x4f, AesKeyType.AES128, DesKeyType.NONE), // 0b01001111
    AES128_DES112(0x43, AesKeyType.AES128, DesKeyType.DES112), // 0b01000011
    AES128_DES56(0x41, AesKeyType.AES128, DesKeyType.DES56), // 0b01000001
    DES112(0x3f, AesKeyType.NONE, DesKeyType.DES112), // 0b00111111
    DES56(0x2f, AesKeyType.NONE, DesKeyType.DES56), // 0b00101111
}
