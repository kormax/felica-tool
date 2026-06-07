package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestResponseStep :
    CommandSupportScanStep(
        id = "request_response",
        title = "Request Response",
        description = "Request response from the card",
        icon = ScanStepIcon.SETTINGS,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestResponseSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestResponseSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val requestResponseResponse =
            transceiveWithRetries(target, RequestResponseCommand(target.idm))

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
