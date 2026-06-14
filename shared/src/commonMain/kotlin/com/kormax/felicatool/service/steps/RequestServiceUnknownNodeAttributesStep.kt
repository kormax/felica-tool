package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon
import kotlin.time.Duration.Companion.milliseconds

private const val REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS = 3
private const val REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER = 0

internal object RequestServiceUnknownNodeAttributesStep :
    ScanStep(
        id = "request_service_determine_unknown_node_attributes_supported",
        title = "Request Service - Unknown Attributes",
        description =
            "Check Request Service behavior when a node with an unknown attribute is requested",
        icon = ScanStepIcon.CHECK,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.requestServiceSupport != CommandSupport.SUPPORTED) {
            throw StepSkipped(
                "Request Service command is unavailable; cannot probe unknown attribute behavior"
            )
        }
        val knownAttributes = buildSet {
            addAll(ServiceAttribute.entries.map { it.value })
            addAll(AreaAttribute.entries.map { it.value })
        }
        val unknownAttributeValue =
            (0..0x3F).firstOrNull { it !in knownAttributes }
                ?: throw StepSkipped("No unknown node attribute value available for probing")
        val unknownAttributeNodeCodeValue =
            (REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER shl 6) or unknownAttributeValue
        val unknownAttributeNodeCode =
            byteArrayOf(
                (unknownAttributeNodeCodeValue and 0xFF).toByte(),
                ((unknownAttributeNodeCodeValue shr 8) and 0xFF).toByte(),
            )

        val response =
            try {
                executeCommand(
                    withSelectedSystemCode = SYSTEM_CODE_WILDCARD,
                    attempts = REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS,
                    retryDelay = 50.milliseconds,
                ) {
                    RequestServiceCommand(idm, arrayOf(unknownAttributeNodeCode))
                }
            } catch (e: TransceiveTimeoutException) {
                null
            }
        val responseReceived = response != null

        scanContext =
            scanContext.copy(requestServiceUnknownNodeAttributesSupported = responseReceived)

        return StepOutput(
            buildString {
                    appendLine(
                        "Probe node: ${unknownAttributeNodeCode.toHexString().uppercase()} " +
                            "(service=${REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER}, " +
                            "attribute=0x${byteToHex(unknownAttributeValue)})"
                    )
                    appendLine("System: ${formatSystemCodeLabel(SYSTEM_CODE_WILDCARD)}")
                    appendLine("Supported = $responseReceived")
                    if (response != null) {
                        val keyVersionHex =
                            response.keyVersions.first().toByteArray().toHexString().uppercase()
                        appendLine("Key version: $keyVersionHex")
                    } else {
                        appendLine(
                            "No response after $REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS attempts"
                        )
                    }
                }
                .trim()
        )
    }
}
