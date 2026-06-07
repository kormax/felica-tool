package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object SearchServiceCodeDetermineSupportedStep :
    CommandSupportScanStep(
        id = "search_service_code_determine_supported",
        title = "Search Service Code: Determine Supported",
        description = "Check whether Search Service Code is available",
        icon = ScanStepIcon.LIST,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.searchServiceCodeSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(searchServiceCodeSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val systemContext = scanContext.systemScanContexts.firstOrNull()

        val index = 0
        val searchServiceCodeResponse =
            transceiveWithRetries(
                target,
                SearchServiceCodeCommand(target.idm, index),
                systemCode = systemContext?.systemCode,
                maxAttempts = ATTEMPTS_DETERMINE_SUPPORTED,
            )
        val systemCodeHex = formatSystemCodeLabel(systemContext?.systemCode)
        val node = searchServiceCodeResponse.node

        return StepOutput(
            buildString {
                    appendLine("Search Service Code command is supported (response received)")
                    appendLine("System: $systemCodeHex")
                    appendLine("Index: $index")
                    appendLine("Node: ${describeNode(node)}")
                }
                .trim()
        )
    }
}
