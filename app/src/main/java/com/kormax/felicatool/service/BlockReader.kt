package com.kormax.felicatool.service

import android.util.Log
import com.kormax.felicatool.felica.BlockListElement
import com.kormax.felicatool.felica.ErrorLocationIndication
import com.kormax.felicatool.felica.FeliCaTarget
import com.kormax.felicatool.felica.ReadWithoutEncryptionCommand
import com.kormax.felicatool.felica.Service
import kotlin.math.min

/** Utility class for reading blocks from FeliCa services that don't require encryption */
class BlockReader(
    private val target: FeliCaTarget,
    private val errorLocationIndication: ErrorLocationIndication = ErrorLocationIndication.INDEX,
    private val maxBlocksPerRequest: Int = 15,
    private val maxServicesPerRequest: Int = 16,
    private val extraBlocksByServiceCode: Map<String, Map<Int, String>> = emptyMap(),
    /**
     * Extra blocks to read for each service, keyed by service code hex string, value is map of
     * block number to name
     */
) {

    companion object {
        private const val TAG = "BlockReader"
        private const val ILLEGAL_NUMBER_OF_SERVICE = 0xA1.toByte()
        private const val ILLEGAL_NUMBER_OF_BLOCK = 0xA2.toByte()
        private const val ILLEGAL_BLOCK_NUMBER = 0xA8.toByte()
        private const val ILLEGAL_BLOCK_LIST_SERVICE_ORDER = 0xA3.toByte()
        private const val BLOCK_SIZE = 16 // Each block is 16 bytes
        private const val MAX_CONSECUTIVE_FAILURES = 24
    }

    /**
     * Reads blocks from services that don't require authentication
     *
     * @return Map of Service to Map of block number to block data (16 bytes each)
     */
    suspend fun readBlocksFromServices(services: List<Service>): Map<Service, Map<Int, ByteArray>> {
        val blockDataByService = mutableMapOf<Service, MutableMap<Int, ByteArray>>()
        val blockCountByService = mutableMapOf<Service, Int>()

        // Initialize block count for each service (we'll discover this dynamically)
        services.forEach { service ->
            blockCountByService[service] = Int.MAX_VALUE // Start with max, adjust as we go
        }

        var maxBlocks = maxBlocksPerRequest
        if (errorLocationIndication == ErrorLocationIndication.BITMASK) {
            maxBlocks = 8
            Log.w(
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

                val blocksAlreadyRead = blockDataByService[service]?.size ?: 0
                val maxBlocksForService = blockCountByService[service] ?: Int.MAX_VALUE

                if (blocksAlreadyRead >= maxBlocksForService) continue

                val blocksToReadForService = maxBlocksForService - blocksAlreadyRead
                val blocksToReadForRequest =
                    min(blocksToReadForService, maxBlocks - blocksToRead.size)

                if (blocksToReadForRequest > 0) {
                    servicesToRead.add(service)

                    // Create block list elements for this service
                    for (i in 0 until blocksToReadForRequest) {
                        val blockNumber = blocksAlreadyRead + i
                        val serviceCodeListOrder = servicesToRead.indexOf(service)

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
                Log.w(
                    TAG,
                    "Circuit breaker triggered: $consecutiveFailures consecutive failures, stopping read operation",
                )
                break
            }

            Log.d(
                TAG,
                "Prepared block request with ${servicesToRead.size} ${servicesToRead} services and ${blocksToRead.size} ${blocksToRead} blocks",
            )
            try {
                // Create the Read Without Encryption command manually
                val readCommand =
                    ReadWithoutEncryptionCommand(
                        idm = target.idm,
                        serviceCodes = servicesToRead.map { it -> it.code }.toTypedArray(),
                        blockListElements = blocksToRead.toTypedArray(),
                    )

                val response = target.transceive(readCommand)

                // Check status flags
                val statusFlag1 = response.statusFlag1
                val statusFlag2 = response.statusFlag2

                Log.d(
                    TAG,
                    "Read response status: 0x${
                        statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')
                    } 0x${statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}",
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
                        Log.d(
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
                            Log.d(
                                TAG,
                                "FLAG mode: Adjusting max blocks readable at once to $maxBlocks",
                            )
                        }

                        ErrorLocationIndication.INDEX -> {
                            // Adjust max block size and retry
                            val currentLen = blocksToRead.size
                            maxBlocks = statusFlag1.toInt() and 0xFF - 1
                            Log.d(
                                TAG,
                                "NUMBER mode: Adjusting max blocks readable at once to $maxBlocks",
                            )
                            continue
                        }

                        ErrorLocationIndication.BITMASK -> {
                            val errorBitmask = statusFlag1.toInt() and 0xFF
                            Log.d(
                                TAG,
                                "BITMASK mode: Error bitmask = 0x${
                                    errorBitmask.toString(16).uppercase().padStart(2, '0')
                                }",
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
                                Log.d(
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
                            Log.d(
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
                            Log.d(
                                TAG,
                                "NUMBER mode: Adjusting blocks in service ${service} to ${blockCountByService[service]} (block index: $blockIndex)",
                            )
                        }

                        ErrorLocationIndication.BITMASK -> {
                            val errorBitmask = statusFlag1.toInt() and 0xFF
                            Log.d(
                                TAG,
                                "BITMASK mode: Error bitmask = 0x${
                                    errorBitmask.toString(16).uppercase().padStart(2, '0')
                                }",
                            )

                            // Process each bit in the bitmask
                            for (bitIndex in 0 until 8) {
                                if ((errorBitmask and (1 shl bitIndex)) == 0) {
                                    continue
                                }
                                // This bit is set, indicating an error with the corresponding block
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
                                Log.d(
                                    TAG,
                                    "BITMASK mode: Adjusting blocks in service ${service} to ${newMaxBlocks} (bit $bitIndex)",
                                )
                            }
                        }
                    }
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

                Log.d(TAG, "Successfully read ${blocksToRead.size} blocks")
            } catch (e: Exception) {
                consecutiveFailures++
                Log.e(TAG, "Error reading blocks", e)
                break
            }
        }

        return blockDataByService
    }

    suspend fun readExtraBlocksFromServices(
        services: List<Service>
    ): Map<Service, Map<Int, ByteArray>> {
        val blockDataByService = mutableMapOf<Service, MutableMap<Int, ByteArray>>()

        // Read extra blocks for services that have them configured
        for (service in services) {
            val extraBlocks =
                extraBlocksByServiceCode[service.code.toHexString().uppercase()] ?: continue

            if (extraBlocks.isEmpty()) continue

            Log.d(TAG, "Reading ${extraBlocks.size} extra blocks for service $service")

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

                    val response = target.transceive(readCommand)

                    if (response.isStatusSuccessful && response.blockData.isNotEmpty()) {
                        val serviceBlocks = blockDataByService.getOrPut(service) { mutableMapOf() }
                        serviceBlocks[blockNumber] = response.blockData[0]
                        Log.d(
                            TAG,
                            "Successfully read extra block 0x${blockNumber.toString(16).uppercase()} ($blockName) for service $service",
                        )
                    } else {
                        Log.d(
                            TAG,
                            "Failed to read extra block 0x${blockNumber.toString(16).uppercase()} ($blockName) for service $service: status=${response.statusFlag1.toUByte().toString(16)},${response.statusFlag2.toUByte().toString(16)}",
                        )
                    }
                } catch (e: Exception) {
                    Log.e(
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
