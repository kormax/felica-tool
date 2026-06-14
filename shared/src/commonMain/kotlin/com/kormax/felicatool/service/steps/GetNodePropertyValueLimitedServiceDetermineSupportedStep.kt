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

internal object GetNodePropertyValueLimitedServiceDetermineSupportedStep :
    CommandSupportScanStep(
        id = "get_node_property_value_limited_service_determine_supported",
        title = "Get Node Property - Value Limited Service: Determine Supported",
        description = "Check whether Value-Limited Purse Service properties are available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getNodePropertyValueLimitedServiceSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getNodePropertyValueLimitedServiceSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                GetNodePropertyCommand(
                    idm = idm,
                    nodePropertyType = NodePropertyType.VALUE_LIMITED_PURSE_SERVICE,
                    nodes = listOf(System),
                )
            }

        return StepOutput(
            buildString {
                    appendLine(
                        "Get Node Property Value-Limited Purse Service is supported (response received)"
                    )
                    appendLine("Node: ${System.code.toHexString().uppercase()} (System)")
                    appendLine("Status: ${formatStatus(response)}")
                    appendLine("Returned ${response.nodeProperties.size} properties")
                }
                .trim()
        )
    }
}
