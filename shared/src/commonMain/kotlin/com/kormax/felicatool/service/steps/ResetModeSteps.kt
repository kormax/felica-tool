package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object ResetModeStep :
    CommandSupportScanStep(
        id = "reset_mode",
        title = "Reset Mode",
        description = "Reset card mode to Mode0",
        icon = ScanStepIcon.REFRESH,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.resetMode.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(resetMode = resetMode.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val resetModeResponse =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) { ResetModeCommand(idm) }

        return StepOutput(
            buildString {
                    appendLine("Status Flags: ${formatStatus(resetModeResponse, prefix = "")}")

                    // appendLine("Note: Reset Mode command resets the card's mode to Mode 0.")
                    // appendLine("This command is supported by AES and AES/DES cards.")
                }
                .trim()
        )
    }
}

internal object ResetModeDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<ResetModeResponse>(
        id = "reset_mode_determine_trailing_data_supported",
        title = "Reset Mode - Trailing Data Supported",
        description = "Check whether Reset Mode accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Reset Mode",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.resetMode.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(resetMode = resetMode.copy(trailingDataSupported = support))
    }

    override fun ScanSession.resetToMode0AfterCommand(): Boolean = true

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<ResetModeResponse> =
        ResetModeCommand(idm = scope.idm, trailingData = trailingData)

    override fun responseLines(response: ResetModeResponse): List<String> =
        listOf("Status Flags: ${formatStatus(response, prefix = "")}")
}
