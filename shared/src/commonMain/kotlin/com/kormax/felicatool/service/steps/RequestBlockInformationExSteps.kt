package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestBlockInformationExDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_block_information_ex_determine_supported",
        title = "Request Block Information Ex: Supported",
        description = "Check whether Request Block Information Ex is available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestBlockInformationEx.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestBlockInformationEx = requestBlockInformationEx.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                RequestBlockInformationExCommand(idm, arrayOf(System.code))
            }

        return StepOutput(
            buildString {
                    appendLine(
                        "Request Block Information Ex command is supported (response received)"
                    )
                    appendLine("Node: ${System.code.toHexString().uppercase()} (System)")
                    appendLine("Status: ${formatStatus(response)}")
                    appendLine("Returned ${response.assignedBlockCount.size} block count entries")
                }
                .trim()
        )
    }
}

internal object RequestBlockInformationExDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<RequestBlockInformationExResponse>(
        id = "request_block_information_ex_determine_trailing_data_supported",
        title = "Request Block Information Ex - Trailing Data Supported",
        description = "Check whether Request Block Information Ex accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Request Block Information Ex",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestBlockInformationEx.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(
            requestBlockInformationEx =
                requestBlockInformationEx.copy(trailingDataSupported = support)
        )
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<RequestBlockInformationExResponse> =
        RequestBlockInformationExCommand(scope.idm, arrayOf(System.code), trailingData)

    override fun responseLines(response: RequestBlockInformationExResponse): List<String> =
        listOf(
            "Status: ${formatStatus(response)}",
            "Returned ${response.assignedBlockCount.size} block count entries",
        )
}
