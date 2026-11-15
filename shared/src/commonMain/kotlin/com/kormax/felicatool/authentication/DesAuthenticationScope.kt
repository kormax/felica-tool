package com.kormax.felicatool.authentication

data class DesAuthenticationScope(
    val systemCode: String,
    val idm: String,
    val areaCodes: List<String>,
    val nodeCodes: List<String>,
) {
    init {
        require(areaCodes.isNotEmpty() || nodeCodes.isNotEmpty()) {
            "At least one area or node is required"
        }
    }
}
