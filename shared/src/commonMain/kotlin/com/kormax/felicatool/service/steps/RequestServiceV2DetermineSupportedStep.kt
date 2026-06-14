package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestServiceV2DetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_service_v2_determine_supported",
        title = "Request Service V2: Determine Supported",
        description = "Check whether Request Service V2 is available",
        icon = ScanStepIcon.CHECK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestServiceV2Support

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestServiceV2Support = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val requestServiceV2Response =
            executeCommand(
                withSelectedSystemCode = SYSTEM_CODE_WILDCARD,
                attempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) {
                RequestServiceV2Command(idm, arrayOf(System.code))
            }
        val aesKeyVersion = requestServiceV2Response.aesKeyVersions.firstOrNull()
        val desKeyVersion = requestServiceV2Response.desKeyVersions.firstOrNull()

        return StepOutput(
            buildString {
                    appendLine("Request Service V2 command is supported (response received)")
                    appendLine("Node: ${System.code.toHexString().uppercase()} (System)")
                    appendLine("Status: ${formatStatus(requestServiceV2Response)}")
                    requestServiceV2Response.encryptionIdentifier?.let { encryptionIdentifier ->
                        appendLine("Encryption Identifier: ${encryptionIdentifier.name}")
                    }
                    aesKeyVersion?.let { keyVersion ->
                        appendLine(
                            "AES System Key Version: ${
                                    if (keyVersion.isMissing) {
                                        "Not found"
                                    } else {
                                        keyVersion.toInt().toString()
                                    }
                                }"
                        )
                    }
                    desKeyVersion?.let { keyVersion ->
                        appendLine(
                            "DES System Key Version: ${
                                    if (keyVersion.isMissing) {
                                        "Not found"
                                    } else {
                                        keyVersion.toInt().toString()
                                    }
                                }"
                        )
                    }
                }
                .trim()
        )
    }
}
