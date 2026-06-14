package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.StepSkipped
import com.kormax.felicatool.service.SystemScanContext

private val FELICA_LITE_SYSTEM_CODE = byteArrayOf(0x88.toByte(), 0xB4.toByte())
private val NDEF_SYSTEM_CODE = byteArrayOf(0x12.toByte(), 0xFC.toByte())

internal data class WritableBlockProbeTarget(
    val service: Service,
    val systemCode: ByteArray?,
    val safeBlockNumber: Int,
    val safeBlockData: ByteArray,
    val invalidBlockNumber: Int,
)

internal fun CardScanContext.findWritableBlockProbeTarget(): WritableBlockProbeTarget {
    val allDiscoveredNodes = systemScanContexts.flatMap { it.nodes }
    val allServices = allDiscoveredNodes.filterIsInstance<Service>()

    if (allServices.isEmpty()) {
        throw StepSkipped("No services available for write testing")
    }

    val writableServices = allServices.filter { service ->
        !service.attribute.authenticationRequired &&
            service.attribute.type == ServiceType.RANDOM &&
            service.attribute.mode == ServiceMode.READ_WRITE
    }

    if (writableServices.isEmpty()) {
        throw StepSkipped("No suitable writable services found (R/W RANDOM, no auth required)")
    }

    data class ServiceCandidate(
        val service: Service,
        val systemContext: SystemScanContext,
        val availableBlocks: MutableMap<Int, ByteArray>,
        val unavailableBlocks: MutableSet<Int>,
    )

    val serviceCandidates = mutableListOf<ServiceCandidate>()

    for (service in writableServices) {
        val systemContext =
            systemScanContexts.find { context -> context.nodes.contains(service) } ?: continue
        val isProtectedSystem =
            systemContext.systemCode?.contentEquals(FELICA_LITE_SYSTEM_CODE) == true ||
                systemContext.systemCode?.contentEquals(NDEF_SYSTEM_CODE) == true

        val isValidServiceNumber =
            if (isProtectedSystem) {
                service.number == 0 || (service.number != 0 && service.number < 1023)
            } else {
                service.number != 0 && service.number < 1023
            }
        if (!isValidServiceNumber) {
            continue
        }

        val macProperties = systemContext.nodeMacCommunicationProperties[service]
        if (macProperties != null && macProperties.enabled) {
            continue
        }

        val blockData = systemContext.serviceBlockData[service] ?: continue
        val maxSafeBlockNumber = if (isProtectedSystem) 0x0D else Int.MAX_VALUE
        val availableBlocks = mutableMapOf<Int, ByteArray>()
        val unavailableBlocks = mutableSetOf<Int>()

        for ((blockNumber, data) in blockData) {
            if (blockNumber > maxSafeBlockNumber) {
                unavailableBlocks.add(blockNumber)
                continue
            }

            val isAllZero = data.all { it == 0x00.toByte() }
            val isAllFf = data.all { it == 0xFF.toByte() }
            if (isProtectedSystem || isAllZero || isAllFf) {
                availableBlocks[blockNumber] = data
            } else {
                unavailableBlocks.add(blockNumber)
            }
        }

        if (availableBlocks.isNotEmpty()) {
            serviceCandidates.add(
                ServiceCandidate(service, systemContext, availableBlocks, unavailableBlocks)
            )
        }
    }

    if (serviceCandidates.isEmpty()) {
        throw StepSkipped(
            "No empty blocks (all 0x00 or 0xFF) found in any writable service. " +
                "Cannot safely test write commands without risking data modification."
        )
    }

    val bestCandidate = serviceCandidates.maxByOrNull { it.availableBlocks.size }!!
    val safeBlockNumber = bestCandidate.availableBlocks.keys.minOrNull()!!
    val safeBlockData = bestCandidate.availableBlocks[safeBlockNumber]!!
    val allKnownBlocks = bestCandidate.availableBlocks.keys + bestCandidate.unavailableBlocks
    var invalidBlockNumber = 0
    while (invalidBlockNumber in allKnownBlocks) {
        invalidBlockNumber++
    }

    return WritableBlockProbeTarget(
        service = bestCandidate.service,
        systemCode = bestCandidate.systemContext.systemCode,
        safeBlockNumber = safeBlockNumber,
        safeBlockData = safeBlockData,
        invalidBlockNumber = invalidBlockNumber,
    )
}
