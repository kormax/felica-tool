package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object GetAreaInformationStep :
    CommandSupportScanStep(
        id = "get_area_information",
        title = "Get Area Information",
        description = "Get information about discovered areas",
        icon = ScanStepIcon.INFO,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.getAreaInformationSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(getAreaInformationSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val allAreas = scanContext.systemScanContexts.flatMap { it.nodes.filterIsInstance<Area>() }

        if (allAreas.isEmpty()) {
            throw StepPreconditionNotMet(
                "No areas discovered. Get Area Information requires discovered areas from Discover Nodes step."
            )
        }
        ensureCardPresence(target)

        val results = mutableListOf<String>()
        val maxAreasPerRequest = 10 // Process areas in smaller batches to avoid overwhelming output
        var totalSuccessful = 0
        var totalTested = 0

        // Process areas in batches across all system contexts
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

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
                    val getAreaInformationCommand = GetAreaInformationCommand(target.idm, area)
                    val getAreaInformationResponse = target.transceive(getAreaInformationCommand)

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
