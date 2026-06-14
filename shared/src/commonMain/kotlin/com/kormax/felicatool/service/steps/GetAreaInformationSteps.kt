package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

private data class GetAreaInformationTestTarget(
    val systemContext: SystemScanContext,
    val area: Area,
)

private fun CardScanContext.findGetAreaInformationTestTarget(): GetAreaInformationTestTarget {
    val systemContext =
        systemScanContexts.firstOrNull { context -> context.nodes.any { node -> node is Area } }
            ?: throw StepSkipped(
                "No areas discovered. Get Area Information requires discovered areas from Discover Nodes step."
            )
    val area = systemContext.nodes.filterIsInstance<Area>().first()
    return GetAreaInformationTestTarget(systemContext = systemContext, area = area)
}

internal object GetAreaInformationDetermineSupportedStep :
    CommandSupportScanStep(
        id = "get_area_information_determine_supported",
        title = "Get Area Information - Supported",
        description = "Check whether Get Area Information is available",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getAreaInformation.supported

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getAreaInformation = getAreaInformation.copy(supported = support))
    }

    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findGetAreaInformationTestTarget()
        val response =
            executeCommand(withSelectedSystemCode = testTarget.systemContext.systemCode) {
                GetAreaInformationCommand(idm, testTarget.area)
            }

        return StepOutput(
            buildString {
                    appendLine("Get Area Information command is supported (response received)")
                    appendLine(
                        "Area: ${testTarget.area.number} (${testTarget.area.code.toHexString()})"
                    )
                    appendLine("Status: ${formatStatus(response)}")
                    if (response.isStatusSuccessful) {
                        appendLine("Node Code: ${response.nodeCode.toHexString()}")
                        appendLine("Data: ${response.data.toHexString()}")
                    }
                }
                .trim()
        )
    }
}

internal object GetAreaInformationDetermineTrailingDataSupportedStep :
    CommandTrailingDataSupportedScanStep<GetAreaInformationResponse>(
        id = "get_area_information_determine_trailing_data_supported",
        title = "Get Area Information - Trailing Data Supported",
        description = "Check whether Get Area Information accepts trailing data bytes",
        icon = ScanStepIcon.SEARCH,
        commandName = "Get Area Information",
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.commands.getAreaInformation.supported

    override fun writeTrailingDataSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.withCommands {
        copy(getAreaInformation = getAreaInformation.copy(trailingDataSupported = support))
    }

    override fun ScanSession.selectedSystemCode(): ByteArray? =
        scanContext.findGetAreaInformationTestTarget().systemContext.systemCode

    override fun ScanSession.createCommand(
        scope: CommandExecutionScope,
        trailingData: ByteArray,
    ): FelicaCommand<GetAreaInformationResponse> {
        val testTarget = scanContext.findGetAreaInformationTestTarget()
        return GetAreaInformationCommand(
            idm = scope.idm,
            node = testTarget.area,
            trailingData = trailingData,
        )
    }

    override fun responseLines(response: GetAreaInformationResponse): List<String> =
        listOf("Status: ${formatStatus(response)}")
}

internal object GetAreaInformationStep :
    ScanStep(
        id = "get_area_information",
        title = "Get Area Information",
        description = "Get information about discovered areas",
        icon = ScanStepIcon.INFO,
    ) {
    override fun commandSupport(context: CardScanContext): CommandSupport =
        context.commands.getAreaInformation.supported

    override suspend fun ScanSession.perform(): StepOutput {
        val allAreas = scanContext.systemScanContexts.flatMap { it.nodes.filterIsInstance<Area>() }

        if (allAreas.isEmpty()) {
            throw StepSkipped(
                "No areas discovered. Get Area Information requires discovered areas from Discover Nodes step."
            )
        }
        val results = mutableListOf<String>()
        val maxAreasPerRequest = 10 // Process areas in smaller batches to avoid overwhelming output
        var totalSuccessful = 0
        var totalTested = 0

        // Process areas in batches across all system contexts
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            val systemAreas = systemContext.nodes.filterIsInstance<Area>()
            if (systemAreas.isEmpty()) {
                continue
            }

            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"
            val systemResults = mutableListOf<String>()
            var systemSuccessful = 0

            systemAreas.chunked(maxAreasPerRequest).forEachIndexed { batchIndex, areaBatch ->
                val batchResults = mutableListOf<String>()

                areaBatch.forEach { area ->
                    totalTested++
                    val getAreaInformationResponse =
                        executeCommand(withSelectedSystemCode = systemContext.systemCode) {
                            GetAreaInformationCommand(idm, area)
                        }

                    if (getAreaInformationResponse.isStatusSuccessful) {
                        totalSuccessful++
                        systemSuccessful++
                        batchResults.add(
                            buildString {
                                appendLine("  Area ${area.number} (${area.code.toHexString()}):")
                                appendLine("    Status: SUCCESS")
                                appendLine(
                                    "    Node Code: ${getAreaInformationResponse.nodeCode.toHexString()}"
                                )
                                appendLine(
                                    "    Data: ${getAreaInformationResponse.data.toHexString()}"
                                )
                            }
                        )
                    } else {
                        val status1Hex = byteToHex(getAreaInformationResponse.statusFlag1)
                        val status2Hex = byteToHex(getAreaInformationResponse.statusFlag2)
                        val statusDescription =
                            when {
                                getAreaInformationResponse.statusFlag1 == 0xFF.toByte() &&
                                    getAreaInformationResponse.statusFlag2 == 0xE0.toByte() ->
                                    "Area 0 error"
                                getAreaInformationResponse.statusFlag1 == 0xFF.toByte() &&
                                    getAreaInformationResponse.statusFlag2 == 0xE7.toByte() ->
                                    "High bits set in code"
                                getAreaInformationResponse.statusFlag1 == 0xFF.toByte() &&
                                    getAreaInformationResponse.statusFlag2 == 0xE2.toByte() ->
                                    "Code doesn't represent area"
                                else -> "Unknown error"
                            }

                        batchResults.add(
                            buildString {
                                appendLine("  Area ${area.number} (${area.code.toHexString()}):")
                                appendLine(
                                    "    Status: ERROR (0x$status1Hex 0x$status2Hex - $statusDescription)"
                                )
                            }
                        )
                    }
                }

                if (batchResults.isNotEmpty()) {
                    systemResults.addAll(batchResults)
                }
            }

            if (systemResults.isNotEmpty()) {
                results.add(
                    buildString {
                        appendLine(
                            "System Context ${contextIndex + 1} ($systemCodeHex): $systemSuccessful/${systemAreas.size} areas successful"
                        )
                        systemResults.forEach { appendLine(it.trimEnd()) }
                    }
                )
            }
        }

        val collapsedResult =
            "Got area information for $totalSuccessful/$totalTested area(s) across ${scanContext.systemScanContexts.size} system(s)"
        val expandedResult =
            buildString {
                    appendLine(
                        "Get Area Information Results: $totalSuccessful/$totalTested areas returned data"
                    )
                    appendLine()
                    results.forEach { result ->
                        appendLine(result.trimEnd())
                        appendLine()
                    }
                }
                .trim()

        return StepOutput(result = expandedResult, collapsedResult = collapsedResult)
    }
}
