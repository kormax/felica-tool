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
    override fun readSupport(context: CardScanContext): CommandSupport = context.setParameterSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(setParameterSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        ensureCardPresence(target)

        // Test different parameter combinations
        val results = mutableListOf<String>()

        // Test with default parameters (SRM_TYPE1, NODECODESIZE_2)
        val setParameterCommand1 =
            SetParameterCommand(
                idm = target.idm,
                encryptionType = SetParameterCommand.EncryptionType.SRM_TYPE1,
                packetType = SetParameterCommand.PacketType.NODECODESIZE_2,
            )
        val setParameterResponse1 = target.transceive(setParameterCommand1)

        // Check if the command failed based on status flags
        if (!setParameterResponse1.isStatusSuccessful) {
            throw RuntimeException(
                "Set Parameter command failed with ${formatStatus(setParameterResponse1)}"
            )
        }

        results.add(
            buildString {
                appendLine("Set Parameter Test 1 (SRM_TYPE1, NODECODESIZE_2):")
                appendLine("  Response IDM: ${setParameterResponse1.idm.toHexString()}")
                appendLine("  Status: ${formatStatus(setParameterResponse1)}")
                appendLine("  Result: SUCCESS")
            }
        ) // Test with different parameters (SRM_TYPE2, NODECODESIZE_4)
        try {
            val setParameterCommand2 =
                SetParameterCommand(
                    idm = target.idm,
                    encryptionType = SetParameterCommand.EncryptionType.SRM_TYPE2,
                    packetType = SetParameterCommand.PacketType.NODECODESIZE_4,
                )
            val setParameterResponse2 = target.transceive(setParameterCommand2)

            // Check if the command failed based on status flags
            if (!setParameterResponse2.isStatusSuccessful) {
                throw RuntimeException(
                    "Set Parameter Test 2 failed with ${formatStatus(setParameterResponse2)}"
                )
            }

            results.add(
                buildString {
                    appendLine("Set Parameter Test 2 (SRM_TYPE2, NODECODESIZE_4):")
                    appendLine("  Response IDM: ${setParameterResponse2.idm.toHexString()}")
                    appendLine("  Status: ${formatStatus(setParameterResponse2)}")
                    appendLine("  Result: SUCCESS")
                }
            )
        } catch (e: Exception) {
            throw RuntimeException(
                "Set Parameter Test 2 (SRM_TYPE2, NODECODESIZE_4) failed: ${e.message}"
            )
        }

        return StepOutput(
            buildString {
                    appendLine("Set Parameter Command Tests:")
                    appendLine()
                    results.forEach { result ->
                        appendLine(result)
                        appendLine()
                    }
                }
                .trim()
        )
    }
}
