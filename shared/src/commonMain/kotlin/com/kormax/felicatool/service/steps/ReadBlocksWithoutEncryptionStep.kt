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
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val allServices = allDiscoveredNodes.filterIsInstance<Service>()

        if (allServices.isEmpty()) {
            throw StepPreconditionNotMet("No services available for block reading")
        }

        // Filter services that don't require authentication across all system contexts
        val allServicesWithoutAuth = allServices.filter { !it.attribute.authenticationRequired }

        if (allServicesWithoutAuth.isEmpty()) {
            throw StepPreconditionNotMet("No services found that don't require authentication")
        }
        ensureCardPresence(target)

        // Process each system context separately
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        val contextResults = mutableListOf<String>()
        var totalBlocksRead = 0
        var totalServicesProcessed = 0
        var maxBlocksPerRequest = 0
        var maxServicesPerRequest = 0

        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val services = systemContext.nodes.filterIsInstance<Service>()
            val servicesWithoutAuth = services.filter { !it.attribute.authenticationRequired }
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (servicesWithoutAuth.isEmpty()) {
                contextResults.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No services without authentication found"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            // Use the utility function to read blocks with appropriate error indication mode
            // Look up extra blocks for each service from the node registry
            val extraBlocksByServiceCode = mutableMapOf<String, Map<Int, String>>()
            val systemCodeHexForLookup = systemContext.systemCode?.toHexString()?.uppercase()
            if (systemCodeHexForLookup != null) {
                for (service in servicesWithoutAuth) {
                    val serviceCodeHex = service.code.toHexString().uppercase()
                    val extraBlocks =
                        nodeMetadataProvider.getExtraBlocks(
                            systemCodeHexForLookup,
                            serviceCodeHex,
                        )
                    if (extraBlocks.isNotEmpty()) {
                        extraBlocksByServiceCode[service.code.toHexString().uppercase()] =
                            extraBlocks
                    }
                }
            }

            val readErrorLocationIndication =
                scanContext.readWithoutEncryptionErrorLocationIndication
            val configuredMaxBlocksPerRequest =
                scanContext.readWithoutEncryptionMaxBlocksPerRequest ?: 15
            val configuredMaxServicesPerRequest =
                scanContext.readWithoutEncryptionMaxServicesPerRequest ?: 16
            val blockDataByService =
                readBlocksFromServices(
                    services = servicesWithoutAuth,
                    systemCode = systemContext.systemCode,
                    errorLocationIndication = readErrorLocationIndication,
                    maxBlocksPerRequest = configuredMaxBlocksPerRequest,
                    maxServicesPerRequest = configuredMaxServicesPerRequest,
                )
            val extraBlockDataByService =
                readExtraBlocksFromServices(
                    services = servicesWithoutAuth,
                    systemCode = systemContext.systemCode,
                    extraBlocksByServiceCode = extraBlocksByServiceCode,
                )

            // Store block data in context for this system context, merging regular and extra
            // blocks
            val serviceBlockDataMap = mutableMapOf<Node, Map<Int, ByteArray>>()
            blockDataByService.forEach { (service, blockData) ->
                val mergedBlockData = blockData.toMutableMap()
                // Merge extra blocks if available for this service
                extraBlockDataByService[service]?.let { extraBlocks ->
                    mergedBlockData.putAll(extraBlocks)
                }
                serviceBlockDataMap[service] = mergedBlockData
            }
            // Also add services that only have extra blocks (no regular blocks)
            extraBlockDataByService.forEach { (service, extraBlocks) ->
                if (!serviceBlockDataMap.containsKey(service)) {
                    serviceBlockDataMap[service] = extraBlocks
                }
            }

            // Update system context with block data
            val updatedSystemContext = systemContext.copy(serviceBlockData = serviceBlockDataMap)
            updatedSystemContexts.add(updatedSystemContext)

            // Update totals (using merged data)
            val contextBlocksRead = serviceBlockDataMap.values.sumOf { it.size }
            totalBlocksRead += contextBlocksRead
            totalServicesProcessed += serviceBlockDataMap.size

            // Build context-specific results
            val contextResult = buildString {
                appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                appendLine("  Blocks read: $contextBlocksRead")
                appendLine("  Services processed: ${serviceBlockDataMap.size}")
                appendLine()

                serviceBlockDataMap.forEach { (node, blockData) ->
                    val service = node as? Service ?: return@forEach
                    val blockCount = blockData.size
                    val regularBlocks = blockData.keys.filter { it < 0x80 }.size
                    val extraBlocks = blockData.keys.filter { it >= 0x80 }.size
                    appendLine(
                        "  Service ${service.code.toHexString()}: $blockCount blocks ($regularBlocks regular, $extraBlocks extra)"
                    )
                    if (blockData.isNotEmpty()) {
                        // Show first few block numbers and their data
                        val previewBlocks = blockData.entries.sortedBy { it.key }.take(4)
                        previewBlocks.forEach { (blockNum, data) ->
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
            contextResults.add(contextResult)
        }

        // Update scan context with all system contexts and global limits
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        // Format results
        val collapsedResult =
            "Read $totalBlocksRead blocks from $totalServicesProcessed services across ${updatedSystemContexts.size} system(s)"

        val expandedResult =
            buildString {
                    appendLine("Block Reading Results:")
                    appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                    appendLine("Total blocks read: $totalBlocksRead")
                    appendLine("Total services processed: $totalServicesProcessed")
                    appendLine("Max blocks per request: $maxBlocksPerRequest")
                    appendLine("Max services per request: $maxServicesPerRequest")
                    appendLine()

                    contextResults.forEach { result -> appendLine(result) }

                    appendLine(
                        "Note: Only services that don't require authentication are processed."
                    )
                    appendLine(
                        "Block data is stored per system context for comprehensive analysis."
                    )
                }
                .trim()

        return StepOutput(result = expandedResult, collapsedResult = collapsedResult)
    }

    private const val TAG = "ReadBlocksWithoutEncryptionStep"
    private const val STATUS_FLAG2_SUCCESS = 0x00.toByte()
    private const val STATUS_FLAG2_OK_MEMORY_WARNING = 0x71.toByte()
    private const val ILLEGAL_NUMBER_OF_SERVICE = 0xA1.toByte()
    private const val ILLEGAL_NUMBER_OF_BLOCK = 0xA2.toByte()
    private const val ILLEGAL_BLOCK_NUMBER = 0xA8.toByte()
    private const val ILLEGAL_BLOCK_LIST_SERVICE_ORDER = 0xA3.toByte()
    private const val BLOCK_WITH_AUTHENTICATION_READ_BEFORE_AUTHENTICATION_COMPLETE = 0xB1.toByte()
    private const val BLOCK_SIZE = 16 // Each block is 16 bytes
    private const val MAX_CONSECUTIVE_FAILURES = 32

    private fun isReadStatusFlag2Successful(statusFlag2: Byte): Boolean =
        statusFlag2 == STATUS_FLAG2_SUCCESS || statusFlag2 == STATUS_FLAG2_OK_MEMORY_WARNING

    /**
     * Reads blocks from services that don't require authentication
     *
     * @return Map of Service to Map of block number to block data (16 bytes each)
     */
    private suspend fun ScanSession.readBlocksFromServices(
        services: List<Service>,
        systemCode: ByteArray?,
        errorLocationIndication: ErrorLocationIndication,
        maxBlocksPerRequest: Int,
        maxServicesPerRequest: Int,
    ): Map<Service, Map<Int, ByteArray>> {
        val blockDataByService = mutableMapOf<Service, MutableMap<Int, ByteArray>>()
        val blockCountByService = mutableMapOf<Service, Int>()

        // Initialize block count for each service (we'll discover this dynamically)
        services.forEach { service ->
            blockCountByService[service] = Int.MAX_VALUE // Start with max, adjust as we go
        }

        var maxBlocks = maxBlocksPerRequest
        if (errorLocationIndication == ErrorLocationIndication.BITMASK) {
            maxBlocks = 8
            ScanLog.w(
                TAG,
                "BITMASK mode: Adjusting max block per read to $maxBlocks to avoid ambiguity",
            )
        }

        var maxServices = maxServicesPerRequest
        var consecutiveFailures = 0
        while (true) {
            val servicesToRead = mutableListOf<Service>()
            val blocksToRead = mutableListOf<BlockListElement>()

            // Prepare the next batch of blocks to read
            for (service in services) {
                if (servicesToRead.size >= maxServices) break
                if (servicesToRead.size >= maxBlocks) break
                // Check if a service with the same number is already in the current request
                // if (servicesToRead.map { it.number }.contains(service.number)) continue

                val serviceBlocks = blockDataByService[service]
                val blocksAlreadyRead =
                    if (serviceBlocks.isNullOrEmpty()) {
                        0
                    } else {
                        var nextBlock = 0
                        while (serviceBlocks.containsKey(nextBlock)) {
                            nextBlock++
                        }
                        nextBlock
                    }
                val maxBlocksForService = blockCountByService[service] ?: Int.MAX_VALUE

                if (blocksAlreadyRead >= maxBlocksForService) continue

                val requestBlocksRemaining = maxBlocks - blocksToRead.size
                if (requestBlocksRemaining <= 0) break

                val blockNumbersToRead = mutableListOf<Int>()
                var candidateBlockNumber = blocksAlreadyRead
                while (
                    candidateBlockNumber < maxBlocksForService &&
                        blockNumbersToRead.size < requestBlocksRemaining
                ) {
                    if (serviceBlocks?.containsKey(candidateBlockNumber) != true) {
                        blockNumbersToRead.add(candidateBlockNumber)
                    }
                    candidateBlockNumber++
                }

                if (blockNumbersToRead.isNotEmpty()) {
                    servicesToRead.add(service)
                    val serviceCodeListOrder = servicesToRead.size - 1

                    // Create block list elements for this service
                    for (blockNumber in blockNumbersToRead) {

                        // Create block list element (normal format, 2 bytes)
                        val blockElement =
                            BlockListElement(
                                serviceCodeListOrder = serviceCodeListOrder,
                                blockNumber = blockNumber,
                                accessMode = BlockListElement.AccessMode.NORMAL,
                                extended = false,
                            )
                        blocksToRead.add(blockElement)
                    }
                }
            }

            if (servicesToRead.isEmpty() || blocksToRead.isEmpty()) {
                break
            }

            // Circuit breaker: if too many consecutive failures, break out
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                ScanLog.w(
                    TAG,
                    "Circuit breaker triggered: $consecutiveFailures consecutive failures, stopping read operation",
                )
                break
            }

            ScanLog.d(
                TAG,
                "Prepared block request with ${servicesToRead.size} ${servicesToRead} services and ${blocksToRead.size} ${blocksToRead} blocks",
            )
            try {
                // Create the Read Without Encryption command manually
                val readCommand =
                    ReadWithoutEncryptionCommand(
                        idm = target.idm,
                        serviceCodes = servicesToRead.map { it.code }.toTypedArray(),
                        blockListElements = blocksToRead.toTypedArray(),
                    )

                val response =
                    transceiveWithRetries(
                        target = target,
                        command = readCommand,
                        systemCode = systemCode,
                    )

                // Check status flags
                val statusFlag1 = response.statusFlag1
                val statusFlag2 = response.statusFlag2

                ScanLog.d(
                    TAG,
                    "Read response status: 0x${byteToHex(statusFlag1)} 0x${byteToHex(statusFlag2)}",
                )

                if (statusFlag2 == ILLEGAL_BLOCK_LIST_SERVICE_ORDER) {
                    consecutiveFailures++
                    throw RuntimeException("Illegal block list service order")
                }

                if (statusFlag2 == ILLEGAL_NUMBER_OF_SERVICE) {
                    consecutiveFailures++
                    // Reduce max services and retry
                    if (maxServices > 1) {
                        maxServices = servicesToRead.size - 1
                        ScanLog.d(
                            TAG,
                            "Adjusting max services to $maxServices due to ILLEGAL_NUMBER_OF_SERVICE",
                        )
                        continue
                    } else {
                        throw RuntimeException("Cannot reduce services further (already at 1)")
                    }
                }

                if (statusFlag2 == ILLEGAL_NUMBER_OF_BLOCK) {
                    consecutiveFailures++
                    when (errorLocationIndication) {
                        ErrorLocationIndication.FLAG -> {
                            val currentLen = blocksToRead.size
                            maxBlocks =
                                (maxBlocksPerRequest downTo 1).firstOrNull { it < currentLen } ?: 1
                            ScanLog.d(
                                TAG,
                                "FLAG mode: Adjusting max blocks readable at once to $maxBlocks",
                            )
                        }

                        ErrorLocationIndication.INDEX -> {
                            // Adjust max block size and retry
                            val currentLen = blocksToRead.size
                            maxBlocks = statusFlag1.toInt() and 0xFF - 1
                            ScanLog.d(
                                TAG,
                                "NUMBER mode: Adjusting max blocks readable at once to $maxBlocks",
                            )
                            continue
                        }

                        ErrorLocationIndication.BITMASK -> {
                            val errorBitmask = statusFlag1.toInt() and 0xFF
                            ScanLog.d(
                                TAG,
                                "BITMASK mode: Error bitmask = 0x${byteToHex(errorBitmask)}",
                            )
                            // Find the highest invalid block index from the bitmask
                            var highestInvalidIndex = -1
                            for (bitIndex in 0 until 8) {
                                if ((errorBitmask and (1 shl bitIndex)) != 0) {
                                    // Find the highest block index that this bit represents
                                    var blockIndex = bitIndex
                                    while (blockIndex + 8 < blocksToRead.size) {
                                        blockIndex += 8
                                    }
                                    if (
                                        blockIndex < blocksToRead.size &&
                                            blockIndex > highestInvalidIndex
                                    ) {
                                        highestInvalidIndex = blockIndex
                                    }
                                }
                            }

                            if (highestInvalidIndex >= 0) {
                                maxBlocks = highestInvalidIndex
                                ScanLog.d(
                                    TAG,
                                    "BITMASK mode: Adjusting max blocks readable at once to $maxBlocks (highest invalid block at index $highestInvalidIndex)",
                                )
                            }
                            continue
                        }
                    }
                }

                if (statusFlag2 == ILLEGAL_BLOCK_NUMBER) {
                    consecutiveFailures++
                    when (errorLocationIndication) {
                        ErrorLocationIndication.FLAG -> {
                            if (statusFlag1.toByte() != 0xFF.toByte()) {
                                throw RuntimeException(
                                    "Got ErrorLocationIndication.FLAG, but statusFlag1 is not 0xFF $statusFlag1"
                                )
                            }
                            ScanLog.d(
                                TAG,
                                "FLAG mode: Adjusting settings max blocks per read to 1, as unable to determine which exact block is problematic",
                            )
                            maxBlocks = 1
                            maxServices = 1
                        }

                        ErrorLocationIndication.INDEX -> {
                            // Adjust block count for the problematic service (indexed method)
                            val blockIndex = statusFlag1.toInt() and 0xFF
                            if (blockIndex > blocksToRead.size) {
                                throw RuntimeException(
                                    "Got ErrorLocationIndication.INDEX, but block index is out of bounds: $blockIndex"
                                )
                            }
                            val blockElement = blocksToRead[blockIndex - 1]
                            val service = servicesToRead[blockElement.serviceCodeListOrder]
                            blockCountByService[service] = blockElement.blockNumber
                            ScanLog.d(
                                TAG,
                                "NUMBER mode: Adjusting blocks in service ${service} to ${blockCountByService[service]} (block index: $blockIndex)",
                            )
                        }

                        ErrorLocationIndication.BITMASK -> {
                            val errorBitmask = statusFlag1.toInt() and 0xFF
                            ScanLog.d(
                                TAG,
                                "BITMASK mode: Error bitmask = 0x${byteToHex(errorBitmask)}",
                            )

                            // Process each bit in the bitmask
                            for (bitIndex in 0 until 8) {
                                if ((errorBitmask and (1 shl bitIndex)) == 0) {
                                    continue
                                }
                                // This bit is set, indicating an error with the corresponding
                                // block
                                if (bitIndex >= blocksToRead.size) {
                                    throw RuntimeException(
                                        "Got ErrorLocationIndication.BITMASK, but bit index is out of bounds: $bitIndex"
                                    )
                                }

                                val blockElement = blocksToRead[bitIndex]
                                val service = servicesToRead[blockElement.serviceCodeListOrder]
                                val currentMaxBlocks = blockCountByService[service] ?: Int.MAX_VALUE
                                val newMaxBlocks = blockElement.blockNumber

                                // Only update if we're reducing the block count
                                if (newMaxBlocks >= currentMaxBlocks) {
                                    throw RuntimeException(
                                        "Got ErrorLocationIndication.BITMASK, but new max blocks is not less than current for service ${service}: current $currentMaxBlocks, new $newMaxBlocks"
                                    )
                                }
                                blockCountByService[service] = newMaxBlocks
                                ScanLog.d(
                                    TAG,
                                    "BITMASK mode: Adjusting blocks in service ${service} to ${newMaxBlocks} (bit $bitIndex)",
                                )
                            }
                        }
                    }
                    continue
                }

                if (statusFlag2 == BLOCK_WITH_AUTHENTICATION_READ_BEFORE_AUTHENTICATION_COMPLETE) {
                    consecutiveFailures++
                    // The block exists but requires authentication — skip it by storing an
                    // empty
                    // sentinel so the sequential block counter advances past it
                    when (errorLocationIndication) {
                        ErrorLocationIndication.FLAG -> {
                            if (blocksToRead.size == 1) {
                                val blockElement = blocksToRead[0]
                                val service = servicesToRead[blockElement.serviceCodeListOrder]
                                blockDataByService
                                    .getOrPut(service) { mutableMapOf() }[
                                        blockElement.blockNumber] = ByteArray(0)
                                ScanLog.d(
                                    TAG,
                                    "FLAG mode: Skipping block ${blockElement.blockNumber} of service $service (authentication required)",
                                )
                            } else {
                                ScanLog.d(
                                    TAG,
                                    "FLAG mode: Adjusting settings max blocks per read to 1, as unable to determine which exact block is problematic",
                                )
                                maxBlocks = 1
                                maxServices = 1
                            }
                        }
                        ErrorLocationIndication.INDEX -> {
                            val blockIndex = statusFlag1.toInt() and 0xFF
                            if (blockIndex > 0 && blockIndex <= blocksToRead.size) {
                                val blockElement = blocksToRead[blockIndex - 1]
                                val service = servicesToRead[blockElement.serviceCodeListOrder]
                                blockDataByService
                                    .getOrPut(service) { mutableMapOf() }[
                                        blockElement.blockNumber] = ByteArray(0)
                                ScanLog.d(
                                    TAG,
                                    "INDEX mode: Skipping block ${blockElement.blockNumber} of service $service (authentication required, index $blockIndex)",
                                )
                            }
                        }
                        ErrorLocationIndication.BITMASK -> {
                            val errorBitmask = statusFlag1.toInt() and 0xFF
                            ScanLog.d(
                                TAG,
                                "BITMASK mode: Auth error bitmask = 0x${byteToHex(errorBitmask)}",
                            )
                            for (bitIndex in 0 until 8) {
                                if ((errorBitmask and (1 shl bitIndex)) == 0) continue
                                if (bitIndex >= blocksToRead.size) continue
                                val blockElement = blocksToRead[bitIndex]
                                val service = servicesToRead[blockElement.serviceCodeListOrder]
                                blockDataByService
                                    .getOrPut(service) { mutableMapOf() }[
                                        blockElement.blockNumber] = ByteArray(0)
                                ScanLog.d(
                                    TAG,
                                    "BITMASK mode: Skipping block ${blockElement.blockNumber} of service $service (authentication required, bit $bitIndex)",
                                )
                            }
                        }
                    }
                    continue
                }

                if (!isReadStatusFlag2Successful(statusFlag2)) {
                    consecutiveFailures++
                    if (maxBlocks > 1) {
                        maxBlocks = 1
                        ScanLog.w(
                            TAG,
                            "Unhandled read response status 0x${byteToHex(statusFlag1)} 0x${byteToHex(statusFlag2)}: reducing max blocks per request to 1 and retrying",
                        )
                        continue
                    }
                    val failedBlockElement =
                        blocksToRead.firstOrNull()
                            ?: throw IllegalStateException(
                                "Unhandled read status with max blocks already at 1 but request block list is empty"
                            )
                    val failedService =
                        servicesToRead.getOrNull(failedBlockElement.serviceCodeListOrder)
                            ?: throw IllegalStateException(
                                "Unhandled read status with invalid serviceCodeListOrder=${failedBlockElement.serviceCodeListOrder}, services=${servicesToRead.size}"
                            )
                    blockCountByService[failedService] = failedBlockElement.blockNumber
                    ScanLog.w(
                        TAG,
                        "Unhandled read response status 0x${byteToHex(statusFlag1)} 0x${byteToHex(statusFlag2)} with max blocks already at 1: marking service $failedService as checked up to block ${failedBlockElement.blockNumber}",
                    )
                    continue
                }

                // Process successful response
                consecutiveFailures = 0 // Reset failure counter on successful read
                val blockDataArray = response.blockData

                // Distribute block data to services
                var dataOffset = 0
                blocksToRead.forEachIndexed { index, blockElement ->
                    val serviceIndex = blockElement.serviceCodeListOrder
                    if (serviceIndex < servicesToRead.size && dataOffset < blockDataArray.size) {
                        val service = servicesToRead[serviceIndex]
                        val blockDataChunk = blockDataArray[dataOffset]
                        val blockNumber = blockElement.blockNumber

                        // Store block data by block number
                        val serviceBlocks = blockDataByService.getOrPut(service) { mutableMapOf() }
                        serviceBlocks[blockNumber] = blockDataChunk

                        dataOffset++
                    }
                }

                ScanLog.d(TAG, "Successfully read ${blocksToRead.size} blocks")
            } catch (e: Exception) {
                consecutiveFailures++
                ScanLog.e(TAG, "Error reading blocks", e)
                break
            }
        }

        return blockDataByService
    }

    private suspend fun ScanSession.readExtraBlocksFromServices(
        services: List<Service>,
        systemCode: ByteArray?,
        extraBlocksByServiceCode: Map<String, Map<Int, String>>,
    ): Map<Service, Map<Int, ByteArray>> {
        val blockDataByService = mutableMapOf<Service, MutableMap<Int, ByteArray>>()

        // Read extra blocks for services that have them configured
        for (service in services) {
            val extraBlocks =
                extraBlocksByServiceCode[service.code.toHexString().uppercase()] ?: continue

            if (extraBlocks.isEmpty()) continue

            ScanLog.d(TAG, "Reading ${extraBlocks.size} extra blocks for service $service")

            // Read extra blocks one at a time to handle potential errors gracefully
            for ((blockNumber, blockName) in extraBlocks.entries.sortedBy { it.key }) {
                try {
                    // Use extended format if block number > 255
                    val extended = blockNumber > 255
                    val blockElement =
                        BlockListElement(
                            serviceCodeListOrder = 0,
                            blockNumber = blockNumber,
                            accessMode = BlockListElement.AccessMode.NORMAL,
                            extended = extended,
                        )

                    val readCommand =
                        ReadWithoutEncryptionCommand(
                            idm = target.idm,
                            serviceCodes = arrayOf(service.code),
                            blockListElements = arrayOf(blockElement),
                        )

                    val response =
                        transceiveWithRetries(
                            target = target,
                            command = readCommand,
                            systemCode = systemCode,
                        )

                    if (
                        isReadStatusFlag2Successful(response.statusFlag2) &&
                            response.blockData.isNotEmpty()
                    ) {
                        val serviceBlocks = blockDataByService.getOrPut(service) { mutableMapOf() }
                        serviceBlocks[blockNumber] = response.blockData[0]
                        ScanLog.d(
                            TAG,
                            "Successfully read extra block 0x${blockNumber.toString(16).uppercase()} ($blockName) for service $service",
                        )
                    } else {
                        ScanLog.d(
                            TAG,
                            "Failed to read extra block 0x${formatBlockNumberHex(blockNumber)} ($blockName) for service $service: status=${byteToHex(response.statusFlag1)},${byteToHex(response.statusFlag2)}",
                        )
                    }
                } catch (e: Exception) {
                    ScanLog.e(
                        TAG,
                        "Error reading extra block 0x${blockNumber.toString(16).uppercase()} ($blockName) for service $service",
                        e,
                    )
                }
            }
        }

        return blockDataByService
    }
}
