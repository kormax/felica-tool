package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon
import com.kormax.felicatool.util.IcTypeRegistry

internal object InitialInfoStep :
    CommandSupportScanStep(
        id = "polling",
        title = "Initial Info",
        description = "Reading card IDM and PMM data",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.polling.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands { copy(polling = polling.copy(supported = support)) }

    override suspend fun ScanSession.perform(): StepOutput {
        IcTypeRegistry.ensureReady()

        // Use the PMM already obtained during target creation.
        val cardPmm = pmm
        val cardIdm = idm
        val idmHex = cardIdm.toHexString()

        // Store card information in context
        scanContext =
            scanContext.copy(
                primaryIdm = cardIdm,
                pmm = cardPmm,
                primarySystemCode = systemCode,
            )

        return StepOutput(
            buildString {
                    appendLine("IDM: $idmHex")
                    // Note: Manufacturer and NFC System Code information not available through
                    // FeliCaTarget interface. These would need to be obtained differently if
                    // needed.
                    appendLine()
                    appendLine("PMM Information:")
                    appendLine("  Raw PMM: ${cardPmm.toString()}")
                    appendLine("  ROM Type: 0x${byteToHex(cardPmm.romType)}")
                    appendLine("  IC Type: 0x${byteToHex(cardPmm.icType)}")
                    IcTypeRegistry.getIcName(cardPmm.icType, cardPmm.romType)?.let { icTypeName ->
                        appendLine("  IC Type Name: $icTypeName")
                    }
                    appendLine()
                    appendLine("Timeout Multipliers (ms):")
                    appendLine(
                        "  Variable Response Time: ${formatTimeoutFormula(cardPmm.variableResponseTimeConstant, cardPmm.variableResponseTimePerUnit, cardPmm.variableResponseTimeCommandSupported)}"
                    )
                    appendLine(
                        "  Fixed Response Time: ${formatTimeoutFormula(cardPmm.fixedResponseTimeConstant, cardPmm.fixedResponseTimePerUnit, cardPmm.fixedResponseTimeCommandSupported)}"
                    )
                    appendLine(
                        "  Mutual Auth: ${formatTimeoutFormula(cardPmm.mutualAuthConstant, cardPmm.mutualAuthPerUnit, cardPmm.mutualAuthCommandSupported)}"
                    )
                    appendLine(
                        "  Data Read: ${formatTimeoutFormula(cardPmm.dataReadConstant, cardPmm.dataReadPerUnit, cardPmm.dataReadCommandSupported)}"
                    )
                    appendLine(
                        "  Data Write: ${formatTimeoutFormula(cardPmm.dataWriteConstant, cardPmm.dataWritePerUnit, cardPmm.dataWriteCommandSupported)}"
                    )
                    appendLine(
                        "  Other: ${formatTimeoutFormula(cardPmm.otherConstant, cardPmm.otherPerUnit, cardPmm.otherCommandSupported)}"
                    )
                }
                .trimEnd()
        )
    }
}
