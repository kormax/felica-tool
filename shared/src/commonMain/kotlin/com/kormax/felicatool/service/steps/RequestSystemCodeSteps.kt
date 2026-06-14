package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestSystemCodeDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_system_code",
        title = "Request System Code - Supported",
        description = "Check whether Request System Code is available",
        icon = ScanStepIcon.SETTINGS,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestSystemCode.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestSystemCode = requestSystemCode.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val requestSystemCodeResponse =
            executeCommand(
                withSelectedSystemCode = SYSTEM_CODE_WILDCARD,
                // On devices with ST chips, IC 24 seems to struggle, resetting the field helps
                withResetToMode0 = pmm.icType == 0x24.toByte(),
            ) {
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

internal object RequestSystemCodeDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<RequestSystemCodeResponse>(
        id = "request_system_code_determine_trailing_data_supported",
        title = "Request System Code - Trailing Data Supported",
        description = "Check whether Request System Code accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Request System Code",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestSystemCode.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestSystemCode = requestSystemCode.copy(trailingDataSupported = support))
    }

    override fun ScanSession.resetToMode0AfterCommand(): Boolean = pmm.icType == 0x24.toByte()

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<RequestSystemCodeResponse> = RequestSystemCodeCommand(scope.idm, trailingData)

    override fun responseLines(response: RequestSystemCodeResponse): List<String> =
        listOf("Returned ${response.systemCodes.size} system code(s)")
}
