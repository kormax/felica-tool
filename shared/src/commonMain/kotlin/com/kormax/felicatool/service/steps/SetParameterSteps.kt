package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object SetParameterStep :
    CommandSupportScanStep(
        id = "set_parameter",
        title = "Set Parameter",
        description = "Set card parameters (encryption type and packet type)",
        icon = ScanStepIcon.BUILD,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.setParameter.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(setParameter = setParameter.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                SetParameterCommand(
                    idm = idm,
                    encryptionType = SetParameterCommand.EncryptionType.SRM_TYPE1,
                    packetType = SetParameterCommand.PacketType.NODECODESIZE_2,
                )
            }

        if (!response.isStatusSuccessful) {
            throw RuntimeException("Set Parameter command failed with ${formatStatus(response)}")
        }

        return StepOutput(
            buildString {
                    appendLine("Encryption Type: SRM_TYPE1")
                    appendLine("Packet Type: NODECODESIZE_2")
                    appendLine("Response IDM: ${response.idm.toHexString()}")
                    appendLine("Status: ${formatStatus(response)}")
                }
                .trim()
        )
    }
}

internal object SetParameterDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<SetParameterResponse>(
        id = "set_parameter_determine_trailing_data_supported",
        title = "Set Parameter - Trailing Data Supported",
        description = "Check whether Set Parameter accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Set Parameter",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.setParameter.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(setParameter = setParameter.copy(trailingDataSupported = support))
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<SetParameterResponse> =
        SetParameterCommand(
            idm = scope.idm,
            encryptionType = SetParameterCommand.EncryptionType.SRM_TYPE1,
            packetType = SetParameterCommand.PacketType.NODECODESIZE_2,
            trailingData = trailingData,
        )

    override fun responseLines(response: SetParameterResponse): List<String> =
        listOf("Status: ${formatStatus(response)}")
}
