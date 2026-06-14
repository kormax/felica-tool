package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestProductInformationStep :
    CommandSupportScanStep(
        id = "request_product_information",
        title = "Request Product Information",
        description = "Request product information from the card",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestProductInformationSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestProductInformationSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val requestProductInformationResponse =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                RequestProductInformationCommand(idm)
            }

        // Store product information in context
        scanContext = scanContext.copy(productInformation = requestProductInformationResponse)

        return StepOutput(
            buildString {
                    appendLine(
                        "Status Flags: ${formatStatus(requestProductInformationResponse, prefix = "")}"
                    )

                    if (requestProductInformationResponse.isStatusSuccessful) {
                        appendLine(
                            "Product information: ${requestProductInformationResponse.productInformationData.toHexString()}"
                        )
                    } else {
                        appendLine("Failed to request product information")
                    }
                }
                .trim()
        )
    }
}
