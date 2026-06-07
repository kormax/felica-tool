package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestCodeListDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_code_list_determine_supported",
        title = "Request Code List: Determine Supported",
        description = "Check whether Request Code List is available",
        icon = ScanStepIcon.LIST,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestCodeListSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestCodeListSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val systemContext = scanContext.systemScanContexts.firstOrNull()

        val index = 1
        val requestCodeListResponse =
            transceiveWithRetries(
                target,
                RequestCodeListCommand(target.idm, Area.ROOT, index),
                systemCode = systemContext?.systemCode,
            )
        val systemCodeHex = formatSystemCodeLabel(systemContext?.systemCode)

        return StepOutput(
            buildString {
                    appendLine("Request Code List command is supported (response received)")
                    appendLine("System: $systemCodeHex")
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
