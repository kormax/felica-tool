package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.CountInformation
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.RequestBlockInformationCommand
import com.kormax.felicatool.felica.RequestBlockInformationExCommand
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.ScanStep
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.service.StepSkipped
import com.kormax.felicatool.service.SystemScanContext
import com.kormax.felicatool.ui.ScanStepIcon

private const val REQUEST_BLOCK_INFORMATION_MAX_NODES = 32
private const val REQUEST_BLOCK_INFORMATION_EX_MAX_NODES = 16

internal object GetNodeBlockInformationStep :
    ScanStep(
        id = "get_node_block_information",
        title = "Get Node Block Information",
        description = "Request block counts for discovered nodes",
        icon = ScanStepIcon.INFO,
    ) {
    override fun commandSupport(context: CardScanContext): CommandSupport =
        when {
            context.commands.requestBlockInformationEx.supported == CommandSupport.SUPPORTED ||
                context.commands.requestBlockInformation.supported == CommandSupport.SUPPORTED ->
                CommandSupport.SUPPORTED
            context.commands.requestBlockInformationEx.supported == CommandSupport.UNSUPPORTED &&
                context.commands.requestBlockInformation.supported == CommandSupport.UNSUPPORTED ->
                CommandSupport.UNSUPPORTED
            else -> CommandSupport.UNKNOWN
        }

    override suspend fun ScanSession.perform(): StepOutput {
        val method =
            when {
                scanContext.commands.requestBlockInformationEx.supported ==
                    CommandSupport.SUPPORTED -> BlockInformationMethod.EX
                scanContext.commands.requestBlockInformation.supported ==
                    CommandSupport.SUPPORTED -> BlockInformationMethod.REGULAR
                else ->
                    throw StepSkipped(
                        "Request Block Information is unavailable; cannot read block counts"
                    )
            }
        val allNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allNodes.isEmpty()) {
            throw StepSkipped(
                "No nodes discovered. Get Node Block Information requires discovered nodes from Discover Nodes step."
            )
        }

        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalBlockCountsRetrieved = 0

        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            val nodes = systemContext.nodes
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (nodes.isEmpty()) {
                results.add("System Context ${contextIndex + 1} ($systemCodeHex): No nodes found")
                updatedSystemContexts.add(systemContext)
                continue
            }

            val result =
                when (method) {
                    BlockInformationMethod.EX -> loadExtendedBlockCounts(systemContext, nodes)
                    BlockInformationMethod.REGULAR -> loadRegularBlockCounts(systemContext, nodes)
                }
            totalBlockCountsRetrieved += result.countsRetrieved
            updatedSystemContexts.add(result.systemContext)

            results.add(
                buildString {
                    appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                    appendLine("  Nodes processed: ${nodes.size}")
                    appendLine()

                    if (result.lines.isNotEmpty()) {
                        result.lines.forEach { line -> appendLine(line) }
                    } else {
                        appendLine("  No block information retrieved")
                    }
                }
            )
        }

        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        val collapsedResult =
            "Loaded block counts for $totalBlockCountsRetrieved/${allNodes.size} node(s) using ${method.label}"
        val expandedResult =
            buildString {
                    appendLine("Get Node Block Information Results:")
                    appendLine("Method: ${method.label}")
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

    private suspend fun ScanSession.loadExtendedBlockCounts(
        systemContext: SystemScanContext,
        nodes: List<Node>,
    ): BlockInformationLoadResult {
        val lines = mutableListOf("           Assign |  Free  |  Total")
        val nodeAssignedBlockCountsMap = mutableMapOf<Node, CountInformation>()
        val nodeFreeBlockCountsMap = mutableMapOf<Node, CountInformation>()
        var countsRetrieved = 0

        nodes.chunked(REQUEST_BLOCK_INFORMATION_EX_MAX_NODES).forEach { nodeBatch ->
            val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
            val response =
                executeCommand(withSelectedSystemCode = systemContext.systemCode) {
                    RequestBlockInformationExCommand(idm, nodeCodes)
                }

            nodeBatch.zip(response.assignedBlockCount.zip(response.freeBlockCount)).forEach {
                (node, blockCounts) ->
                val (assignedCount, freeCount) = blockCounts
                countsRetrieved++
                val assignedBlocks = assignedCount.toInt()
                val freeBlocks = freeCount.toInt()
                val totalBlocks = assignedBlocks + freeBlocks
                lines.add(
                    " ${node.fullCode.toHexString().padStart(8, ' ')}: ${assignedBlocks.toString().padStart(6, ' ')} | ${freeBlocks.toString().padStart(6, ' ')} | ${totalBlocks.toString().padStart(6, ' ')}"
                )

                nodeAssignedBlockCountsMap[node] = assignedCount
                nodeFreeBlockCountsMap[node] = freeCount
            }
        }

        return BlockInformationLoadResult(
            systemContext =
                systemContext.copy(
                    nodeBlockCounts = emptyMap(),
                    nodeAssignedBlockCounts = nodeAssignedBlockCountsMap,
                    nodeFreeBlockCounts = nodeFreeBlockCountsMap,
                ),
            lines = lines,
            countsRetrieved = countsRetrieved,
        )
    }

    private suspend fun ScanSession.loadRegularBlockCounts(
        systemContext: SystemScanContext,
        nodes: List<Node>,
    ): BlockInformationLoadResult {
        val lines = mutableListOf("           Assign |  Free")
        val nodeBlockCountsMap = mutableMapOf<Node, CountInformation>()
        val nodeAssignedBlockCountsMap = mutableMapOf<Node, CountInformation>()
        val nodeFreeBlockCountsMap = mutableMapOf<Node, CountInformation>()
        var countsRetrieved = 0

        nodes.chunked(REQUEST_BLOCK_INFORMATION_MAX_NODES).forEach { nodeBatch ->
            val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
            val response =
                executeCommand(withSelectedSystemCode = systemContext.systemCode) {
                    RequestBlockInformationCommand(idm, nodeCodes)
                }

            nodeBatch.zip(response.blockCountInformation).forEach { (node, blockInfo) ->
                countsRetrieved++
                val assignedCount: CountInformation?
                val freeCount: CountInformation?
                when (node) {
                    is Area -> {
                        assignedCount = null
                        freeCount = blockInfo
                        nodeFreeBlockCountsMap[node] = blockInfo
                    }
                    is Service -> {
                        assignedCount = blockInfo
                        freeCount = null
                        nodeAssignedBlockCountsMap[node] = blockInfo
                    }
                    else -> {
                        assignedCount = null
                        freeCount = null
                        nodeBlockCountsMap[node] = blockInfo
                    }
                }

                val assignedText = assignedCount?.toInt()?.toString()?.padStart(6, ' ') ?: "     -"
                val freeText = freeCount?.toInt()?.toString()?.padStart(6, ' ') ?: "     -"
                lines.add(
                    " ${node.fullCode.toHexString().padStart(8, ' ')}: $assignedText | $freeText"
                )
            }
        }

        return BlockInformationLoadResult(
            systemContext =
                systemContext.copy(
                    nodeBlockCounts = nodeBlockCountsMap,
                    nodeAssignedBlockCounts = nodeAssignedBlockCountsMap,
                    nodeFreeBlockCounts = nodeFreeBlockCountsMap,
                ),
            lines = lines,
            countsRetrieved = countsRetrieved,
        )
    }
}

private enum class BlockInformationMethod(val label: String) {
    EX("Request Block Information Ex"),
    REGULAR("Request Block Information"),
}

private data class BlockInformationLoadResult(
    val systemContext: SystemScanContext,
    val lines: List<String>,
    val countsRetrieved: Int,
)
