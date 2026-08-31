package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object ReadBlocksWithoutEncryptionStep :
    ReadWithoutEncryptionScanStep(
        id = "read_blocks_without_encryption",
        title = "Read Blocks Without Encryption",
        description = "Reading block data from services that don't require authentication",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val allServices =
            scanContext.systemScanContexts.flatMap { it.nodes }.filterIsInstance<Service>()

        if (allServices.isEmpty()) {
            throw StepSkipped("No services available for block reading")
        }

        if (allServices.none { !it.attribute.authenticationRequired }) {
            throw StepSkipped("No services found that don't require authentication")
        }

        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        val contextResults = mutableListOf<String>()
        var totalBlocksRead = 0
        var totalServicesProcessed = 0

        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            val servicesWithoutAuth =
                systemContext.nodes.filterIsInstance<Service>().filter {
                    !it.attribute.authenticationRequired
                }
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (servicesWithoutAuth.isEmpty()) {
                contextResults.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No services without authentication found"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            val blockReadHints = mutableMapOf<Service, List<BlockReadCandidate>>()
            val systemCode = systemContext.systemCode?.toHexString()
            if (systemCode != null) {
                for (service in servicesWithoutAuth) {
                    val blockIndices =
                        nodeMetadataProvider
                            .getExtraBlocks(systemCode, service.code.toHexString())
                            .keys
                    if (blockIndices.isNotEmpty()) {
                        blockReadHints[service] =
                            listOf(
                                BlockReadCandidate(
                                    blockIndices,
                                    probeAfterRuns = false,
                                )
                            )
                    }
                }
            }

            val blockDataByService =
                UnencryptedBlockReader(
                        session = this,
                        systemCode = systemContext.systemCode,
                        maxBlocksPerRequest =
                            scanContext.commands.readWithoutEncryption.maxBlocksPerRequest ?: 15,
                        maxServicesPerRequest =
                            scanContext.commands.readWithoutEncryption.maxServicesPerRequest ?: 16,
                        errorLocationIndication =
                            scanContext.commands.readWithoutEncryption.errorLocationIndication,
                    )
                    .read(servicesWithoutAuth, blockReadHints)

            updatedSystemContexts.add(
                systemContext.copy(serviceBlockData = blockDataByService.mapKeys { it.key })
            )

            val contextBlocksRead = blockDataByService.values.sumOf { it.size }
            totalBlocksRead += contextBlocksRead
            totalServicesProcessed += blockDataByService.size

            contextResults += buildString {
                appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                appendLine("  Blocks read: $contextBlocksRead")
                appendLine("  Services processed: ${blockDataByService.size}")
                appendLine()

                blockDataByService.forEach { (service, blockData) ->
                    val regularBlocks = blockData.keys.count { it < 0x80 }
                    val extraBlocks = blockData.keys.count { it >= 0x80 }
                    appendLine(
                        "  Service ${service.code.toHexString()}: ${blockData.size} blocks ($regularBlocks regular, $extraBlocks extra)"
                    )
                    if (blockData.isNotEmpty()) {
                        blockData.entries
                            .sortedBy { it.key }
                            .take(4)
                            .forEach { (blockNum, data) ->
                                appendLine(
                                    "    Block 0x${formatBlockNumberHex(blockNum)}: ${data.toHexString()}"
                                )
                            }
                        if (blockData.size > 4) {
                            appendLine("    ... (${blockData.size - 4} more blocks)")
                        }
                    }
                    appendLine()
                }
            }
        }

        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        val collapsedResult =
            "Read $totalBlocksRead blocks from $totalServicesProcessed services across ${updatedSystemContexts.size} system(s)"
        val expandedResult = buildString {
            appendLine("Block Reading Results:")
            appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
            appendLine("Total blocks read: $totalBlocksRead")
            appendLine("Total services processed: $totalServicesProcessed")
            appendLine()
            contextResults.forEach { result -> appendLine(result) }
            appendLine("Note: Only services that don't require authentication are processed.")
            appendLine("Block data is stored per system context for comprehensive analysis.")
        }
            .trim()

        return StepOutput(result = expandedResult, collapsedResult = collapsedResult)
    }
}
