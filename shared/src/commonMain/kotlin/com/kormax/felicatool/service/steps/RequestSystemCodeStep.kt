package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestSystemCodeStep :
    CommandSupportScanStep(
        id = "request_system_code",
        title = "Request System Code",
        description = "Request all system codes registered to the card",
        icon = ScanStepIcon.SETTINGS,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestSystemCodeSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestSystemCodeSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val requestSystemCodeResponse =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                RequestSystemCodeCommand(idm)
            }

        // Handle special system codes and ensure system contexts exist
        val updatedSystemContexts =
            handleDiscoveredSystemCodes(requestSystemCodeResponse.systemCodes)

        // Store discovered system codes in context and update system contexts
        scanContext =
            scanContext.copy(
                discoveredSystemCodes = requestSystemCodeResponse.systemCodes,
                systemScanContexts = updatedSystemContexts,
            )

        return StepOutput(
            if (requestSystemCodeResponse.systemCodes.isNotEmpty()) {
                buildString {
                        appendLine(
                            "Discovered System Codes (${requestSystemCodeResponse.systemCodes.size}):"
                        )
                        requestSystemCodeResponse.systemCodes.forEachIndexed { index, systemCode ->
                            val systemCodeHex = systemCode.toHexString().uppercase()
                            appendLine("  ${index + 1}. $systemCodeHex")
                        }
                    }
                    .trim()
            } else {
                "No system codes discovered"
            }
        )
    }
}
