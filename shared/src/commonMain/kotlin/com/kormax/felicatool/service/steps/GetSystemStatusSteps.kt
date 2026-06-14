package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetSystemStatusDetermineSupportedStep :
    CommandSupportScanStep(
        id = "get_system_status_determine_supported",
        title = "Get System Status - Supported",
        description = "Check whether Get System Status is available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getSystemStatus.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getSystemStatus = getSystemStatus.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val response =
            executeCommand(withSelectedSystemCode = SYSTEM_CODE_WILDCARD) {
                GetSystemStatusCommand(idm)
            }

        return StepOutput(
            buildString {
                    appendLine("Get System Status command is supported (response received)")
                    appendLine("Status Flags: ${formatStatus(response, prefix = "")}")
                    appendLine("Flag: 0x${byteToHex(response.flag)}")
                }
                .trim()
        )
    }
}

internal object GetSystemStatusDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<GetSystemStatusResponse>(
        id = "get_system_status_determine_trailing_data_supported",
        title = "Get System Status - Trailing Data Supported",
        description = "Check whether Get System Status accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Get System Status",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getSystemStatus.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getSystemStatus = getSystemStatus.copy(trailingDataSupported = support))
    }

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<GetSystemStatusResponse> =
        GetSystemStatusCommand(idm = scope.idm, trailingData = trailingData)

    override fun responseLines(response: GetSystemStatusResponse): List<String> =
        listOf(
            "Status Flags: ${formatStatus(response, prefix = "")}",
            "Flag: 0x${byteToHex(response.flag)}",
        )
}

internal object GetSystemStatusesStep :
    ScanStep(
        id = "get_system_statuses",
        title = "Get System Statuses",
        description = "Get system statuses for discovered systems",
        icon = ScanStepIcon.INFO,
    ) {
    override fun commandSupport(context: CardScanContext): CommandSupport =
        context.commands.getSystemStatus.supported

    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.systemScanContexts.isEmpty()) {
            throw StepPreconditionNotMet(
                "No systems have been discovered. Please run system discovery first."
            )
        }

        var errors = 0
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            val systemCodeHex = systemContext.systemCode?.toHexString()?.uppercase() ?: "unknown"

            try {
                val getSystemStatusResponse =
                    executeCommand(withSelectedSystemCode = systemContext.systemCode) {
                        GetSystemStatusCommand(idm)
                    }

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
                    appendLine("System Statuses:")
                    appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                    appendLine()
                    results.forEach { result -> appendLine(result) }
                }
                .trim()
        )
    }
}
