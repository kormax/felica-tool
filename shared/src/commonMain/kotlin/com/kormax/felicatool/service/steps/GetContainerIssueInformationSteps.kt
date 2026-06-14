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
        context.commands.getContainerIssueInformation.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getContainerIssueInformation = getContainerIssueInformation.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val getContainerIssueInformationResponse =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                GetContainerIssueInformationCommand(idm)
            }
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

internal object GetContainerIssueInformationDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<GetContainerIssueInformationResponse>(
        id = "get_container_issue_information_determine_trailing_data_supported",
        title = "Get Container Issue Information - Trailing Data Supported",
        description = "Check whether Get Container Issue Information accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Get Container Issue Information",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getContainerIssueInformation.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(
            getContainerIssueInformation =
                getContainerIssueInformation.copy(trailingDataSupported = support)
        )
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<GetContainerIssueInformationResponse> =
        GetContainerIssueInformationCommand(scope.idm, trailingData = trailingData)

    override fun responseLines(response: GetContainerIssueInformationResponse): List<String> =
        listOf(
            "Format Version & Carrier Info: ${response.containerInformation.formatVersionCarrierInformation.toHexString()}",
            "Mobile Phone Model: ${response.containerInformation.mobilePhoneModelInformation.toHexString()}",
        )
}
