package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestBlockInformationDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_block_information_determine_supported",
        title = "Request Block Information: Supported",
        description = "Check whether Request Block Information is available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestBlockInformation.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestBlockInformation = requestBlockInformation.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                RequestBlockInformationCommand(idm, arrayOf(System.code))
            }

        return StepOutput(
            buildString {
                    appendLine("Request Block Information command is supported (response received)")
                    appendLine("Node: ${System.code.toHexString().uppercase()} (System)")
                    appendLine(
                        "Returned ${response.blockCountInformation.size} block count entries"
                    )
                }
                .trim()
        )
    }
}

internal object RequestBlockInformationDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<RequestBlockInformationResponse>(
        id = "request_block_information_determine_trailing_data_supported",
        title = "Request Block Information - Trailing Data Supported",
        description = "Check whether Request Block Information accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Request Block Information",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestBlockInformation.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(
            requestBlockInformation = requestBlockInformation.copy(trailingDataSupported = support)
        )
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<RequestBlockInformationResponse> =
        RequestBlockInformationCommand(scope.idm, arrayOf(System.code), trailingData)

    override fun responseLines(response: RequestBlockInformationResponse): List<String> =
        listOf("Returned ${response.blockCountInformation.size} block count entries")
}
