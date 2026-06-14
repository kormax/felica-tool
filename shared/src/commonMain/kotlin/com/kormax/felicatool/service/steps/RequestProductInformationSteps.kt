package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestProductInformationDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_product_information",
        title = "Request Product Information - Supported",
        description = "Check whether Request Product Information is available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestProductInformation.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestProductInformation = requestProductInformation.copy(supported = support))
    }

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

internal object RequestProductInformationDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<RequestProductInformationResponse>(
        id = "request_product_information_determine_trailing_data_supported",
        title = "Request Product Information - Trailing Data Supported",
        description = "Check whether Request Product Information accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Request Product Information",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestProductInformation.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(
            requestProductInformation =
                requestProductInformation.copy(trailingDataSupported = support)
        )
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<RequestProductInformationResponse> =
        RequestProductInformationCommand(idm = scope.idm, trailingData = trailingData)

    override fun responseLines(response: RequestProductInformationResponse): List<String> =
        listOf("Status Flags: ${formatStatus(response, prefix = "")}")
}
