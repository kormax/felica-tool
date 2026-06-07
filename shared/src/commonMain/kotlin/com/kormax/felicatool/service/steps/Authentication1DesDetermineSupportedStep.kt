package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon

internal object Authentication1DesDetermineSupportedStep :
    CommandSupportScanStep(
        id = "authentication1_des_determine_supported",
        title = "Authenticate1 DES",
        description = "Attempt DES authentication with discovered nodes",
        icon = ScanStepIcon.LOCK,
    ) {
    override fun readSupport(context: CardScanContext): CommandSupport =
        context.authentication1DesSupport

    override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(authentication1DesSupport = support)

    override suspend fun ScanSession.perform(): StepOutput {
        val testTarget = scanContext.findBestAuthentication1DesTarget()
        if (testTarget == null) {
            throw StepPreconditionNotMet(
                "No suitable system found for DES authentication (root area with valid DES key is required)."
            )
        }
        ensureCardPresence(target)

        val systemCodeHex = testTarget.systemContext.systemCode?.toHexString() ?: "unknown"
        ScanLog.d(
            "CardScanService",
            "Selected system $systemCodeHex for DES authentication using root area ${testTarget.rootArea.code.toHexString()} in area and node lists (node count: ${testTarget.systemContext.nodes.size})",
        )

        // Generate a random challenge1A (8 bytes)
        val challenge1A = ByteArray(8) { 0x00.toByte() }

        val areasToAuth = listOf(testTarget.rootArea)
        // Area0 may appear in both lists: this is allowed because key updates can target areas.
        val nodesToAuth = listOf<Node>(testTarget.rootArea)

        var selectedSystemIdmUsed: ByteArray? = null
        val authenticateResponse =
            transceiveWithRetries(
                target = target,
                commandLabel = "Authentication1DesCommand",
                systemCode = testTarget.systemContext.systemCode,
                maxAttempts = ATTEMPTS_DETERMINE_SUPPORTED,
                retryDelayStepMs = 50,
            ) { activeTarget, _ ->
                val selectedSystemContext =
                    scanContext.systemScanContexts.firstOrNull { context ->
                        context.systemCode.sameBytes(testTarget.systemContext.systemCode)
                    }
                val selectedSystemIdm = selectedSystemContext?.idm ?: activeTarget.idm
                selectedSystemIdmUsed = selectedSystemIdm

                Authentication1DesCommand(
                    idm = selectedSystemIdm,
                    areaNodes = areasToAuth,
                    nodes = nodesToAuth,
                    challenge1A = challenge1A,
                )
            }
        setCurrentMode(Mode.Mode1.Des, selectedSystemCode = testTarget.systemContext.systemCode)

        val resetModeResult =
            resetAuthenticationState(
                target = target,
                authenticatedSystemCode = testTarget.systemContext.systemCode,
                authenticatedSystemIdm = selectedSystemIdmUsed,
            )

        return StepOutput(
            buildString {
                    appendLine("DES Authentication Results:")
                    appendLine("Selected system: $systemCodeHex")
                    appendLine("Using root area in both area and node lists for support check")
                    appendLine("Challenge1A (sent): ${challenge1A.toHexString()}")
                    appendLine(
                        "Challenge1B (received): ${authenticateResponse.challenge1B.toHexString()}"
                    )
                    appendLine(
                        "Challenge2A (received): ${authenticateResponse.challenge2A.toHexString()}"
                    )
                    appendLine()

                    if (areasToAuth.isNotEmpty()) {
                        appendLine("Areas authenticated:")
                        areasToAuth.forEachIndexed { index, area ->
                            val keyType =
                                when {
                                    testTarget.systemContext.nodeDesKeyVersions.containsKey(area) ->
                                        "DES key"
                                    testTarget.systemContext.nodeKeyVersions.containsKey(area) ->
                                        "Legacy (DES) key"
                                    else -> "Unknown"
                                }
                            appendLine(
                                "  ${index + 1}. Area ${area.number}-${area.endNumber} (${area.code.toHexString()}) - $keyType"
                            )
                        }
                        appendLine()
                    }

                    if (nodesToAuth.isNotEmpty()) {
                        appendLine("Nodes authenticated:")
                        nodesToAuth.forEachIndexed { index, node ->
                            val keyType =
                                when {
                                    testTarget.systemContext.nodeDesKeyVersions.containsKey(node) ->
                                        "DES key"
                                    testTarget.systemContext.nodeKeyVersions.containsKey(node) ->
                                        "Legacy (DES) key"
                                    else -> "Unknown"
                                }
                            appendLine("  ${index + 1}. ${describeNode(node)} - $keyType")
                        }
                        appendLine()
                    }

                    appendLine()
                    appendLine("$resetModeResult")
                }
                .trim()
        )
    }
}
