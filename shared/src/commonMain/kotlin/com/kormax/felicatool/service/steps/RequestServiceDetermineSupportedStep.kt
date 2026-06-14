package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object RequestServiceDetermineSupportedStep :
    CommandSupportScanStep(
        id = "request_service_determine_supported",
        title = "Request Service: Determine Supported",
        description = "Check whether Request Service is available",
        icon = ScanStepIcon.CHECK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.requestServiceSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(requestServiceSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        var requestedNodes: List<Node> = emptyList()

        val requestServiceResponse =
            executeCommand(
                withSelectedSystemCode = SYSTEM_CODE_WILDCARD,
                attempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) {
                requestedNodes =
                    when (attempt) {
                        // Heuristics
                        // For some reason, the special Octopus variant (IC 24) succeeds more if
                        // we try different values when re-attempting the command
                        1 -> listOf(System)
                        2 -> listOf(Area.ROOT)
                        else -> listOf(System, Area.ROOT)
                    }
                RequestServiceCommand(
                    idm,
                    requestedNodes.map { node -> node.code }.toTypedArray(),
                )
            }

        return StepOutput(
            buildString {
                    appendLine("Request Service command is supported (response received)")
                    appendLine("Nodes:")
                    requestedNodes.zip(requestServiceResponse.keyVersions).forEach {
                        (node, keyVersion) ->
                        appendLine(
                            "  ${node.code.toHexString().uppercase()} (${describeNode(node, includeCode = false)}): ${
                                if (keyVersion.isMissing) {
                                    "Not found"
                                } else {
                                    keyVersion.toInt().toString()
                                }
                            }"
                        )
                    }
                }
                .trim()
        )
    }
}
