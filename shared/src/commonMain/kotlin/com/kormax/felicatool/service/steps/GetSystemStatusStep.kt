package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetSystemStatusStep :
    CommandSupportScanStep(
        id = "get_system_status",
        title = "Get System Status",
        description = "Getting current system status information from the card",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getSystemStatusSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getSystemStatusSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.systemScanContexts.isEmpty()) {
            throw StepPreconditionNotMet(
                "No systems have been discovered. Please run system discovery first."
            )
        }
        ensureCardPresence(target)

        var errors = 0
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val systemCodeHex = systemContext.systemCode?.toHexString()?.uppercase() ?: "unknown"

            try {
                val getSystemStatusCommand = GetSystemStatusCommand(target.idm)
                val getSystemStatusResponse = target.transceive(getSystemStatusCommand)

                // Store system status as ByteArray in context
                val systemStatusData =
                    byteArrayOf(
                        getSystemStatusResponse.statusFlag1,
                        getSystemStatusResponse.statusFlag2,
                        getSystemStatusResponse.flag,
                    ) + getSystemStatusResponse.data

                // Update system context with system status
                val updatedSystemContext = systemContext.copy(systemStatus = systemStatusData)
                updatedSystemContexts.add(updatedSystemContext)

                // Build result for this system
                val systemResult = buildString {
                    appendLine("System ${contextIndex + 1} ($systemCodeHex):")
                    appendLine(
                        "  Status Flags: ${formatStatus(getSystemStatusResponse, prefix = "")}"
                    )
                    appendLine("  Flag: 0x${byteToHex(getSystemStatusResponse.flag)}")

                    if (getSystemStatusResponse.data.isNotEmpty()) {
                        appendLine("  Data: ${getSystemStatusResponse.data.toHexString()}")
                    } else {
                        appendLine("  Data: None")
                    }
                }

                results.add(systemResult)
            } catch (e: Exception) {
                errors++
                updatedSystemContexts.add(systemContext) // Keep original context
                results.add(
                    "System ${contextIndex + 1} ($systemCodeHex): Failed to get system status - ${e.message}"
                )
            }
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        if (errors > 0) {
            throw RuntimeException("Get System Status encountered $errors error(s)")
        }

        return StepOutput(
            buildString {
                    appendLine("System Status Information:")
                    appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                    appendLine()
                    results.forEach { result -> appendLine(result) }
                }
                .trim()
        )
    }
}
