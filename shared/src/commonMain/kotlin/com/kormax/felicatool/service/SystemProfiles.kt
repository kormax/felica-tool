package com.kormax.felicatool.service

import com.kormax.felicatool.authentication.AuthenticationListKind
import com.kormax.felicatool.authentication.AuthenticationListPosition
import com.kormax.felicatool.authentication.AuthenticationNodeKind
import com.kormax.felicatool.authentication.AuthenticationPlacement
import com.kormax.felicatool.authentication.DesAuthenticationBehavior
import com.kormax.felicatool.authentication.NodeProfile
import com.kormax.felicatool.authentication.SystemProfile
import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.felica.System
import com.kormax.felicatool.felica.areaPath

internal fun CardScanContext.systemProfiles(
    behavior: DesAuthenticationBehavior = scanAuthenticationBehavior(),
): List<SystemProfile> = systemScanContexts.mapNotNull { systemContext ->
    val systemCode = systemContext.systemCode ?: return@mapNotNull null
    val idm = systemContext.idm ?: return@mapNotNull null
    systemContext.toSystemProfile(systemCode, idm, behavior)
}

internal fun SystemScanContext.toSystemProfile(
    systemCode: ByteArray,
    systemIdm: ByteArray,
    behavior: DesAuthenticationBehavior,
): SystemProfile? {
    val root = nodes.filterIsInstance<Area>().firstOrNull(Area::isRoot) ?: Area.ROOT
    val nodeProfiles =
        (listOf<Node>(System, root) + nodes)
            .distinctBy { it.code.toList() }
            .map { node ->
                NodeProfile(
                    code = node.code.toHexString().uppercase(),
                    kind = node.authenticationKind(),
                    keyVersion = desKeyVersion(node),
                    areaPath =
                        authenticationAreaPath(node, root).map {
                            it.code.toHexString().uppercase()
                        },
                )
            }
    if (nodeProfiles.none { it.keyVersion != null }) return null
    return SystemProfile(
        systemCode = systemCode.toHexString().uppercase(),
        idm = systemIdm.toHexString().uppercase(),
        rootAreaCode = root.code.toHexString().uppercase(),
        nodes = nodeProfiles,
        behavior = behavior,
    )
}

private fun SystemScanContext.desKeyVersion(node: Node) =
    (nodeDesKeyVersions[node] ?: nodeKeyVersions[node]?.takeIf { node !in nodeAesKeyVersions })
        ?.takeUnless { it.isMissing }

private fun Node.authenticationKind(): AuthenticationNodeKind =
    when (this) {
        is System -> AuthenticationNodeKind.SYSTEM
        is Area -> AuthenticationNodeKind.AREA
        is Service ->
            if (attribute.authenticationRequired) {
                AuthenticationNodeKind.AUTHENTICATION_REQUIRED_SERVICE
            } else {
                AuthenticationNodeKind.AUTHENTICATION_NOT_REQUIRED_SERVICE
            }
        else -> AuthenticationNodeKind.AUTHENTICATION_NOT_REQUIRED_SERVICE
    }

private fun SystemScanContext.authenticationAreaPath(node: Node, root: Area): List<Area> {
    if (node is System || (node is Area && node.isRoot)) return listOf(root)
    return node.areaPath(nodes.filterIsInstance<Area>() + root).ifEmpty { listOf(root) }
}

internal fun CardScanContext.scanAuthenticationBehavior(): DesAuthenticationBehavior {
    val capability = commands.authentication1Des
    val placements = mutableSetOf<AuthenticationPlacement>()

    fun add(
        support: CommandSupport,
        list: AuthenticationListKind,
        kind: AuthenticationNodeKind,
        position: AuthenticationListPosition,
    ) {
        if (support == CommandSupport.SUPPORTED) {
            placements += AuthenticationPlacement(list, kind, position)
        }
    }

    add(
        capability.leadingSystemNodeInAreaListSupported,
        AuthenticationListKind.AREA,
        AuthenticationNodeKind.SYSTEM,
        AuthenticationListPosition.LEADING,
    )
    add(
        capability.trailingAuthenticationRequiredServiceInAreaListSupported,
        AuthenticationListKind.AREA,
        AuthenticationNodeKind.AUTHENTICATION_REQUIRED_SERVICE,
        AuthenticationListPosition.TRAILING,
    )
    add(
        capability.leadingAuthenticationRequiredServiceInAreaListSupported,
        AuthenticationListKind.AREA,
        AuthenticationNodeKind.AUTHENTICATION_REQUIRED_SERVICE,
        AuthenticationListPosition.LEADING,
    )
    add(
        capability.leadingAuthenticationNotRequiredServiceInNodeListSupported,
        AuthenticationListKind.NODE,
        AuthenticationNodeKind.AUTHENTICATION_NOT_REQUIRED_SERVICE,
        AuthenticationListPosition.LEADING,
    )
    return DesAuthenticationBehavior(
        supported = capability.supported == CommandSupport.SUPPORTED,
        areaListWithoutRootSupported =
            capability.areaListWithoutRootAreaSupported == CommandSupport.SUPPORTED,
        areaListWithoutImmediateParentSupported =
            capability.areaListWithoutNodeImmediateParentAreaSupported == CommandSupport.SUPPORTED,
        supportedPlacements = placements,
    )
}

internal fun SystemScanContext.profileIn(profiles: List<SystemProfile>): SystemProfile? {
    val systemCode = systemCode?.toHexString()?.uppercase() ?: return null
    val idm = idm?.toHexString()?.uppercase() ?: return null
    return profiles.firstOrNull { it.systemCode == systemCode && it.idm == idm }
}

internal fun SystemScanContext.authenticationRequiredServices(): Map<String, Service> =
    nodes
        .filterIsInstance<Service>()
        .filter { it.attribute.authenticationRequired }
        .associateBy { it.code.toHexString().uppercase() }
