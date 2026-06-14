package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetNodeKeyVersionsStep :
    ScanStep(
        id = "get_node_key_versions",
        title = "Get Node Key Versions",
        description = "Get key versions for discovered nodes using the best supported command",
        icon = ScanStepIcon.CHECK,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        return when {
            scanContext.requestServiceV2Support == CommandSupport.SUPPORTED ->
                requestServiceV2KeyVersions()
            scanContext.requestServiceSupport == CommandSupport.SUPPORTED ->
                requestServiceKeyVersions()
            else ->
                throw StepSkipped(
                    "Get node key versions requires Request Service or Request Service V2 support"
                )
        }
    }

    private suspend fun ScanSession.requestServiceKeyVersions(): StepOutput {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val areas = allDiscoveredNodes.filterIsInstance<Area>()
        val services = allDiscoveredNodes.filterIsInstance<Service>()
        val systems = allDiscoveredNodes.filterIsInstance<System>()

        // Check if no areas are known - consider this a failure
        if (allDiscoveredNodes.isEmpty()) {
            throw StepPreconditionNotMet(
                "No nodes found. Request service key versions require at least one area to be discovered."
            )
        }
        // Get key versions in batches (max 32 nodes per request)
        val maxNodesPerRequest = 32
        val keyVersionResults = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // Process each system context separately
        for (systemContext in scanContext.systemScanContexts) {
            val systemNodes = systemContext.nodes
            val nodeKeyVersionsMap = mutableMapOf<Node, KeyVersion>()

            if (systemNodes.isEmpty()) {
                continue
            }
            systemNodes.chunked(maxNodesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
                val requestServiceResponse =
                    executeCommand(withSelectedSystemCode = systemContext.systemCode) {
                        RequestServiceCommand(idm, nodeCodes)
                    }

                // Collect key version results for this batch
                nodeBatch.forEachIndexed { index, node ->
                    val keyVersion = requestServiceResponse.keyVersions[index]
                    val exists = !requestServiceResponse.keyVersions[index].isMissing
                    val nodeType =
                        describeNode(node, includeNodeNumber = false, includeCode = false)
                    val status = if (exists) "Key Version: ${keyVersion.toInt()}" else "Not found"
                    keyVersionResults.add(
                        "${systemContext.systemCode?.toHexString() ?: "unknown"} - $nodeType ${node.code.toHexString()}: $status"
                    )

                    // Store key version in map
                    if (exists) {
                        nodeKeyVersionsMap[node] = keyVersion
                    }
                }
            }
            // Filter out registry-populated nodes that don't actually exist on the card
            // Only filter nodes that were populated from registry, keep discovered nodes as-is
            // Always keep System and root Area nodes as they are structural
            val filteredNodes = systemNodes.filter { node ->
                // Always keep System and root Area nodes
                if (node is System || (node is Area && node.isRoot)) {
                    return@filter true
                }
                val isRegistryPopulated = systemContext.registryPopulatedNodes.contains(node)
                if (isRegistryPopulated) {
                    // Registry nodes must have a key version to be kept
                    nodeKeyVersionsMap.containsKey(node)
                } else {
                    // Discovered nodes are always kept
                    true
                }
            }

            // Update context with key version data and filtered nodes
            val updatedSystemContext =
                systemContext.copy(nodes = filteredNodes, nodeKeyVersions = nodeKeyVersionsMap)
            updatedSystemContexts.add(updatedSystemContext)
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        // Return summary with key version details
        val collapsedSummary =
            "Got node key versions for ${areas.size} areas, ${services.size} services across ${updatedSystemContexts.size} system(s)"
        val expandedResult =
            if (keyVersionResults.isNotEmpty()) {
                collapsedSummary + "\n" + keyVersionResults.joinToString("\n")
            } else {
                collapsedSummary + " (no details available)"
            }

        return StepOutput(result = expandedResult, collapsedResult = collapsedSummary)
    }

    private suspend fun ScanSession.requestServiceV2KeyVersions(): StepOutput {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val areas = allDiscoveredNodes.filterIsInstance<Area>()
        val services = allDiscoveredNodes.filterIsInstance<Service>()
        val systems = allDiscoveredNodes.filterIsInstance<System>()

        // Check if no areas are known - consider this a failure
        if (allDiscoveredNodes.isEmpty()) {
            throw StepPreconditionNotMet(
                "No nodes found. Request service key versions require at least one area to be discovered."
            )
        }
        // Get key versions in batches (max 32 nodes per request)
        val maxNodesPerRequest = 32
        val keyVersionResults = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var globalEncryptionId: EncryptionIdentifier? = null

        // Process each system context separately
        for (systemContext in scanContext.systemScanContexts) {
            val nodes = systemContext.nodes
            val nodeAesKeyVersionsMap = mutableMapOf<Node, KeyVersion>()
            val nodeDesKeyVersionsMap = mutableMapOf<Node, KeyVersion>()
            var encryptionId: EncryptionIdentifier? = null

            if (nodes.isEmpty()) {
                continue
            }
            nodes.chunked(maxNodesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
                val requestServiceV2Response =
                    executeCommand(withSelectedSystemCode = systemContext.systemCode) {
                        RequestServiceV2Command(idm, nodeCodes)
                    }

                if (requestServiceV2Response.isStatusSuccessful) {
                    // Store encryption identifier from first successful response
                    if (encryptionId == null) {
                        encryptionId = requestServiceV2Response.encryptionIdentifier
                        if (globalEncryptionId == null) {
                            globalEncryptionId = encryptionId
                        }
                    }

                    // Collect key version results for this batch
                    val encId = requestServiceV2Response.encryptionIdentifier
                    val supportsAes = encId?.aesKeyType != AesKeyType.NONE
                    val supportsDes = encId?.desKeyType != DesKeyType.NONE

                    nodeBatch.forEachIndexed { index, node ->
                        val aesKeyVersion = requestServiceV2Response.aesKeyVersions.getOrNull(index)
                        if (aesKeyVersion == null && supportsAes) {
                            throw RuntimeException(
                                "AES key version missing for node at index $index"
                            )
                        }
                        val desKeyVersion = requestServiceV2Response.desKeyVersions.getOrNull(index)
                        if (desKeyVersion == null && supportsDes) {
                            throw RuntimeException(
                                "DES key version missing for node at index $index"
                            )
                        }
                        val aesExists = aesKeyVersion?.isMissing == false
                        val desExists = desKeyVersion?.isMissing == false

                        val nodeType =
                            describeNode(node, includeNodeNumber = false, includeCode = false)

                        val nodeCodeHex = node.code.toHexString()
                        val encryptionInfo =
                            requestServiceV2Response.encryptionIdentifier?.let { encId ->
                                " (${encId.name})"
                            } ?: ""

                        val aesStatus =
                            if (aesExists) "AES: ${aesKeyVersion?.toInt()}" else "AES: N/A"
                        val desStatus =
                            if (desExists) {
                                "DES: ${desKeyVersion?.toInt()}"
                            } else "DES: Not supported"

                        keyVersionResults.add(
                            "${systemContext.systemCode?.toHexString() ?: "unknown"} - $nodeType $nodeCodeHex$encryptionInfo: $aesStatus, $desStatus"
                        )

                        aesKeyVersion
                            ?.takeUnless { it.isMissing }
                            ?.let { keyVersion -> nodeAesKeyVersionsMap[node] = keyVersion }
                        desKeyVersion
                            ?.takeUnless { it.isMissing }
                            ?.let { keyVersion -> nodeDesKeyVersionsMap[node] = keyVersion }
                    }
                } else {
                    keyVersionResults.add(
                        "${systemContext.systemCode?.toHexString() ?: "unknown"} - Batch ${batchIndex + 1}: Error - Status: ${requestServiceV2Response.statusFlag1.toInt() and 0xFF}"
                    )
                }
            }

            // Filter out registry-populated nodes that don't actually exist on the card
            // Only filter nodes that were populated from registry, keep discovered nodes as-is
            // Always keep System and root Area nodes as they are structural
            val filteredNodes = nodes.filter { node ->
                // Always keep System and root Area nodes
                if (node is System || (node is Area && node.isRoot)) {
                    return@filter true
                }
                val isRegistryPopulated = systemContext.registryPopulatedNodes.contains(node)
                if (isRegistryPopulated) {
                    // Registry nodes must have either AES or DES key version to be kept
                    nodeAesKeyVersionsMap.containsKey(node) ||
                        nodeDesKeyVersionsMap.containsKey(node)
                } else {
                    // Discovered nodes are always kept
                    true
                }
            }

            // Update context with key version data and filtered nodes
            val updatedSystemContext =
                systemContext.copy(
                    nodes = filteredNodes,
                    nodeAesKeyVersions = nodeAesKeyVersionsMap,
                    nodeDesKeyVersions = nodeDesKeyVersionsMap,
                    encryptionIdentifier = encryptionId,
                )
            updatedSystemContexts.add(updatedSystemContext)
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        // Return summary with key version details
        val collapsedSummary =
            "Got enhanced node key versions for ${areas.size} areas, ${services.size} services across ${updatedSystemContexts.size} system(s)"
        val expandedResult =
            if (keyVersionResults.isNotEmpty()) {
                collapsedSummary + "\n" + keyVersionResults.joinToString("\n")
            } else {
                collapsedSummary + " (no details available)"
            }

        return StepOutput(result = expandedResult, collapsedResult = collapsedSummary)
    }

    /**
     * Force discover all nodes by iterating through all possible node codes (0-1023) with all known
     * service and area attributes. Uses RequestServiceV2 if available, otherwise RequestService.
     * Nodes discovered this way that were not found in regular discovery are marked as hidden.
     */
}
