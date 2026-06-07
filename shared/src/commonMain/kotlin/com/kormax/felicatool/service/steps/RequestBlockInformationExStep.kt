package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestBlockInformationExStep :
    CommandSupportScanStep(
        id = "request_block_information_ex",
        title = "Request Block Information Ex",
        description = "Request the amount of allocated and free blocks for nodes",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestBlockInformationExSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestBlockInformationExSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val allNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allNodes.isEmpty()) {
            throw StepPreconditionNotMet(
                "No nodes discovered. Request Block Information Ex requires discovered nodes from Discover Nodes step."
            )
        }
        ensureCardPresence(target)

        // Request block information in batches (max 32 services per request as per FeliCa spec)
        val maxServicesPerRequest = 16
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalBlockCountsRetrieved = 0

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val nodes = systemContext.nodes
            val blockInfoResults = mutableListOf<String>()
            val nodeAssignedBlockCountsMap = mutableMapOf<Node, CountInformation>()
            val nodeFreeBlockCountsMap = mutableMapOf<Node, CountInformation>()
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (nodes.isEmpty()) {
                results.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No services found"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            blockInfoResults.add("           Assign |  Free  |  Total")

            nodes.chunked(maxServicesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
                val requestBlockInfoExCommand =
                    RequestBlockInformationExCommand(target.idm, nodeCodes)
                val requestBlockInfoExResponse = target.transceive(requestBlockInfoExCommand)
                // Process the extended block information for each service in this batch
                nodeBatch
                    .zip(
                        requestBlockInfoExResponse.assignedBlockCount.zip(
                            requestBlockInfoExResponse.freeBlockCount
                        )
                    )
                    .forEach { (node, blockCounts) ->
                        val (assignedCount, freeCount) = blockCounts
                        totalBlockCountsRetrieved++
                        val assignedBlocks = assignedCount.toInt()
                        val freeBlocks = freeCount.toInt()
                        val totalBlocks = assignedBlocks + freeBlocks
                        blockInfoResults.add(
                            " ${node.fullCode.toHexString().padStart(8, ' ')}: ${assignedBlocks.toString().padStart(6, ' ')} | ${freeBlocks.toString().padStart(6, ' ')} | ${totalBlocks.toString().padStart(6, ' ')}"
                        )

                        // Store block count information objects in maps
                        nodeAssignedBlockCountsMap[node] = assignedCount
                        nodeFreeBlockCountsMap[node] = freeCount
                    }
            }

            // Update context with block count data
            val updatedSystemContext =
                systemContext.copy(
                    nodeAssignedBlockCounts = nodeAssignedBlockCountsMap,
                    nodeFreeBlockCounts = nodeFreeBlockCountsMap,
                )
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
            "Loaded extended block counts for $totalBlockCountsRetrieved/${allNodes.size} node(s) across ${updatedSystemContexts.size} system(s)"
        val expandedResult =
            buildString {
                    appendLine("Request Block Information Ex Results:")
                    appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                    appendLine("Total nodes processed: ${allNodes.size}")
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
