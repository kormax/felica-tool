package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object SearchServiceCodeDetermineSupportedStep :
    CommandSupportScanStep(
        id = "search_service_code_determine_supported",
        title = "Search Service Code: Supported",
        description = "Check whether Search Service Code is available",
        icon = ScanStepIcon.LIST,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.searchServiceCode.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(searchServiceCode = searchServiceCode.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val index = 0
        val searchServiceCodeResponse =
            executeCommand(
                withSelectedSystemCode = SYSTEM_CODE_WILDCARD,
                attempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) {
                SearchServiceCodeCommand(idm, index)
            }
        val node = searchServiceCodeResponse.node

        return StepOutput(
            buildString {
                    appendLine("Search Service Code command is supported (response received)")
                    appendLine("Index: $index")
                    appendLine("Node: ${describeNode(node)}")
                }
                .trim()
        )
    }
}

internal object SearchServiceCodeDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<SearchServiceCodeResponse>(
        id = "search_service_code_determine_trailing_data_supported",
        title = "Search Service Code - Trailing Data Supported",
        description = "Check whether Search Service Code accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Search Service Code",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.searchServiceCode.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(searchServiceCode = searchServiceCode.copy(trailingDataSupported = support))
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<SearchServiceCodeResponse> =
        SearchServiceCodeCommand(scope.idm, index = 0, trailingData = trailingData)

    override fun responseLines(response: SearchServiceCodeResponse): List<String> =
        listOf("Node: ${describeNode(response.node)}")
}
