package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestResponseDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_response_determine_supported",
        title = "Request Response - Supported",
        description = "Check whether Request Response is available",
        icon = ScanStepIcon.SETTINGS,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestResponse.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestResponse = requestResponse.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val requestResponseResponse =
            executeCommand(
                withSelectedSystemCode = SYSTEM_CODE_WILDCARD,
                // On devices with ST chips, IC 24 seems to struggle, resetting the field helps
                withResetToMode0 = pmm.icType == 0x24.toByte(),
            ) {
                RequestResponseCommand(idm)
            }

        val mode = requestResponseResponse.mode

        return StepOutput(
            buildString {
                    appendLine("Card is present and responding")
                    appendLine("Current Mode: ${mode.name} (${mode.value})")
                }
                .trim()
        )
    }
}

internal object RequestResponseDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<RequestResponseResponse>(
        id = "request_response_determine_trailing_data_supported",
        title = "Request Response - Trailing Data Supported",
        description = "Check whether Request Response accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Request Response",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestResponse.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestResponse = requestResponse.copy(trailingDataSupported = support))
    }

    override fun ScanSession.resetToMode0AfterCommand(): Boolean = pmm.icType == 0x24.toByte()

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<RequestResponseResponse> = RequestResponseCommand(scope.idm, trailingData)

    override fun responseLines(response: RequestResponseResponse): List<String> =
        listOf("Current Mode: ${response.mode.name} (${response.mode.value})")
}
