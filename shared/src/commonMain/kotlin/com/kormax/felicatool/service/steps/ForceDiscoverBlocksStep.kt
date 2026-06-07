package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object ForceDiscoverBlocksStep :
    ScanStep(
        id = "force_discover_blocks",
        title = "Force Discover Blocks",
        description =
            "Exhaustively search for blocks in readable services by iterating through all possible block numbers",
        icon = ScanStepIcon.SEARCH,
    ) {
    override fun isEnabled(settings: ScanSettings): Boolean = settings.forceDiscoverAllBlocks

    override suspend fun ScanSession.perform(): StepOutput {
        ensureCardPresence(target)

        val contextResults = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalNewBlocksFound = 0
        var totalServicesProcessed = 0
        var totalBlocksScanned = 0

        val maxBlocksPerRequest = scanContext.readWithoutEncryptionMaxBlocksPerRequest ?: 15

        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            pollSystemCode(target, systemContext.systemCode)

            val services = systemContext.nodes.filterIsInstance<Service>()
            val servicesWithoutAuth = services.filter { !it.attribute.authenticationRequired }
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (servicesWithoutAuth.isEmpty()) {
                contextResults.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No readable services found"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            // Get existing block data for this system context
            val existingBlockData = systemContext.serviceBlockData.toMutableMap()
            val newBlocksFoundInContext = mutableMapOf<Service, MutableMap<Int, ByteArray>>()

            for (service in servicesWithoutAuth) {
                totalServicesProcessed++
                val existingBlocks = existingBlockData[service]?.keys ?: emptySet()
                val newBlocks = mutableMapOf<Int, ByteArray>()

                // Determine the starting block number - start after the last known block
                val maxExistingBlock = existingBlocks.maxOrNull() ?: -1
                var startBlock = maxExistingBlock + 1

                // Skip if we've already scanned up to the maximum
                if (startBlock > 0xFFFF) {
                    continue
                }

                // Iterate through blocks in batches
                var currentBlock = startBlock
                var consecutiveFailures = 0
                val maxConsecutiveFailures = 256 // Stop after this many consecutive failures

                while (currentBlock <= 0xFFFF && consecutiveFailures < maxConsecutiveFailures) {
                    totalBlocksScanned++

                    try {
                        val blockElement =
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = currentBlock,
                                accessMode = BlockListElement.AccessMode.NORMAL,
                                extended = currentBlock > 255,
                            )
                        val command =
                            ReadWithoutEncryptionCommand(
                                idm = target.idm,
                                serviceCodes = arrayOf(service.code),
                                blockListElements = arrayOf(blockElement),
                            )
                        val response = target.transceive(command)

                        if (
                            response.statusFlag1 == 0x00.toByte() && response.blockData.isNotEmpty()
                        ) {
                            val readBlockData = response.blockData.first()
                            if (!existingBlocks.contains(currentBlock)) {
                                newBlocks[currentBlock] = readBlockData
                                totalNewBlocksFound++
                            }
                            consecutiveFailures = 0
                        } else {
                            consecutiveFailures++
                        }
                    } catch (e: Exception) {
                        consecutiveFailures++
                    }

                    currentBlock++
                }

                if (newBlocks.isNotEmpty()) {
                    newBlocksFoundInContext[service] = newBlocks
                }
            }

            // Merge new blocks with existing block data
            val mergedBlockData = existingBlockData.toMutableMap()
            for ((service, newBlocks) in newBlocksFoundInContext) {
                val existing = mergedBlockData[service]?.toMutableMap() ?: mutableMapOf()
                existing.putAll(newBlocks)
                mergedBlockData[service] = existing
            }

            val updatedSystemContext = systemContext.copy(serviceBlockData = mergedBlockData)
            updatedSystemContexts.add(updatedSystemContext)

            // Build context results
            val contextNewBlocks = newBlocksFoundInContext.values.sumOf { it.size }
            val contextResult = buildString {
                appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                appendLine("  New blocks discovered: $contextNewBlocks")
                if (newBlocksFoundInContext.isNotEmpty()) {
                    newBlocksFoundInContext.forEach { (service, blocks) ->
                        appendLine(
                            "  Service ${service.code.toHexString()}: ${blocks.size} new blocks"
                        )
                        val previewBlocks = blocks.entries.sortedBy { it.key }.take(4)
                        previewBlocks.forEach { (blockNum, data) ->
                            appendLine(
                                "    Block 0x${formatBlockNumberHex(blockNum)}: ${data.toHexString()}"
                            )
                        }
                        if (blocks.size > 4) {
                            appendLine("    ... (${blocks.size - 4} more blocks)")
                        }
                    }
                }
            }
            contextResults.add(contextResult)
        }

        // Update scan context
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        val collapsedResult =
            "Discovered $totalNewBlocksFound new blocks from $totalServicesProcessed services"
        val expandedResult =
            buildString {
                    appendLine("Force Block Discovery Results:")
                    appendLine("Total new blocks discovered: $totalNewBlocksFound")
                    appendLine("Services processed: $totalServicesProcessed")
                    appendLine("Total blocks scanned: $totalBlocksScanned")
                    appendLine()
                    contextResults.forEach { appendLine(it) }
                    appendLine()
                    appendLine(
                        "Note: Scanning stops after $maxBlocksPerRequest consecutive read failures per service."
                    )
                }
                .trim()

        return StepOutput(
            result = expandedResult,
            collapsedResult = collapsedResult,
        )
    }
}
