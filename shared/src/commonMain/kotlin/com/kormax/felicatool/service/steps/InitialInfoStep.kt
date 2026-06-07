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
    override fun readSupport(context: CardScanContext): CommandSupport = context.pollingSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(pollingSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        IcTypeRegistry.ensureReady()

        // Use the PMM from the target (already obtained during creation)
        val pmm = target.pmm
        val idmHex = target.idm.toHexString()

        // Store card information in context
        scanContext =
            scanContext.copy(
                primaryIdm = target.idm,
                pmm = pmm,
                primarySystemCode = target.systemCode,
            )

        return StepOutput(
            buildString {
                    appendLine("IDM: $idmHex")
                    // Note: Manufacturer and NFC System Code information not available through
                    // FeliCaTarget interface. These would need to be obtained differently if
                    // needed.
                    appendLine()
                    appendLine("PMM Information:")
                    appendLine("  Raw PMM: ${pmm.toString()}")
                    appendLine("  ROM Type: 0x${byteToHex(pmm.romType)}")
                    appendLine("  IC Type: 0x${byteToHex(pmm.icType)}")
                    IcTypeRegistry.getIcName(pmm.icType, pmm.romType)?.let { icTypeName ->
                        appendLine("  IC Type Name: $icTypeName")
                    }
                    appendLine()
                    appendLine("Timeout Multipliers (ms):")
                    appendLine(
                        "  Variable Response Time: ${formatTimeoutFormula(pmm.variableResponseTimeConstant, pmm.variableResponseTimePerUnit, pmm.variableResponseTimeCommandSupported)}"
                    )
                    appendLine(
                        "  Fixed Response Time: ${formatTimeoutFormula(pmm.fixedResponseTimeConstant, pmm.fixedResponseTimePerUnit, pmm.fixedResponseTimeCommandSupported)}"
                    )
                    appendLine(
                        "  Mutual Auth: ${formatTimeoutFormula(pmm.mutualAuthConstant, pmm.mutualAuthPerUnit, pmm.mutualAuthCommandSupported)}"
                    )
                    appendLine(
                        "  Data Read: ${formatTimeoutFormula(pmm.dataReadConstant, pmm.dataReadPerUnit, pmm.dataReadCommandSupported)}"
                    )
                    appendLine(
                        "  Data Write: ${formatTimeoutFormula(pmm.dataWriteConstant, pmm.dataWritePerUnit, pmm.dataWriteCommandSupported)}"
                    )
                    appendLine(
                        "  Other: ${formatTimeoutFormula(pmm.otherConstant, pmm.otherPerUnit, pmm.otherCommandSupported)}"
                    )
                }
                .trimEnd()
        )
    }
}
