package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetContainerIdStep :
    CommandSupportScanStep(
        id = "get_container_id",
        title = "Get Container ID",
        description = "Get container IDM from mobile FeliCa cards",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getContainerIdSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getContainerIdSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val getContainerIdResponse = executeCommand { GetContainerIdCommand() }

        // Store container IDM in context
        scanContext = scanContext.copy(containerIdm = getContainerIdResponse.containerIdm)

        return StepOutput(
            buildString {
                    appendLine(
                        "Container IDM: ${getContainerIdResponse.containerIdm.toHexString()}"
                    )
                }
                .trim()
        )
    }
}
