package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.SystemScanContext

internal data class Authentication1DesTestTarget(
    val systemContext: SystemScanContext,
    val rootArea: Area,
)

internal fun CardScanContext.findBestAuthentication1DesTarget(): Authentication1DesTestTarget? {
    var bestTarget: Authentication1DesTestTarget? = null
    var bestNodeCount = -1

    for (systemContext in systemScanContexts) {
        val rootArea =
            systemContext.nodes.filterIsInstance<Area>().firstOrNull { it.isRoot } ?: Area.ROOT
        val hasValidDesKeyOnRootArea =
            systemContext.nodeDesKeyVersions.containsKey(rootArea) ||
                (!systemContext.nodeAesKeyVersions.containsKey(rootArea) &&
                    systemContext.nodeKeyVersions.containsKey(rootArea))
        if (!hasValidDesKeyOnRootArea) {
            continue
        }

        val nodeCount = systemContext.nodes.size
        if (nodeCount > bestNodeCount) {
            bestNodeCount = nodeCount
            bestTarget =
                Authentication1DesTestTarget(systemContext = systemContext, rootArea = rootArea)
        }
    }

    return bestTarget
}
