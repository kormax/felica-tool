package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestSpecificationVersionDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_specification_version",
        title = "Request Specification Version - Supported",
        description = "Check whether Request Specification Version is available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestSpecificationVersion.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(requestSpecificationVersion = requestSpecificationVersion.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val requestSpecVersionResponse =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                RequestSpecificationVersionCommand(idm)
            }

        // Store specification version in context
        scanContext =
            scanContext.copy(specificationVersion = requestSpecVersionResponse.specificationVersion)

        return StepOutput(
            buildString {
                    appendLine("Specification Version Information:")
                    appendLine(
                        "Status Flags: ${formatStatus(requestSpecVersionResponse, prefix = "")}"
                    )

                    if (requestSpecVersionResponse.isStatusSuccessful) {
                        appendLine(
                            "Format Version: ${requestSpecVersionResponse.specificationVersion?.formatVersion?.let { "0x${byteToHex(it)}" } ?: "N/A"}"
                        )
                        appendLine()

                        requestSpecVersionResponse.specificationVersion?.basicVersion?.let {
                            basicVersion ->
                            appendLine("Basic Version: ${basicVersion.major}.${basicVersion.minor}")
                        }

                        requestSpecVersionResponse.specificationVersion?.desOptionVersion?.let {
                            desVersion ->
                            appendLine(
                                "DES Option Version: ${desVersion.major}.${desVersion.minor}"
                            )
                        }

                        requestSpecVersionResponse.specificationVersion
                            ?.specialOptionVersion
                            ?.let { specialVersion ->
                                appendLine(
                                    "Special Option Version: ${specialVersion.major}.${specialVersion.minor}"
                                )
                            }

                        requestSpecVersionResponse.specificationVersion
                            ?.extendedOverlapOptionVersion
                            ?.let { extendedOverlapVersion ->
                                appendLine(
                                    "Extended Overlap Option Version: ${extendedOverlapVersion.major}.${extendedOverlapVersion.minor}"
                                )
                            }

                        requestSpecVersionResponse.specificationVersion
                            ?.valueLimitedPurseServiceOptionVersion
                            ?.let { valueLimitedPurseVersion ->
                                appendLine(
                                    "Value-Limited Purse Service Option Version: ${valueLimitedPurseVersion.major}.${valueLimitedPurseVersion.minor}"
                                )
                            }

                        requestSpecVersionResponse.specificationVersion
                            ?.communicationWithMacOptionVersion
                            ?.let { communicationWithMacVersion ->
                                appendLine(
                                    "Communication with MAC Option Version: ${communicationWithMacVersion.major}.${communicationWithMacVersion.minor}"
                                )
                            }

                        requestSpecVersionResponse.specificationVersion
                            ?.randomIdOptionVersion
                            ?.let { randomIdVersion ->
                                appendLine(
                                    "Random ID Option Version: ${randomIdVersion.major}.${randomIdVersion.minor}"
                                )
                            }
                    } else {
                        appendLine("Failed to retrieve specification version information")
                    }
                }
                .trim()
        )
    }
}

internal object RequestSpecificationVersionDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<RequestSpecificationVersionResponse>(
        id = "request_specification_version_determine_trailing_data_supported",
        title = "Request Specification Version - Trailing Data Supported",
        description = "Check whether Request Specification Version accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Request Specification Version",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.requestSpecificationVersion.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(
            requestSpecificationVersion =
                requestSpecificationVersion.copy(trailingDataSupported = support)
        )
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<RequestSpecificationVersionResponse> =
        RequestSpecificationVersionCommand(idm = scope.idm, trailingData = trailingData)

    override fun responseLines(response: RequestSpecificationVersionResponse): List<String> =
        listOf("Status Flags: ${formatStatus(response, prefix = "")}")
}
