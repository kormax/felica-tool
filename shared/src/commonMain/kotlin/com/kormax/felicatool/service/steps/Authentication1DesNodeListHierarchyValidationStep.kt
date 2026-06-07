package com.kormax.felicatool.service.steps

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TagUnavailableException
import com.kormax.felicatool.service.*
import com.kormax.felicatool.ui.ScanStepIcon
import kotlinx.coroutines.CancellationException

private const val AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS = 3

internal object Authentication1DesNodeListHierarchyValidationStep :
    ScanStep(
        id = "authentication1_des_node_list_hierarchy_validation",
        title = "Authenticate1 DES: Node List Hierarchy Validation",
        description =
            "Check Authenticate1 DES validation behavior for nodes that aren't immediate children of specified areas",
        icon = ScanStepIcon.LOCK,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        if (scanContext.authentication1DesSupport != CommandSupport.SUPPORTED) {
            throw StepPreconditionNotMet(
                "Authenticate1 DES support is not confirmed; cannot check node-list hierarchy validation"
            )
        }

        val preferredTarget = scanContext.findBestAuthentication1DesTarget()
        if (preferredTarget == null) {
            throw StepPreconditionNotMet(
                "No suitable system found for Authenticate1 DES node-list hierarchy validation (root area with valid DES key is required)."
            )
        }

        val modeBeforeCheck = currentMode
        if (modeBeforeCheck != Mode.Mode0) {
            throw StepPreconditionNotMet(
                "Authenticate1 DES node-list hierarchy validation requires Mode 0 (current: $modeBeforeCheck)."
            )
        }

        val nonRootAreas =
            preferredTarget.systemContext.nodes.filterIsInstance<Area>().filter { area ->
                area != preferredTarget.rootArea &&
                    !area.isRoot &&
                    area.belongsTo(preferredTarget.rootArea)
            }
        val serviceUnderNonRootArea =
            preferredTarget.systemContext.nodes
                .filterIsInstance<Service>()
                .filter { service -> nonRootAreas.any { area -> service.belongsTo(area) } }
                .sortedBy { it.number }
                .firstOrNull()
        val nestedAreaUnderNonRootArea =
            if (serviceUnderNonRootArea == null) {
                nonRootAreas
                    .filter { candidate ->
                        nonRootAreas.any { parent ->
                            parent != candidate && candidate.belongsTo(parent)
                        }
                    }
                    .sortedBy { it.number }
                    .firstOrNull()
            } else {
                null
            }
        val nonImmediateNode = serviceUnderNonRootArea ?: nestedAreaUnderNonRootArea
        if (nonImmediateNode == null) {
            throw StepPreconditionNotMet(
                "No node found under an area under root area; cannot check Authenticate1 DES node-list hierarchy validation."
            )
        }
        ensureCardPresence(target)

        val challenge1A = ByteArray(8) { 0x00.toByte() }
        val areasToAuth = listOf(preferredTarget.rootArea)
        // Area0 may appear in both lists: this is allowed because key updates can target areas.
        val nodesToAuth = listOf<Node>(preferredTarget.rootArea, nonImmediateNode)

        var selectedSystemIdmUsed: ByteArray? = null

        val response =
            try {
                transceiveWithRetries(
                    target = target,
                    systemCode = preferredTarget.systemContext.systemCode,
                    maxAttempts = AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS,
                    retryDelayStepMs = 50,
                ) { activeTarget, _ ->
                    val currentSystemContext =
                        scanContext.systemScanContexts.firstOrNull { context ->
                            context.systemCode.sameBytes(preferredTarget.systemContext.systemCode)
                        }
                    val selectedSystemIdm = currentSystemContext?.idm ?: activeTarget.idm
                    selectedSystemIdmUsed = selectedSystemIdm

                    Authentication1DesCommand(
                        idm = selectedSystemIdm,
                        areaNodes = areasToAuth,
                        nodes = nodesToAuth,
                        challenge1A = challenge1A,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                null
            }

        if (response != null) {
            setCurrentMode(
                Mode.Mode1.Des,
                selectedSystemCode = preferredTarget.systemContext.systemCode,
            )
        }

        val resetStateResult =
            resetAuthenticationState(
                target = target,
                authenticatedSystemCode = preferredTarget.systemContext.systemCode,
                authenticatedSystemIdm = selectedSystemIdmUsed,
            )

        val validationBehavior =
            if (response != null) {
                Authentication1DesNodeListHierarchyValidation.LENIENT
            } else {
                Authentication1DesNodeListHierarchyValidation.STRICT
            }
        scanContext =
            scanContext.copy(authentication1DesNodeListHierarchyValidation = validationBehavior)

        return StepOutput(
            buildString {
                    appendLine("Authenticate1 DES node-list validation check:")
                    appendLine(
                        "System: ${preferredTarget.systemContext.systemCode?.toHexString()?.uppercase() ?: "unknown"}"
                    )
                    appendLine("Mode before check: $modeBeforeCheck")
                    appendLine("Area list:")
                    appendLine("  1. ${describeNode(preferredTarget.rootArea)}")
                    appendLine("Node list:")
                    appendLine("  1. ${describeNode(preferredTarget.rootArea)}")
                    appendLine("  2. ${describeNode(nonImmediateNode)}")
                    appendLine("Challenge1A: ${challenge1A.toHexString().uppercase()}")
                    if (response != null) {
                        appendLine("Challenge1B: ${response.challenge1B.toHexString().uppercase()}")
                        appendLine("Challenge2A: ${response.challenge2A.toHexString().uppercase()}")
                    } else {
                        appendLine(
                            "No response after $AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS attempts"
                        )
                    }
                    appendLine("Validation behavior: $validationBehavior")
                    appendLine()
                    appendLine(resetStateResult)
                }
                .trim()
        )
    }
}
