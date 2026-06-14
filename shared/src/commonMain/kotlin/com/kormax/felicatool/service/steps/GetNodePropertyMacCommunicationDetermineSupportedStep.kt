package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.GetNodePropertyCommand
import com.kormax.felicatool.felica.NodePropertyType
import com.kormax.felicatool.felica.System
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SYSTEM_CODE_WILDCARD
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.service.formatStatus
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetNodePropertyMacCommunicationDetermineSupportedStep :
    CommandSupportScanStep(
        id = "get_node_property_mac_communication_determine_supported",
        title = "Get Node Property - MAC Communication: Determine Supported",
        description = "Check whether MAC communication properties are available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getNodePropertyMacCommunicationSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getNodePropertyMacCommunicationSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                GetNodePropertyCommand(
                    idm = idm,
                    nodePropertyType = NodePropertyType.MAC_COMMUNICATION,
                    nodes = listOf(System),
                )
            }

        return StepOutput(
            buildString {
                    appendLine(
                        "Get Node Property MAC Communication is supported (response received)"
                    )
                    appendLine("Node: ${System.code.toHexString().uppercase()} (System)")
                    appendLine("Status: ${formatStatus(response)}")
                    appendLine("Returned ${response.nodeProperties.size} properties")
                }
                .trim()
        )
    }
}
