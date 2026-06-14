package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.StepSkipped
import com.kormax.felicatool.service.SystemScanContext

private val FELICA_LITE_SYSTEM_CODE = byteArrayOf(0x88.toByte(), 0xB4.toByte())
private val NDEF_SYSTEM_CODE = byteArrayOf(0x12.toByte(), 0xFC.toByte())
private val PROTECTED_READ_TEST_SERVICE_CODES = setOf("0B00", "0900")
private const val PROTECTED_READ_TEST_BLOCK_NUMBER = 0x0092

internal data class ReadWithoutEncryptionTestTarget(
    val systemContext: SystemScanContext,
    val service: Service,
    val blockNumber: Int,
)

internal fun CardScanContext.findReadWithoutEncryptionTestTarget(
    allowAuthenticationRequiredFallback: Boolean = false
): ReadWithoutEncryptionTestTarget {
    val allServices = systemScanContexts.flatMap { context ->
        context.nodes.filterIsInstance<Service>()
    }
    if (allServices.isEmpty()) {
        throw StepSkipped("No services available")
    }

    val allServicesWithoutAuth = allServices.filter { service ->
        !service.attribute.authenticationRequired
    }
    val useAuthenticationRequiredFallback =
        allowAuthenticationRequiredFallback && allServicesWithoutAuth.isEmpty()
    if (allServicesWithoutAuth.isEmpty() && !useAuthenticationRequiredFallback) {
        throw StepSkipped("No services found that don't require authentication")
    }

    val bestSystemContext =
        if (useAuthenticationRequiredFallback) {
            systemScanContexts.maxByOrNull { systemContext ->
                systemContext.nodes.filterIsInstance<Service>().size
            }
        } else {
            systemScanContexts.maxByOrNull { systemContext ->
                systemContext.nodes.filterIsInstance<Service>().count { service ->
                    !service.attribute.authenticationRequired
                }
            }
        } ?: throw StepSkipped("No system context found with readable services")

    val servicesInBestSystem = bestSystemContext.nodes.filterIsInstance<Service>()
    val servicesWithoutAuth = servicesInBestSystem.filter { service ->
        !service.attribute.authenticationRequired
    }
    val candidateServices =
        if (servicesWithoutAuth.isNotEmpty()) {
            servicesWithoutAuth
        } else if (useAuthenticationRequiredFallback) {
            servicesInBestSystem
        } else {
            emptyList()
        }
    if (candidateServices.isEmpty()) {
        throw StepSkipped("No readable services found in the selected system")
    }

    val testService =
        candidateServices.maxByOrNull { service ->
            var score = 0
            if (service.number != 0) score += 4
            if (service.attribute.type == ServiceType.RANDOM) score += 2
            if (service.attribute.mode == ServiceMode.READ_ONLY) score += 1
            score
        }!!
    val isProtectedSystem =
        bestSystemContext.systemCode?.contentEquals(FELICA_LITE_SYSTEM_CODE) == true ||
            bestSystemContext.systemCode?.contentEquals(NDEF_SYSTEM_CODE) == true
    val serviceCodeHex = testService.code.toHexString().uppercase()
    val testBlockNumber =
        if (isProtectedSystem && serviceCodeHex in PROTECTED_READ_TEST_SERVICE_CODES) {
            PROTECTED_READ_TEST_BLOCK_NUMBER
        } else {
            0
        }

    return ReadWithoutEncryptionTestTarget(
        systemContext = bestSystemContext,
        service = testService,
        blockNumber = testBlockNumber,
    )
}
