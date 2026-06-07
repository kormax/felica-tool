package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object ReadWithoutEncryptionDetermineSupportedStep :
    ReadWithoutEncryptionScanStep(
        id = "read_without_encryption_determine_supported",
        title = "Read: Determine Supported",
        description =
            "Probe if Read Without Encryption is supported by sending a single-service, single-block read request",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget =
            scanContext.findReadWithoutEncryptionTestTarget(
                allowAuthenticationRequiredFallback = true
            )
        val systemCode = testTarget.systemContext.systemCode

        val response =
            transceiveWithRetries(
                target = target,
                commandLabel = "ReadWithoutEncryptionCommand",
                systemCode = systemCode,
                maxAttempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) { activeTarget, _ ->
                ReadWithoutEncryptionCommand(
                    idm = activeTarget.idm,
                    serviceCodes = arrayOf(testTarget.service.code),
                    blockListElements =
                        arrayOf(
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = testTarget.blockNumber,
                            )
                        ),
                )
            }

        val systemCodeHex = systemCode?.toHexString() ?: "unknown"
        val serviceCodeHex = testTarget.service.code.toHexString().uppercase()

        return StepOutput(
            buildString {
                    appendLine("Read Without Encryption command is supported (response received)")
                    appendLine(
                        "System: $systemCodeHex; Service: $serviceCodeHex; Block: ${formatBlockNumberHex(testTarget.blockNumber)}"
                    )
                    appendLine("(${formatStatus(response)})")
                    if (testTarget.service.attribute.authenticationRequired) {
                        appendLine(
                            "Note: Used auth-required service fallback because no no-auth service was available."
                        )
                    }
                }
                .trim()
        )
    }
}
