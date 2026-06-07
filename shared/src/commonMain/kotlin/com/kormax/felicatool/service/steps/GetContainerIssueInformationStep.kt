package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetContainerIssueInformationStep :
    CommandSupportScanStep(
        id = "get_container_issue_information",
        title = "Get Container Issue Information",
        description =
            "Get container-specific information including format version and mobile phone model",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getContainerIssueInformationSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getContainerIssueInformationSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        ensureCardPresence(target)

        val getContainerIssueInformationCommand = GetContainerIssueInformationCommand(target.idm)
        val getContainerIssueInformationResponse =
            target.transceive(getContainerIssueInformationCommand)
        val containerInformation = getContainerIssueInformationResponse.containerInformation

        // Store container issue information in context
        scanContext = scanContext.copy(containerIssueInformation = containerInformation)

        val formatVersionHex = containerInformation.formatVersionCarrierInformation.toHexString()
        val modelInfoHex = containerInformation.mobilePhoneModelInformation.toHexString()

        // Try to decode mobile phone model as printable string
        val modelString =
            try {
                val printableBytes =
                    containerInformation.mobilePhoneModelInformation.filter { it in 32..126 }
                if (printableBytes.size >= 3) { // At least 3 printable characters
                    printableBytes.joinToString(separator = "") { byte ->
                        byte.toInt().toChar().toString()
                    }
                } else {
                    modelInfoHex
                }
            } catch (e: Exception) {
                modelInfoHex
            }

        return StepOutput(
            buildString {
                    appendLine("Format Version & Carrier Info: $formatVersionHex")
                    appendLine("Mobile Phone Model: $modelString")
                }
                .trim()
        )
    }
}
