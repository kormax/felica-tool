package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object ForceDiscoverNodesStep :
    ScanStep(
        id = "force_discover_nodes",
        title = "Force Discover All Nodes",
        description =
            "Exhaustively search for hidden nodes using RequestService by iterating all possible node codes",
        icon = ScanStepIcon.SEARCH,
    ) {
    override fun isEnabled(settings: ScanSettings): Boolean = settings.forceDiscoverAllNodes

    override suspend fun ScanSession.perform(): StepOutput {
        // Check if RequestService commands are supported
        val requestServiceV2Supported =
            scanContext.commands.requestServiceV2.supported == CommandSupport.SUPPORTED
        val requestServiceSupported =
            scanContext.commands.requestService.supported == CommandSupport.SUPPORTED

        if (!requestServiceV2Supported && !requestServiceSupported) {
            throw StepSkipped(
                "Force discover requires RequestService or RequestServiceV2 to be supported"
            )
        }

        val useV2 = requestServiceV2Supported
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // All known service attributes to probe
        val serviceAttributes = ServiceAttribute.entries
        // Area attributes to probe - exclude end markers (EndRootArea, EndSubArea),
        // as they are not valid start attributes
        val areaAttributes =
            AreaAttribute.entries.filter {
                it != AreaAttribute.EndRootArea && it != AreaAttribute.EndSubArea
            }
        val maxNodeNumber = 1023 // Node codes range from 0 to 1023 (10 bits)
        val batchSize = 32 // Max nodes per request

        var totalDiscovered = 0
        var totalHidden = 0
        var totalHiddenServices = 0
        var totalHiddenAreas = 0

        // Process each system context
        for (systemContext in scanContext.systemScanContexts) {
            val existingNodes = systemContext.nodes.toSet()
            val existingNodeCodes = existingNodes.map { it.code.toHexString().uppercase() }.toSet()
            val newlyDiscoveredNodes = mutableListOf<Node>()
            val hiddenNodesSet = mutableSetOf<Node>()

            // Track key versions for discovered nodes
            val newNodeKeyVersions = mutableMapOf<Node, KeyVersion>()
            val newNodeAesKeyVersions = mutableMapOf<Node, KeyVersion>()
            val newNodeDesKeyVersions = mutableMapOf<Node, KeyVersion>()

            // Generate all possible nodes to probe (services and areas),
            // skipping codes that were already discovered by normal scanning
            // For areas, we create minimal areas where endNumber equals number
            // since we don't know the actual end code from RequestService
            val nodesToProbe =
                (0..maxNodeNumber)
                    .flatMap { nodeNumber ->
                        areaAttributes.map { attribute ->
                            Area(
                                number = nodeNumber,
                                attribute = attribute,
                                endNumber = nodeNumber,
                                endAttribute = AreaAttribute.EndSubArea,
                            )
                        } +
                            serviceAttributes.map { attribute ->
                                Service(nodeNumber, attribute)
                            }
                    }
                    .filter { it.code.toHexString().uppercase() !in existingNodeCodes }

            // Normalised per-slot result: the key version to check for presence,
            // a display label, and a store action that writes into the correct maps.
            data class SlotResult(
                val keyVersion: KeyVersion,
                val keyLabel: String,
                val store: (Node) -> Unit,
            )

            // Process in batches
            nodesToProbe.chunked(batchSize).forEach { batch ->
                try {
                    val nodeCodes = batch.map { it.code }.toTypedArray()

                    val slots: List<SlotResult> =
                        if (useV2) {
                            val response =
                                executeCommand(withSelectedSystemCode = systemContext.systemCode) {
                                    RequestServiceV2Command(idm, nodeCodes)
                                }
                            if (!response.isStatusSuccessful) return@forEach
                            batch.indices.map { i ->
                                val aes = response.aesKeyVersions[i]
                                val des = response.desKeyVersions[i]
                                SlotResult(aes, "AES") { node ->
                                    newNodeAesKeyVersions[node] = aes
                                    if (!des.isMissing) newNodeDesKeyVersions[node] = des
                                }
                            }
                        } else {
                            val response =
                                executeCommand(withSelectedSystemCode = systemContext.systemCode) {
                                    RequestServiceCommand(idm, nodeCodes)
                                }
                            batch.indices.map { i ->
                                val kv = response.keyVersions[i]
                                SlotResult(kv, "Key") { node -> newNodeKeyVersions[node] = kv }
                            }
                        }

                    batch.forEachIndexed { index, node ->
                        val (keyVersion, keyLabel, store) = slots[index]
                        // If key version is not FFFF, the node exists
                        if (!keyVersion.isMissing) {
                            val codeHex = node.code.toHexString().uppercase()
                            store(node)
                            if (!existingNodeCodes.contains(codeHex)) {
                                // This is a hidden node
                                newlyDiscoveredNodes.add(node)
                                hiddenNodesSet.add(node)
                                totalHidden++
                                when (node) {
                                    is Service -> {
                                        totalHiddenServices++
                                        results.add(
                                            "${systemContext.systemCode?.toHexString() ?: "unknown"} - Hidden Service $codeHex: $keyLabel v${keyVersion.toInt()}"
                                        )
                                    }
                                    is Area -> {
                                        totalHiddenAreas++
                                        results.add(
                                            "${systemContext.systemCode?.toHexString() ?: "unknown"} - Hidden Area $codeHex: $keyLabel v${keyVersion.toInt()}"
                                        )
                                    }
                                    else -> {}
                                }
                            }
                            totalDiscovered++
                        }
                    }
                } catch (e: Exception) {
                    // Log error but continue with next batch
                    ScanLog.w("CardScanService", "Force discover batch failed: ${e.message}")
                }
            }

            // Merge newly discovered nodes with existing nodes
            val allNodes = systemContext.nodes + newlyDiscoveredNodes
            val mergedHiddenNodes = systemContext.hiddenNodes + hiddenNodesSet

            // Merge key versions (existing + newly discovered)
            val mergedNodeKeyVersions = systemContext.nodeKeyVersions + newNodeKeyVersions
            val mergedNodeAesKeyVersions = systemContext.nodeAesKeyVersions + newNodeAesKeyVersions
            val mergedNodeDesKeyVersions = systemContext.nodeDesKeyVersions + newNodeDesKeyVersions

            val updatedSystemContext =
                systemContext.copy(
                    nodes = allNodes,
                    hiddenNodes = mergedHiddenNodes,
                    nodeKeyVersions = mergedNodeKeyVersions,
                    nodeAesKeyVersions = mergedNodeAesKeyVersions,
                    nodeDesKeyVersions = mergedNodeDesKeyVersions,
                )
            updatedSystemContexts.add(updatedSystemContext)
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        val collapsedSummary =
            "Force discovered $totalHidden hidden node(s) ($totalHiddenAreas areas, $totalHiddenServices services) out of $totalDiscovered total present"
        val expandedResult =
            if (results.isNotEmpty()) {
                collapsedSummary + "\n" + results.joinToString("\n")
            } else {
                collapsedSummary + "\nNo hidden nodes found"
            }

        return StepOutput(result = expandedResult, collapsedResult = collapsedSummary)
    }
}
