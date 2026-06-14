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
    override fun readSupport(context: CardScanContext): CommandSupport = context.resetModeSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(resetModeSupport = support)

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
