package com.kormax.felicatool.authentication

import com.kormax.felicatool.felica.Authentication1Des

enum class AuthenticationListKind {
    AREA,
    NODE,
}

enum class AuthenticationListPosition {
    TRAILING,
    LEADING,
    SOLE,
}

data class AuthenticationPlacement(
    val list: AuthenticationListKind,
    val nodeKind: AuthenticationNodeKind,
    val position: AuthenticationListPosition,
)

data class DesAuthenticationBehavior(
    val supported: Boolean,
    val areaListWithoutRootSupported: Boolean = false,
    val areaListWithoutImmediateParentSupported: Boolean = false,
    val supportedPlacements: Set<AuthenticationPlacement> = emptySet(),
) {
    fun rejectionReason(scope: DesAuthenticationScope, profile: SystemProfile): String? {
        if (!supported) return "baseline Authentication1 DES is unsupported"
        if (scope.systemCode != profile.systemCode) return "system code does not match the card"
        if (scope.idm != profile.idm) return "IDm does not match the card"
        if (scope.areaCodes.isEmpty()) return "area list is empty"
        if (scope.nodeCodes.isEmpty()) return "node list is empty"
        if (scope.areaCodes.size + scope.nodeCodes.size > Authentication1Des.Command.MAX_NODES) {
            return "scope exceeds ${Authentication1Des.Command.MAX_NODES} codes"
        }

        if (profile.rootAreaCode !in scope.areaCodes && !areaListWithoutRootSupported) {
            return "area list omits root ${profile.rootAreaCode}"
        }

        for (nodeCode in scope.nodeCodes) {
            val node =
                profile.node(nodeCode) ?: return "node-list code $nodeCode was not discovered"
            val immediateParent = node.areaPath.lastOrNull() ?: profile.rootAreaCode
            if (
                immediateParent !in scope.areaCodes &&
                    immediateParent != profile.rootAreaCode &&
                    !areaListWithoutImmediateParentSupported
            ) {
                return "node $nodeCode omits immediate parent $immediateParent"
            }
        }

        unusualEntryRejection(scope.areaCodes, AuthenticationListKind.AREA, profile)?.let {
            return it
        }
        unusualEntryRejection(scope.nodeCodes, AuthenticationListKind.NODE, profile)?.let {
            return it
        }
        return null
    }

    private fun unusualEntryRejection(
        entries: List<String>,
        listKind: AuthenticationListKind,
        profile: SystemProfile,
    ): String? {
        entries.forEachIndexed { index, code ->
            val node =
                profile.node(code) ?: return "${listKind.label()} code $code was not discovered"
            if (node.kind == AuthenticationNodeKind.AREA) return@forEachIndexed
            if (
                listKind == AuthenticationListKind.NODE &&
                    node.kind == AuthenticationNodeKind.AUTHENTICATION_REQUIRED_SERVICE
            ) {
                return@forEachIndexed
            }
            if (
                AuthenticationPlacement(listKind, node.kind, AuthenticationListPosition.LEADING) in
                    supportedPlacements
            ) {
                return@forEachIndexed
            }
            val missing =
                entries.requiredPositionsAt(index).filter { position ->
                    AuthenticationPlacement(listKind, node.kind, position) !in supportedPlacements
                }
            if (missing.isNotEmpty()) {
                return "${listKind.label()} code $code (${node.kind}) at ${index + 1}/${entries.size} " +
                    "requires ${missing.joinToString(" and ") { it.name.lowercase() }} placement support"
            }
        }
        return null
    }

    private fun List<String>.requiredPositionsAt(index: Int): Set<AuthenticationListPosition> =
        when {
            size == 1 -> setOf(AuthenticationListPosition.SOLE)
            index == 0 -> setOf(AuthenticationListPosition.LEADING)
            index == lastIndex -> setOf(AuthenticationListPosition.TRAILING)
            else -> setOf(AuthenticationListPosition.LEADING, AuthenticationListPosition.TRAILING)
        }

    private fun AuthenticationListKind.label(): String =
        name.lowercase().replaceFirstChar(Char::uppercase)
}
