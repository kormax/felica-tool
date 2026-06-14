package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestCodeListDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_code_list_determine_supported",
        title = "Request Code List: Supported",
        description = "Check whether Request Code List is available",
        icon = ScanStepIcon.LIST,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestCodeList.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestCodeList = requestCodeList.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val index = 1
        val requestCodeListResponse =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                RequestCodeListCommand(idm, Area.ROOT, index)
            }

        return StepOutput(
            buildString {
                    appendLine("Request Code List command is supported (response received)")
                    appendLine("Parent node: ${Area.ROOT.code.toHexString().uppercase()}")
                    appendLine("Index: $index")
                    appendLine("Status: ${formatStatus(requestCodeListResponse)}")
                    appendLine(
                        "Returned ${requestCodeListResponse.areas.size} area(s), ${requestCodeListResponse.services.size} service(s)"
                    )
                    appendLine("Continue flag: ${requestCodeListResponse.continueFlag}")
                    if (!requestCodeListResponse.isStatusSuccessful) {
                        appendLine(
                            "Note: Response status is not successful, but command support is confirmed."
                        )
                    }
                }
                .trim()
        )
    }
}

internal object RequestCodeListDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<RequestCodeListResponse>(
        id = "request_code_list_determine_trailing_data_supported",
        title = "Request Code List - Trailing Data Supported",
        description = "Check whether Request Code List accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Request Code List",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestCodeList.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestCodeList = requestCodeList.copy(trailingDataSupported = support))
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<RequestCodeListResponse> =
        RequestCodeListCommand(scope.idm, Area.ROOT, index = 1, trailingData = trailingData)

    override fun responseLines(response: RequestCodeListResponse): List<String> =
        listOf(
            "Status: ${formatStatus(response)}",
            "Returned ${response.areas.size} area(s), ${response.services.size} service(s)",
            "Continue flag: ${response.continueFlag}",
        )
}
