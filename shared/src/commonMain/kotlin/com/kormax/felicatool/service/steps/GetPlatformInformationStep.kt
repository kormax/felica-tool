package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetPlatformInformationStep :
    CommandSupportScanStep(
        id = "get_platform_information",
        title = "Get Platform Information",
        description = "Get platform information from the card",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getPlatformInformationSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getPlatformInformationSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        ensureCardPresence(target)

        val getPlatformInformationCommand = GetPlatformInformationCommand(target.idm)
        val getPlatformInformationResponse = target.transceive(getPlatformInformationCommand)

        // Store secure element information in context
        scanContext = scanContext.copy(platformInformation = getPlatformInformationResponse)

        return StepOutput(
            buildString {
                    appendLine(
                        "Status Flags: ${formatStatus(getPlatformInformationResponse, prefix = "")}"
                    )

                    if (getPlatformInformationResponse.isStatusSuccessful) {
                        appendLine(
                            "Platform information: ${getPlatformInformationResponse.platformInformationData.toHexString()}"
                        )
                    } else {
                        appendLine("Failed to retrieve secure element information")
                    }
                }
                .trim()
        )
    }
}
