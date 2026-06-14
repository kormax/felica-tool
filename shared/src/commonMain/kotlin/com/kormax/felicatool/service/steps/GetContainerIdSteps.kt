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
        context.commands.getContainerId.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getContainerId = getContainerId.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val getContainerIdResponse =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                GetContainerIdCommand()
            }

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

internal object GetContainerIdDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<GetContainerIdResponse>(
        id = "get_container_id_determine_trailing_data_supported",
        title = "Get Container ID - Trailing Data Supported",
        description = "Check whether Get Container ID accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Get Container ID",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getContainerId.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getContainerId = getContainerId.copy(trailingDataSupported = support))
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<GetContainerIdResponse> = GetContainerIdCommand(trailingData = trailingData)

    override fun responseLines(response: GetContainerIdResponse): List<String> =
        listOf("Container IDM: ${response.containerIdm.toHexString()}")
}
