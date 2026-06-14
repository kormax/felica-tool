package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.RequestBlockInformationCommand
import com.kormax.felicatool.felica.System
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SYSTEM_CODE_WILDCARD
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestBlockInformationDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_block_information_determine_supported",
        title = "Request Block Information: Determine Supported",
        description = "Check whether Request Block Information is available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestBlockInformationSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestBlockInformationSupport = support)

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
