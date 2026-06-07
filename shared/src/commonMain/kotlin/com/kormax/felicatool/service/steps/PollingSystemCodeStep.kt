package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object PollingSystemCodeStep :
    CommandSupportScanStep(
        id = "polling_system_code",
        title = "Polling - System Code",
        description = "Request primary system code of the card using polling command",
        icon = ScanStepIcon.SETTINGS,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.pollingSystemCodeSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(pollingSystemCodeSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        ensureCardPresence(target)

        val systemCodeCommand =
            PollingCommand(
                systemCode = target.systemCode ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                requestCode = RequestCode.SYSTEM_CODE_REQUEST,
            )
        val parsedSystemCodeResponse = target.transceive(systemCodeCommand)

        // Store system code in context
        if (parsedSystemCodeResponse.hasRequestData) {
            scanContext = scanContext.copy(primarySystemCode = parsedSystemCodeResponse.systemCode)

            // Handle special system codes and ensure system contexts exist
            val updatedSystemContexts =
                handleDiscoveredSystemCodes(listOf(parsedSystemCodeResponse.systemCode))

            // Update scan context with the new system contexts
            scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)
        } else {
            // Update existing system context or create a placeholder one (fallback for legacy
            // code)
            if (scanContext.systemScanContexts.isNotEmpty()) {
                val updatedSystemContext =
                    scanContext.systemScanContexts
                        .first()
                        .copy(systemCode = parsedSystemCodeResponse.systemCode)
                scanContext = scanContext.copy(systemScanContexts = listOf(updatedSystemContext))
            } else {
                // Create a basic system context if none exists yet
                val systemContext =
                    SystemScanContext(
                        systemCode = parsedSystemCodeResponse.systemCode,
                        idm = target.idm,
                    )
                scanContext = scanContext.copy(systemScanContexts = listOf(systemContext))
            }
        }

        return if (parsedSystemCodeResponse.hasRequestData) {
            val systemCodeHex = parsedSystemCodeResponse.systemCode.toHexString().uppercase()
            StepOutput("System Code: $systemCodeHex")
        } else {
            throw RuntimeException("Polling: System Code: Not available")
        }
    }
}
