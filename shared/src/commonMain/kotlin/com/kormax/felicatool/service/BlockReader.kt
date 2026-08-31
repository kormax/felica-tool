package com.kormax.felicatool.service

import com.kormax.felicatool.felica.BlockListElement
import com.kormax.felicatool.felica.ErrorLocationIndication
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.felica.Status
import kotlinx.coroutines.CancellationException

private const val MAX_BLOCK_NUMBER = 0xFFFF

internal class BlockReadCandidate(
    val blockIndices: Set<Int>,
    val probeAfterRuns: Boolean,
) {
    init {
        require(blockIndices.isNotEmpty()) { "A block-read candidate must not be empty" }
        require(blockIndices.all { it in 0..MAX_BLOCK_NUMBER }) {
            "Block indices must fit in an extended block-list element"
        }
    }
}

/**
 * Reads blocks from a fixed set of services. Subclasses only send one batch using their transport;
 * this class owns candidate selection, probing, batching, and error-location handling.
 */
internal abstract class BlockReader(
    private val maxBlocksPerRequest: Int,
    private val maxServicesPerRequest: Int,
    private val errorLocationIndication: ErrorLocationIndication,
) {
    init {
        require(maxBlocksPerRequest > 0) { "Maximum blocks per request must be positive" }
        require(maxServicesPerRequest > 0) { "Maximum services per request must be positive" }
    }

    protected class BatchResult(
        val status: Status,
        val blocks: List<ByteArray>,
    )

    protected abstract suspend fun readBatch(
        services: List<Service>,
        blocks: List<BlockListElement>,
    ): BatchResult

    suspend fun read(
        services: List<Service>,
        hints: Map<Service, List<BlockReadCandidate>> = emptyMap(),
    ): Map<Service, Map<Int, ByteArray>> {
        require(
            hints.all { (service, candidates) ->
                service in services && candidates.isNotEmpty()
            }
        )

        val blocksByService = mutableMapOf<Service, MutableMap<Int, ByteArray>>()
        val states = services.associateWith { service ->
            hints[service]?.let(::HintReadState) ?: SequentialReadState()
        }

        var maxBlocks = maxBlocksPerRequest
        if (errorLocationIndication == ErrorLocationIndication.BITMASK) {
            maxBlocks = minOf(maxBlocks, BITMASK_MAX_BLOCKS)
            ScanLog.w(
                TAG,
                "BITMASK mode: Adjusting max blocks per read to $maxBlocks to avoid ambiguity",
            )
        }

        var maxServices = maxServicesPerRequest
        var consecutiveFailures = 0
        while (true) {
            val batchServices = mutableListOf<Service>()
            val batchBlocks = mutableListOf<BlockListElement>()
            val batchTargets = mutableListOf<ReadTarget>()

            for (service in services) {
                if (batchServices.size >= maxServices) break

                val availableSlots = maxBlocks - batchBlocks.size
                if (availableSlots <= 0) break

                val targets =
                    states
                        .getValue(service)
                        .targets(service, blocksByService[service], availableSlots)
                if (targets.isEmpty()) continue

                batchServices += service
                for (target in targets) {
                    batchTargets += target
                    batchBlocks +=
                        BlockListElement(
                            serviceCodeListOrder = batchServices.lastIndex,
                            blockNumber = target.blockNumber,
                            accessMode = BlockListElement.AccessMode.NORMAL,
                            extended = target.blockNumber > 0xFF,
                        )
                }
            }

            if (batchTargets.isEmpty()) break
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                ScanLog.w(
                    TAG,
                    "Circuit breaker triggered after $consecutiveFailures consecutive failures",
                )
                break
            }

            ScanLog.d(
                TAG,
                "Reading ${batchBlocks.size} block(s) from ${batchServices.size} service(s)",
            )
            try {
                val response = readBatch(batchServices, batchBlocks)

                val status = response.status
                val statusFlag1 = status.statusFlag1
                ScanLog.d(
                    TAG,
                    "Read response status: 0x${byteToHex(statusFlag1)} 0x${byteToHex(status.statusFlag2)}",
                )

                if (status is Status.IllegalBlockListServiceOrder) {
                    throw RuntimeException("Illegal block list service order")
                }

                if (status is Status.IllegalNumberOfService) {
                    consecutiveFailures++
                    if (batchServices.size > 1) {
                        maxServices = batchServices.size - 1
                        ScanLog.d(
                            TAG,
                            "Adjusting max services to $maxServices due to ILLEGAL_NUMBER_OF_SERVICE",
                        )
                        continue
                    }
                    throw RuntimeException("Cannot reduce services further")
                }

                if (status is Status.IllegalNumberOfBlock) {
                    consecutiveFailures++
                    when (errorLocationIndication) {
                        ErrorLocationIndication.FLAG -> {
                            if (batchBlocks.size == 1) {
                                throw RuntimeException("Cannot reduce blocks further")
                            }
                            maxBlocks = minOf(maxBlocks, batchBlocks.size - 1)
                        }

                        ErrorLocationIndication.INDEX -> {
                            maxBlocks =
                                minOf(maxBlocks, (statusFlag1.toInt() and 0xFF) - 1)
                                    .coerceAtLeast(1)
                        }

                        ErrorLocationIndication.BITMASK -> {
                            val errorBitmask = statusFlag1.toInt() and 0xFF
                            val highestInvalidIndex =
                                (BITMASK_MAX_BLOCKS - 1 downTo 1).firstOrNull { bitIndex ->
                                    errorBitmask and (1 shl bitIndex) != 0
                                }
                                    ?: throw RuntimeException(
                                        "Cannot determine a smaller block batch"
                                    )
                            maxBlocks = minOf(maxBlocks, highestInvalidIndex)
                        }
                    }
                    ScanLog.d(TAG, "Adjusting max blocks to $maxBlocks")
                    continue
                }

                if (status is Status.IllegalBlockNumber) {
                    if (errorLocationIndication == ErrorLocationIndication.FLAG) {
                        require(statusFlag1 == 0xFF.toByte()) {
                            "FLAG mode requires status flag 1 to be FF, got ${byteToHex(statusFlag1)}"
                        }
                    }

                    val failedTargets = locateErrorTargets(statusFlag1, batchTargets)
                    if (failedTargets == null) {
                        val service = batchTargets.first().service
                        val state = states.getValue(service)
                        if (
                            state !is HintReadState ||
                                batchTargets.any { it.service != service || it.isProbe } ||
                                !state.advanceCandidate()
                        ) {
                            maxBlocks = 1
                        }
                    } else {
                        failedTargets.forEach { target ->
                            states.getValue(target.service).reject(target, tryNextCandidate = true)
                        }
                    }
                    continue
                }

                if (status is Status.AuthenticationRequired) {
                    val skippedTargets = locateErrorTargets(statusFlag1, batchTargets)
                    if (skippedTargets == null) {
                        maxBlocks = 1
                    } else {
                        for (target in skippedTargets) {
                            blocksByService
                                .getOrPut(target.service) { mutableMapOf() }[target.blockNumber] =
                                ByteArray(0)
                            states.getValue(target.service).accept(target)
                        }
                    }
                    continue
                }

                if (status is Status.RandomChallengeWriteRequired) {
                    val unavailableTargets = locateErrorTargets(statusFlag1, batchTargets)
                    if (unavailableTargets == null) {
                        maxBlocks = 1
                    } else {
                        unavailableTargets.forEach { target ->
                            states.getValue(target.service).reject(target, tryNextCandidate = false)
                        }
                    }
                    continue
                }

                val failureReason =
                    when {
                        status !is Status.Success -> "Unhandled read status"
                        response.blocks.size < batchTargets.size ->
                            "Successful read returned ${response.blocks.size} of ${batchTargets.size} requested blocks"
                        else -> null
                    }
                if (failureReason != null) {
                    consecutiveFailures++
                    if (maxBlocks > 1) {
                        maxBlocks = 1
                        ScanLog.w(TAG, "$failureReason; retrying one block at a time")
                        continue
                    }
                    val failedTarget = batchTargets.first()
                    states
                        .getValue(failedTarget.service)
                        .reject(failedTarget, tryNextCandidate = false)
                    ScanLog.w(
                        TAG,
                        "$failureReason; stopping target ${failedTarget.blockNumber} of ${failedTarget.service}",
                    )
                    continue
                }

                consecutiveFailures = 0
                batchTargets.forEachIndexed { index, target ->
                    blocksByService
                        .getOrPut(target.service) { mutableMapOf() }[target.blockNumber] =
                        response.blocks[index]
                    states.getValue(target.service).accept(target)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ScanLog.e(TAG, "Error reading blocks", error)
                break
            }
        }

        return blocksByService
    }

    private fun locateErrorTargets(
        statusFlag1: Byte,
        targets: List<ReadTarget>,
    ): List<ReadTarget>? =
        when (errorLocationIndication) {
            ErrorLocationIndication.FLAG -> targets.takeIf { it.size == 1 }
            ErrorLocationIndication.INDEX -> {
                val index = statusFlag1.toInt() and 0xFF
                require(index in 1..targets.size) { "Block index is out of bounds: $index" }
                listOf(targets[index - 1])
            }
            ErrorLocationIndication.BITMASK -> {
                val bitmask = statusFlag1.toInt() and 0xFF
                buildList {
                    for (index in 0 until BITMASK_MAX_BLOCKS) {
                        if ((bitmask and (1 shl index)) == 0) continue
                        require(index < targets.size) { "Bit index is out of bounds: $index" }
                        add(targets[index])
                    }
                    require(isNotEmpty()) { "Error bitmask does not identify a block" }
                }
            }
        }

    private sealed interface ServiceReadState {
        fun targets(
            service: Service,
            blocks: Map<Int, ByteArray>?,
            limit: Int,
        ): List<ReadTarget>

        fun accept(target: ReadTarget) = Unit

        fun reject(target: ReadTarget, tryNextCandidate: Boolean)
    }

    private class SequentialReadState : ServiceReadState {
        private var blockCount = Int.MAX_VALUE

        override fun targets(
            service: Service,
            blocks: Map<Int, ByteArray>?,
            limit: Int,
        ): List<ReadTarget> = buildList {
            var blockNumber = 0
            while (blocks?.containsKey(blockNumber) == true) blockNumber++
            while (blockNumber < blockCount && size < limit) {
                if (blocks?.containsKey(blockNumber) != true) {
                    add(ReadTarget(service, blockNumber))
                }
                blockNumber++
            }
        }

        override fun reject(target: ReadTarget, tryNextCandidate: Boolean) {
            blockCount = minOf(blockCount, target.blockNumber)
        }
    }

    private class HintReadState(private val candidates: List<BlockReadCandidate>) :
        ServiceReadState {
        private var candidateIndex = 0
        private val unavailableBlocks = mutableSetOf<Int>()
        private var probeFrontiers: MutableSet<Int>? = null

        override fun targets(
            service: Service,
            blocks: Map<Int, ByteArray>?,
            limit: Int,
        ): List<ReadTarget> {
            val candidate = candidates[candidateIndex]
            if (
                probeFrontiers == null &&
                    candidate.blockIndices.all { blockNumber ->
                        blocks?.containsKey(blockNumber) == true || blockNumber in unavailableBlocks
                    }
            ) {
                probeFrontiers =
                    if (unavailableBlocks.isNotEmpty() || !candidate.probeAfterRuns) {
                        mutableSetOf()
                    } else {
                        candidate.blockIndices.filterTo(mutableSetOf()) { blockNumber ->
                            blockNumber < MAX_BLOCK_NUMBER &&
                                blockNumber + 1 !in candidate.blockIndices
                        }
                    }
            }
            probeFrontiers?.let { frontiers ->
                val nextFrontiers =
                    frontiers.mapNotNullTo(mutableSetOf()) { frontier ->
                        var nextBlock = frontier
                        while (
                            nextBlock <= MAX_BLOCK_NUMBER && blocks?.containsKey(nextBlock) == true
                        ) {
                            nextBlock++
                        }
                        nextBlock.takeIf { it <= MAX_BLOCK_NUMBER }
                    }
                frontiers.clear()
                frontiers += nextFrontiers
            }

            val frontiers = probeFrontiers
            if (frontiers?.isEmpty() == true) return emptyList()
            val blockNumbers =
                if (frontiers != null) {
                    frontiers.asSequence()
                } else {
                    candidate.blockIndices.asSequence().filter { blockNumber ->
                        blocks?.containsKey(blockNumber) != true &&
                            blockNumber !in unavailableBlocks
                    }
                }

            return blockNumbers
                .sorted()
                .take(limit)
                .map { blockNumber ->
                    ReadTarget(
                        service,
                        blockNumber,
                        candidateIndex = candidateIndex,
                        isProbe = frontiers != null,
                    )
                }
                .toList()
        }

        override fun accept(target: ReadTarget) {
            if (target.candidateIndex != candidateIndex || !target.isProbe) return
            val frontiers = probeFrontiers ?: return
            if (frontiers.remove(target.blockNumber) && target.blockNumber < MAX_BLOCK_NUMBER) {
                frontiers += target.blockNumber + 1
            }
        }

        override fun reject(target: ReadTarget, tryNextCandidate: Boolean) {
            if (target.candidateIndex != candidateIndex) return
            if (target.isProbe) {
                probeFrontiers?.remove(target.blockNumber)
            } else if (!tryNextCandidate || !advanceCandidate()) {
                unavailableBlocks += target.blockNumber
            }
        }

        fun advanceCandidate(): Boolean {
            if (candidateIndex >= candidates.lastIndex) return false
            candidateIndex++
            unavailableBlocks.clear()
            probeFrontiers = null
            return true
        }
    }

    private class ReadTarget(
        val service: Service,
        val blockNumber: Int,
        val candidateIndex: Int? = null,
        val isProbe: Boolean = false,
    )

    private companion object {
        const val TAG = "BlockReader"
        const val BITMASK_MAX_BLOCKS = 8
        const val MAX_CONSECUTIVE_FAILURES = 32
    }
}
