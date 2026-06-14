package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.RequestBlockInformationExCommand
import com.kormax.felicatool.felica.System
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SYSTEM_CODE_WILDCARD
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.service.formatStatus
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestBlockInformationExDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_block_information_ex_determine_supported",
        title = "Request Block Information Ex: Determine Supported",
        description = "Check whether Request Block Information Ex is available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestBlockInformationExSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestBlockInformationExSupport = support)

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
