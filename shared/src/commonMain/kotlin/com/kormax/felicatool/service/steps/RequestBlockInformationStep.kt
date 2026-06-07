package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestBlockInformationStep :
    CommandSupportScanStep(
        id = "request_block_information",
        title = "Request Block Information",
        description = "Request the amount of allocated blocks for nodes",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestBlockInformationSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestBlockInformationSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val allNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allNodes.isEmpty()) {
            throw StepPreconditionNotMet(
                "No nodes discovered. Request Block Information requires discovered nodes from Discover Nodes step."
            )
        }
        ensureCardPresence(target)

        // Request block information in batches (max 32 services per request as per FeliCa spec)
        val maxServicesPerRequest = 32
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalBlockCountsRetrieved = 0

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val nodes = systemContext.nodes
            val blockInfoResults = mutableListOf<String>()
            val nodeBlockCountsMap = mutableMapOf<Node, CountInformation>()
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (nodes.isEmpty()) {
                results.add("System Context ${contextIndex + 1} ($systemCodeHex): No nodes found")
                updatedSystemContexts.add(systemContext)
                continue
            }

            nodes.chunked(maxServicesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
                val requestBlockInfoCommand = RequestBlockInformationCommand(target.idm, nodeCodes)
                val requestBlockInfoResponse = target.transceive(requestBlockInfoCommand)

                // Process the block information for each service in this batch
                nodeBatch.zip(requestBlockInfoResponse.assignedBlockCountInformation).forEach {
                    (node, blockInfo) ->
                    totalBlockCountsRetrieved++
                    val blockCount = blockInfo.toInt()
                    blockInfoResults.add(
                        "${node.fullCode.toHexString().padStart(8, ' ')}: ${blockCount.toString().padStart(5, ' ')} blocks"
                    )
                    // Store block count information object in map
                    nodeBlockCountsMap[node] = blockInfo
                }
            }

            // Update context with block count data
            val updatedSystemContext = systemContext.copy(nodeBlockCounts = nodeBlockCountsMap)
            updatedSystemContexts.add(updatedSystemContext)

            results.add(
                buildString {
                    appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                    appendLine("  Services processed: ${nodes.size}")
                    appendLine()

                    if (blockInfoResults.isNotEmpty()) {
                        blockInfoResults.forEach { result -> appendLine(result) }
                    } else {
                        appendLine("  No block information retrieved")
                    }
                }
            )
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        val collapsedResult =
            "Loaded block counts for $totalBlockCountsRetrieved/${allNodes.size} node(s) across ${updatedSystemContexts.size} system(s)"
        val expandedResult =
            buildString {
                    appendLine("Request Block Information Results:")
                    appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                    appendLine("Total services processed: ${allNodes.size}")
                    appendLine()

                    results.forEach { result ->
                        appendLine(result)
                        appendLine()
                    }
                }
                .trim()

        return StepOutput(result = expandedResult, collapsedResult = collapsedResult)
    }
}
