package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

private val manualSystemProbeTargets =
    listOf(
        byteArrayOf(0x12.toByte(), 0xFC.toByte()) to "NDEF",
        byteArrayOf(0x88.toByte(), 0xB4.toByte()) to "FeliCa Lite",
        byteArrayOf(0x95.toByte(), 0x7A.toByte()) to "FeliCa Secure ID",
    )
private const val WILDCARD_SYSTEM_PROBE_FIRST_PREFIX = 0x00
private const val WILDCARD_SYSTEM_PROBE_LAST_PREFIX = 0xFE

internal object ProbeSystemCodesManuallyStep :
    ScanStep(
        id = "probe_system_codes_manually",
        title = "Probe System Codes Manually",
        description = "Probe known system codes not reported by Request System Code",
        icon = ScanStepIcon.SEARCH,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        ensureCardPresence(target)

        val requestSystemCodeSucceeded =
            scanContext.requestSystemCodeSupport == CommandSupport.SUPPORTED
        val reportedSystemCodes =
            if (requestSystemCodeSucceeded) {
                scanContext.discoveredSystemCodes
            } else {
                emptyList()
            }

        val targetsToProbe =
            if (requestSystemCodeSucceeded) {
                manualSystemProbeTargets.filterNot { (systemCode, _) ->
                    reportedSystemCodes.containsBytes(systemCode)
                }
            } else {
                manualSystemProbeTargets
            }
        val skippedTargets =
            if (requestSystemCodeSucceeded) {
                manualSystemProbeTargets.filter { (systemCode, _) ->
                    reportedSystemCodes.containsBytes(systemCode)
                }
            } else {
                emptyList()
            }

        val manualResultLines = mutableListOf<String>()
        var manualFoundCount = 0
        var manualAddedCount = 0

        for ((systemCode, label) in targetsToProbe) {
            val systemCodeHex = systemCode.toHexString().uppercase()
            try {
                pollSystemCode(target, systemCode)
                val contextAdded = addOrUpdateSystemContext(systemCode, target.idm)
                val contextStatus =
                    if (contextAdded) {
                        "added system context"
                    } else {
                        "system context already present"
                    }

                manualFoundCount++
                if (contextAdded) {
                    manualAddedCount++
                }
                manualResultLines +=
                    "  - $systemCodeHex ($label): found (IDM ${target.idm.toHexString().uppercase()}; $contextStatus)"
            } catch (e: Exception) {
                val error = e.message ?: e::class.simpleName ?: "Unknown error"
                manualResultLines += "  - $systemCodeHex ($label): not found ($error)"
            }
        }

        val wildcardResultLines = mutableListOf<String>()
        var wildcardFoundCount = 0
        var wildcardAddedCount = 0
        var wildcardSkipped = 0
        var wildcardNoResponse = 0

        if (settings.bruteForceSystemCodePrefixes) {
            for (prefix in WILDCARD_SYSTEM_PROBE_FIRST_PREFIX..WILDCARD_SYSTEM_PROBE_LAST_PREFIX) {
                val probeSystemCode = byteArrayOf(prefix.toByte(), 0xFF.toByte())
                val knownSystemCodes =
                    reportedSystemCodes.toUniqueByteArrays() +
                        scanContext.systemScanContexts.mapNotNull { context ->
                            context.systemCode
                        }

                if (
                    knownSystemCodes.any { knownCode ->
                        knownCode.isNotEmpty() &&
                            probeSystemCode.isNotEmpty() &&
                            knownCode[0] == probeSystemCode[0]
                    }
                ) {
                    wildcardSkipped++
                    continue
                }

                try {
                    val pollingCommand =
                        PollingCommand(
                            systemCode = probeSystemCode,
                            requestCode = RequestCode.SYSTEM_CODE_REQUEST,
                            timeSlot = TimeSlot.SLOT_1,
                        )
                    val pollingResponse =
                        transceiveWithRetries(target = target, command = pollingCommand)

                    val discoveredSystemCode =
                        if (pollingResponse.hasRequestData) {
                            pollingResponse.systemCode
                        } else {
                            probeSystemCode
                        }

                    val contextAdded =
                        addOrUpdateSystemContext(discoveredSystemCode, pollingResponse.idm)
                    val contextStatus =
                        if (contextAdded) {
                            "added system context"
                        } else {
                            "system context already present"
                        }
                    wildcardFoundCount++
                    if (contextAdded) {
                        wildcardAddedCount++
                    }
                    wildcardResultLines +=
                        "    - ${probeSystemCode.toHexString().uppercase()} -> ${discoveredSystemCode.toHexString().uppercase()} " +
                            "(IDM ${pollingResponse.idm.toHexString().uppercase()}; $contextStatus)"
                } catch (e: Exception) {
                    wildcardNoResponse++
                }
            }
        }

        return StepOutput(
            buildString {
                    appendLine("Probe System Codes Manually Results:")

                    if (skippedTargets.isNotEmpty()) {
                        appendLine("Skipped reported candidate(s):")
                        skippedTargets.forEach { (systemCode, label) ->
                            appendLine("  - ${systemCode.toHexString().uppercase()} ($label)")
                        }
                        appendLine()
                    }

                    if (manualResultLines.isEmpty()) {
                        appendLine("No manual probes needed.")
                    } else {
                        appendLine("Probed candidate(s):")
                        manualResultLines.forEach(::appendLine)
                    }

                    if (settings.bruteForceSystemCodePrefixes) {
                        appendLine()
                        appendLine("Wildcard suffix brute force:")
                        appendLine(
                            "  Range: ${
                                    byteToHex(WILDCARD_SYSTEM_PROBE_FIRST_PREFIX)
                                }FF-${
                                    byteToHex(WILDCARD_SYSTEM_PROBE_LAST_PREFIX)
                                }FF"
                        )
                        appendLine("  Skipped known prefixes: $wildcardSkipped")
                        appendLine("  No response: $wildcardNoResponse")
                        if (wildcardResultLines.isNotEmpty()) {
                            appendLine("  Found:")
                            wildcardResultLines.forEach(::appendLine)
                        }
                    }

                    appendLine()
                    appendLine(
                        "Found ${manualFoundCount + wildcardFoundCount} system(s); added ${manualAddedCount + wildcardAddedCount} new system context(s)."
                    )
                }
                .trim()
        )
    }

    private fun ScanSession.addOrUpdateSystemContext(
        systemCode: ByteArray,
        idm: ByteArray,
    ): Boolean {
        var added = true
        var idmUpdated = false
        val updatedContexts =
            scanContext.systemScanContexts.map { context ->
                if (!context.systemCode.sameBytes(systemCode)) {
                    return@map context
                }

                added = false
                if (context.idm?.contentEquals(idm) != true) {
                    idmUpdated = true
                    context.copy(idm = idm)
                } else {
                    context
                }
            }

        scanContext =
            if (added) {
                scanContext.copy(
                    systemScanContexts =
                        scanContext.systemScanContexts +
                            SystemScanContext(systemCode = systemCode, idm = idm)
                )
            } else if (idmUpdated) {
                scanContext.copy(systemScanContexts = updatedContexts)
            } else {
                scanContext
            }

        return added
    }
}
