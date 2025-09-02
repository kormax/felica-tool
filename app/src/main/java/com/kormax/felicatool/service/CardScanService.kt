package com.kormax.felicatool.service

import android.util.Log
import com.kormax.felicatool.felica.*
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.util.IcTypeMapping
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.delay

/** Context class to store discovered card data across multiple scan steps */
data class CardScanContext(
    val systemScanContexts: List<SystemScanContext> = emptyList(),
    val primaryIdm: ByteArray? = null,
    val pmm: Pmm? = null,
    val primarySystemCode: ByteArray? = null,
    val discoveredSystemCodes: List<ByteArray> = emptyList(),
    val communicationPerformance: CommunicationPerformance? = null,
    val specificationVersion: RequestSpecificationVersionResponse? = null,
    val containerIssueInformation: ContainerInformation? = null,
    val secureElementInformation: GetSecureElementInformationResponse? = null,
    val containerIdm: ByteArray? = null,
    val errorLocationIndication: ErrorLocationIndication = ErrorLocationIndication.INDEX,
    val maxBlocksPerRequest: Int? = null,
    val maxServicesPerRequest: Int? = null,
    // Command support
    val pollingSupport: CommandSupport = CommandSupport.UNKNOWN,
    val pollingSystemCodeSupport: CommandSupport = CommandSupport.UNKNOWN,
    val pollingCommunicationPerformanceSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestResponseSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestSystemCodeSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestSpecificationVersionSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getSystemStatusSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestCodeListSupport: CommandSupport = CommandSupport.UNKNOWN,
    val searchServiceCodeSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestServiceSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestServiceV2Support: CommandSupport = CommandSupport.UNKNOWN,
    val setParameterSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getContainerIssueInformationSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getSecureElementInformationSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getContainerIdSupport: CommandSupport = CommandSupport.UNKNOWN,
    val echoSupport: CommandSupport = CommandSupport.UNKNOWN,
    val resetModeSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getNodePropertyValueLimitedServiceSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getNodePropertyMacCommunicationSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestBlockInformationSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestBlockInformationExSupport: CommandSupport = CommandSupport.UNKNOWN,
    val readBlocksWithoutEncryptionSupport: CommandSupport = CommandSupport.UNKNOWN,
) {}

data class SystemScanContext(
    val systemCode: ByteArray? = null,
    val nodes: List<Node> = emptyList(),
    val nodeKeyVersions: Map<Node, KeyVersion> = emptyMap(),
    val nodeAesKeyVersions: Map<Node, KeyVersion> = emptyMap(),
    val nodeDesKeyVersions: Map<Node, KeyVersion> = emptyMap(),
    val encryptionIdentifier: EncryptionIdentifier? = null,
    val nodeBlockCounts: Map<Node, CountInformation> = emptyMap(),
    val nodeAssignedBlockCounts: Map<Node, CountInformation> = emptyMap(),
    val nodeFreeBlockCounts: Map<Node, CountInformation> = emptyMap(),
    val serviceBlockData: Map<Node, ByteArray> = emptyMap(),
    val nodeValueLimitedPurseProperties: Map<Node, ValueLimitedPurseServiceProperty> = emptyMap(),
    val nodeMacCommunicationProperties: Map<Node, MacCommunicationProperty> = emptyMap(),
    val systemStatus: ByteArray? = null,
    val idm: ByteArray? = null,
)

class CardScanService {

    // Context to store discovered nodes across steps
    private var scanContext = CardScanContext()

    // Public getter for context
    fun getScanContext(): CardScanContext = scanContext

    /** Updates the scan context with command support status */
    private fun updateCommandSupport(commandId: String, support: CommandSupport) {
        scanContext =
            when (commandId) {
                "polling" -> scanContext.copy(pollingSupport = support)
                "polling_system_code" -> scanContext.copy(pollingSystemCodeSupport = support)
                "polling_communication_performance" ->
                    scanContext.copy(pollingCommunicationPerformanceSupport = support)
                "request_response" -> scanContext.copy(requestResponseSupport = support)
                "request_system_code" -> scanContext.copy(requestSystemCodeSupport = support)
                "request_specification_version" ->
                    scanContext.copy(requestSpecificationVersionSupport = support)
                "get_system_status" -> scanContext.copy(getSystemStatusSupport = support)
                "request_code_list" -> scanContext.copy(requestCodeListSupport = support)
                "search_service_code" -> scanContext.copy(searchServiceCodeSupport = support)
                "request_service" -> scanContext.copy(requestServiceSupport = support)
                "request_service_v2" -> scanContext.copy(requestServiceV2Support = support)
                "set_parameter" -> scanContext.copy(setParameterSupport = support)
                "get_container_issue_information" ->
                    scanContext.copy(getContainerIssueInformationSupport = support)
                "get_secure_element_information" ->
                    scanContext.copy(getSecureElementInformationSupport = support)
                "get_container_id" -> scanContext.copy(getContainerIdSupport = support)
                "echo" -> scanContext.copy(echoSupport = support)
                "reset_mode" -> scanContext.copy(resetModeSupport = support)
                "get_node_property_value_limited_service" ->
                    scanContext.copy(getNodePropertyValueLimitedServiceSupport = support)
                "get_node_property_mac_communication" ->
                    scanContext.copy(getNodePropertyMacCommunicationSupport = support)
                "request_block_information" ->
                    scanContext.copy(requestBlockInformationSupport = support)
                "request_block_information_ex" ->
                    scanContext.copy(requestBlockInformationExSupport = support)
                "read_without_encryption_determine_error_indication" ->
                    scanContext.copy(readBlocksWithoutEncryptionSupport = support)
                "read_without_encryption_determine_max_services" ->
                    scanContext.copy(readBlocksWithoutEncryptionSupport = support)
                "read_without_encryption_determine_max_blocks" ->
                    scanContext.copy(readBlocksWithoutEncryptionSupport = support)
                "read_blocks_without_encryption" ->
                    scanContext.copy(readBlocksWithoutEncryptionSupport = support)
                else -> scanContext
            }
    }

    /** Gets the current command support status for a given command ID */
    private fun getCommandSupport(commandId: String): CommandSupport {
        return when (commandId) {
            "polling" -> scanContext.pollingSupport
            "polling_system_code" -> scanContext.pollingSystemCodeSupport
            "polling_communication_performance" ->
                scanContext.pollingCommunicationPerformanceSupport
            "request_response" -> scanContext.requestResponseSupport
            "request_system_code" -> scanContext.requestSystemCodeSupport
            "request_specification_version" -> scanContext.requestSpecificationVersionSupport
            "get_system_status" -> scanContext.getSystemStatusSupport
            "request_code_list" -> scanContext.requestCodeListSupport
            "search_service_code" -> scanContext.searchServiceCodeSupport
            "request_service" -> scanContext.requestServiceSupport
            "request_service_v2" -> scanContext.requestServiceV2Support
            "set_parameter" -> scanContext.setParameterSupport
            "get_container_issue_information" -> scanContext.getContainerIssueInformationSupport
            "get_secure_element_information" -> scanContext.getSecureElementInformationSupport
            "get_container_id" -> scanContext.getContainerIdSupport
            "echo" -> scanContext.echoSupport
            "reset_mode" -> scanContext.resetModeSupport
            "get_node_property_value_limited_service" ->
                scanContext.getNodePropertyValueLimitedServiceSupport
            "get_node_property_mac_communication" ->
                scanContext.getNodePropertyMacCommunicationSupport
            "request_block_information" -> scanContext.requestBlockInformationSupport
            "request_block_information_ex" -> scanContext.requestBlockInformationExSupport
            "read_without_encryption_determine_error_indication" ->
                scanContext.readBlocksWithoutEncryptionSupport
            "read_without_encryption_determine_max_services" ->
                scanContext.readBlocksWithoutEncryptionSupport
            "read_without_encryption_determine_max_blocks" ->
                scanContext.readBlocksWithoutEncryptionSupport
            "read_blocks_without_encryption" -> scanContext.readBlocksWithoutEncryptionSupport
            else -> CommandSupport.UNKNOWN
        }
    }

    /**
     * Perform system-specific polling before executing commands for a specific system context
     *
     * @param target The FeliCa target to poll
     * @param systemCode The system code to poll, or null for wildcard polling
     * @throws Exception if the card does not respond to system-specific polling
     */
    private suspend fun pollSystemCode(target: FeliCaTarget, systemCode: ByteArray? = null) {
        val pollingSystemCode =
            systemCode
                ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte()) // Use wildcard if no system code
        val pollingCommand =
            PollingCommand(
                systemCode = pollingSystemCode,
                requestCode = RequestCode.NO_REQUEST,
                timeSlot = TimeSlot.SLOT_1,
            )
        val pollingResponse = target.transceive(pollingCommand)
        target.idm = pollingResponse.idm
    }

    suspend fun executeStep(
        step: CardScanStep,
        target: FeliCaTarget,
        onStepUpdate: (CardScanStep) -> Unit,
    ): CardScanStep {
        // Reset context for new scan session (when starting with initial_info)
        if (step.id == "polling") {
            scanContext = CardScanContext()
        }

        // Check card presence before executing any command (except initial_info)
        if (step.id != "polling") {
            var lastException: Exception? = null
            var pollSuccessful = false

            // Try up to 3 attempts
            for (attempt in 1..3) {
                try {
                    pollSystemCode(target)
                    pollSuccessful = true
                    break
                } catch (e: Exception) {
                    lastException = e
                    Log.w(
                        "CardScanService",
                        "Card presence check attempt $attempt failed for step ${step.id}",
                        e,
                    )
                    if (attempt < 3) {
                        delay(50) // Small delay between attempts
                    }
                }
            }

            if (!pollSuccessful) {
                Log.e(
                    "CardScanService",
                    "Card presence check failed after 3 attempts for step ${step.id}",
                    lastException,
                )
                return step.copy(
                    status = StepStatus.ERROR,
                    errorMessage =
                        "Card not responding to polling after 3 attempts - scan terminated",
                    duration = kotlin.time.Duration.ZERO,
                )
            }
        }

        // Check if command is already known to be unsupported
        val commandSupport = getCommandSupport(step.id)
        if (commandSupport == CommandSupport.UNSUPPORTED) {
            // Skip execution for known unsupported commands
            val duration = kotlin.time.Duration.ZERO
            return step.copy(
                status = StepStatus.COMPLETED,
                result = "Command not supported by this card",
                duration = duration,
            )
        }

        // Mark step as in progress
        val inProgressStep = step.copy(status = StepStatus.IN_PROGRESS)
        onStepUpdate(inProgressStep)

        // Add a small delay for better UX
        delay(25)

        // Mark start time for execution measurement
        val startTime = TimeSource.Monotonic.markNow()

        try {
            val resultStep =
                when (step.id) {
                    "search_service_code" -> {
                        val (collapsedResult, expandedResult) = executeSearchServiceCode(target)
                        updateCommandSupport(step.id, CommandSupport.SUPPORTED)
                        step.copy(
                            status = StepStatus.COMPLETED,
                            result = expandedResult,
                            collapsedResult = collapsedResult,
                            isCollapsed = true,
                            // treeData = null // Commented out tree display for now
                        )
                    }
                    "request_service" -> {
                        val (collapsedResult, expandedResult) = executeRequestService(target)
                        updateCommandSupport(step.id, CommandSupport.SUPPORTED)
                        step.copy(
                            status = StepStatus.COMPLETED,
                            result = expandedResult,
                            collapsedResult = collapsedResult,
                            isCollapsed = true,
                        )
                    }
                    "request_service_v2" -> {
                        val (collapsedResult, expandedResult) = executeRequestServiceV2(target)
                        updateCommandSupport(step.id, CommandSupport.SUPPORTED)
                        step.copy(
                            status = StepStatus.COMPLETED,
                            result = expandedResult,
                            collapsedResult = collapsedResult,
                            isCollapsed = true,
                        )
                    }
                    "read_without_encryption_determine_max_services" -> {
                        val result = executeReadWithoutEncryptionDetermineMaxServices(target)
                        updateCommandSupport(step.id, CommandSupport.SUPPORTED)
                        step.copy(status = StepStatus.COMPLETED, result = result)
                    }
                    "read_without_encryption_determine_max_blocks" -> {
                        val result = executeReadWithoutEncryptionDetermineMaxBlocks(target)
                        updateCommandSupport(step.id, CommandSupport.SUPPORTED)
                        step.copy(status = StepStatus.COMPLETED, result = result)
                    }
                    "read_blocks_without_encryption" -> {
                        val (collapsedResult, expandedResult) =
                            executeReadBlocksWithoutEncryption(target)
                        updateCommandSupport(step.id, CommandSupport.SUPPORTED)
                        step.copy(
                            status = StepStatus.COMPLETED,
                            result = expandedResult,
                            collapsedResult = collapsedResult,
                            isCollapsed = true,
                        )
                    }
                    "scan_overview" -> {
                        step.copy(
                            status = StepStatus.COMPLETED,
                            result =
                                "Click to view comprehensive overview of all discovered card data",
                        )
                    }
                    else -> {
                        val result =
                            when (step.id) {
                                "polling" -> executeInitialInfo(target)
                                "request_response" -> executeRequestResponse(target)
                                "request_system_code" -> executeRequestSystemCode(target)
                                "request_specification_version" ->
                                    executeRequestSpecificationVersion(target)
                                "get_system_status" -> executeGetSystemStatus(target)
                                "polling_system_code" -> executePollingSystemCode(target)
                                "polling_communication_performance" ->
                                    executePollingCommunicationPerformance(target)
                                "request_code_list" -> executeRequestCodeList(target)
                                "set_parameter" -> executeSetParameter(target)
                                "get_container_issue_information" ->
                                    executeGetContainerIssueInformation(target)
                                "get_secure_element_information" ->
                                    executeGetSecureElementInformation(target)
                                "get_container_id" -> executeGetContainerId(target)
                                "echo" -> executeEcho(target)
                                "reset_mode" -> executeResetMode(target)
                                "get_node_property_value_limited_service" ->
                                    executeGetNodePropertyValueLimitedService(target)
                                "get_node_property_mac_communication" ->
                                    executeGetNodePropertyMacCommunication(target)
                                "request_block_information" ->
                                    executeRequestBlockInformation(target)
                                "request_block_information_ex" ->
                                    executeRequestBlockInformationEx(target)
                                "read_without_encryption_determine_error_indication" ->
                                    executeReadWithoutEncryptionDetermineErrorIndication(target)
                                else -> "Unknown step"
                            }

                        // Mark command as supported if we reach this point
                        updateCommandSupport(step.id, CommandSupport.SUPPORTED)

                        step.copy(status = StepStatus.COMPLETED, result = result)
                    }
                }

            // Calculate execution duration
            val duration = startTime.elapsedNow()

            // Return step with duration
            return resultStep.copy(duration = duration)
        } catch (e: Exception) {
            Log.e("CardScanService", "Error executing step ${step.id}", e)

            // Mark command as unsupported if it fails
            updateCommandSupport(step.id, CommandSupport.UNSUPPORTED)

            // Calculate execution duration even for errors
            val duration = startTime.elapsedNow()

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Unknown error",
                duration = duration,
            )
        }
    }

    private fun handleDiscoveredSystemCodes(
        discoveredSystemCodes: List<ByteArray>,
        target: FeliCaTarget,
    ): List<SystemScanContext> {
        // Collect all system codes (discovered + inferred)
        val allSystemCodes = mutableSetOf<ByteArray>()
        allSystemCodes.addAll(discoveredSystemCodes)

        // Check for special system codes 12FC and 88B4
        val hasSystemCode12FC =
            discoveredSystemCodes.any {
                it.contentEquals(byteArrayOf(0x12.toByte(), 0xFC.toByte()))
            }
        val hasSystemCode88B4 =
            discoveredSystemCodes.any {
                it.contentEquals(byteArrayOf(0x88.toByte(), 0xB4.toByte()))
            }

        // Add the complementary system code if one of the special codes is found
        if (hasSystemCode12FC && !hasSystemCode88B4) {
            allSystemCodes.add(byteArrayOf(0x88.toByte(), 0xB4.toByte()))
        } else if (hasSystemCode88B4 && !hasSystemCode12FC) {
            allSystemCodes.add(byteArrayOf(0x12.toByte(), 0xFC.toByte()))
        }


        // Create or update system contexts for all system codes
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        allSystemCodes.forEach { systemCode ->
            // Check if context already exists for this system code
            val existingContext =
                scanContext.systemScanContexts.find {
                    it.systemCode?.contentEquals(systemCode) == true
                }

            if (existingContext != null) {
                // Keep existing context
                updatedSystemContexts.add(existingContext)
            } else {
                // Create new context
                val newNodes = mutableListOf<Node>()

                // Add special service nodes for 12FC and 88B4 system codes
                if (
                    systemCode.contentEquals(byteArrayOf(0x12.toByte(), 0xFC.toByte())) ||
                        systemCode.contentEquals(byteArrayOf(0x88.toByte(), 0xB4.toByte()))
                ) {

                    // Add System node, Root Area and Service nodes 0x0009 and 0x000B
                    newNodes.add(System)
                    newNodes.add(Area.ROOT)
                    newNodes.add(Service.fromHexString("0900"))
                    newNodes.add(Service.fromHexString("0B00"))
                }

                val newContext =
                    SystemScanContext(systemCode = systemCode, nodes = newNodes, idm = target.idm)
                updatedSystemContexts.add(newContext)
            }
        }

        return updatedSystemContexts
    }

    private suspend fun executeInitialInfo(target: FeliCaTarget): String {
        // Use the PMM from the target (already obtained during creation)
        val pmm = target.pmm
        val idmHex = target.idm.toHexString()

        // Store card information in context
        scanContext = scanContext.copy(primaryIdm = target.idm, pmm = pmm)

        return buildString {
                appendLine("IDM: $idmHex")
                // Note: Manufacturer and NFC System Code information not available through
                // FeliCaTarget
                // interface
                // These would need to be obtained differently if needed
                appendLine()
                appendLine("PMM Information:")
                appendLine("  Raw PMM: ${pmm.toString()}")
                appendLine(
                    "  ROM Type: 0x${pmm.romType.toUByte().toString(16).uppercase().padStart(2, '0')}"
                )
                appendLine("  IC Type: ${IcTypeMapping.getFormattedIcType(pmm.icType)}")
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
            .trim()
    }

    private suspend fun executeRequestResponse(target: FeliCaTarget): String {
        val requestResponseCommand = RequestResponseCommand(target.idm)
        val requestResponseResponse = target.transceive(requestResponseCommand)

        val responseIdmHex = requestResponseResponse.idm.toHexString()
        val mode = requestResponseResponse.mode

        return buildString {
                appendLine("Card is present and responding")
                appendLine("Current Mode: ${mode.name} (${mode.value})")
            }
            .trim()
    }

    private suspend fun executeRequestSystemCode(target: FeliCaTarget): String {
        val requestSystemCodeCommand = RequestSystemCodeCommand(target.idm)
        val requestSystemCodeResponse = target.transceive(requestSystemCodeCommand)

        // Handle special system codes and ensure system contexts exist
        val updatedSystemContexts =
            handleDiscoveredSystemCodes(requestSystemCodeResponse.systemCodes, target)

        // Store discovered system codes in context and update system contexts
        scanContext =
            scanContext.copy(
                discoveredSystemCodes = requestSystemCodeResponse.systemCodes,
                systemScanContexts = updatedSystemContexts,
            )

        // Calculate which codes were added for display
        val allSystemCodes = updatedSystemContexts.map { it.systemCode }.filterNotNull()
        val addedCodes =
            allSystemCodes.filter { additionalCode ->
                !requestSystemCodeResponse.systemCodes.any { it.contentEquals(additionalCode) }
            }

        return if (requestSystemCodeResponse.systemCodes.isNotEmpty()) {
            buildString {
                    appendLine(
                        "Discovered System Codes (${requestSystemCodeResponse.systemCodes.size}):"
                    )
                    requestSystemCodeResponse.systemCodes.forEachIndexed { index, systemCode ->
                        val systemCodeHex = systemCode.toHexString().uppercase()
                        appendLine("  ${index + 1}. $systemCodeHex")
                    }

                    // Add information about added system codes
                    if (addedCodes.isNotEmpty()) {
                        appendLine()
                        appendLine("Additional System Codes (inferred):")
                        addedCodes.forEachIndexed { index, systemCode ->
                            val systemCodeHex = systemCode.toHexString().uppercase()
                            appendLine("  ${index + 1}. $systemCodeHex")
                        }
                    }
                }
                .trim()
        } else {
            "No system codes discovered"
        }
    }

    private suspend fun executeRequestSpecificationVersion(target: FeliCaTarget): String {
        val requestSpecVersionCommand = RequestSpecificationVersionCommand(target.idm)
        val requestSpecVersionResponse = target.transceive(requestSpecVersionCommand)

        // Store specification version in context
        scanContext = scanContext.copy(specificationVersion = requestSpecVersionResponse)

        return buildString {
                appendLine("Specification Version Information:")
                appendLine(
                    "Status Flags: 0x${requestSpecVersionResponse.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')} 0x${requestSpecVersionResponse.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                )

                if (requestSpecVersionResponse.isStatusSuccessful) {
                    appendLine(
                        "Format Version: 0x${requestSpecVersionResponse.formatVersion?.toUByte()?.toString(16)?.uppercase()?.padStart(2, '0') ?: "N/A"}"
                    )
                    appendLine()

                    requestSpecVersionResponse.basicVersion?.let { basicVersion ->
                        appendLine("Basic Version: ${basicVersion.major}.${basicVersion.minor}")
                    }

                    requestSpecVersionResponse.desOptionVersion?.let { desVersion ->
                        appendLine("DES Option Version: ${desVersion.major}.${desVersion.minor}")
                    }

                    requestSpecVersionResponse.specialOptionVersion?.let { specialVersion ->
                        appendLine(
                            "Special Option Version: ${specialVersion.major}.${specialVersion.minor}"
                        )
                    }

                    requestSpecVersionResponse.extendedOverlapOptionVersion?.let {
                        extendedOverlapVersion ->
                        appendLine(
                            "Extended Overlap Option Version: ${extendedOverlapVersion.major}.${extendedOverlapVersion.minor}"
                        )
                    }

                    requestSpecVersionResponse.valueLimitedPurseServiceOptionVersion?.let {
                        valueLimitedPurseVersion ->
                        appendLine(
                            "Value-Limited Purse Service Option Version: ${valueLimitedPurseVersion.major}.${valueLimitedPurseVersion.minor}"
                        )
                    }

                    requestSpecVersionResponse.communicationWithMacOptionVersion?.let {
                        communicationWithMacVersion ->
                        appendLine(
                            "Communication with MAC Option Version: ${communicationWithMacVersion.major}.${communicationWithMacVersion.minor}"
                        )
                    }
                } else {
                    appendLine("Failed to retrieve specification version information")
                }
            }
            .trim()
    }

    private suspend fun executeGetSystemStatus(target: FeliCaTarget): String {
        if (scanContext.systemScanContexts.isEmpty()) {
            throw RuntimeException("No systems have been discovered. Please run system discovery first.")
        }

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
                        "  Status Flags: 0x${getSystemStatusResponse.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')} 0x${getSystemStatusResponse.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                    )
                    appendLine(
                        "  Flag: 0x${getSystemStatusResponse.flag.toUByte().toString(16).uppercase().padStart(2, '0')}"
                    )

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
                results.add("System ${contextIndex + 1} ($systemCodeHex): Failed to get system status - ${e.message}")
            }
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        if (errors > 0) {
            throw RuntimeException(
                "Get System Status encountered $errors error(s)"
            )
        }

        return buildString {
                appendLine("System Status Information:")
                appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                appendLine()
                results.forEach { result ->
                    appendLine(result)
                }
            }
            .trim()
    }

    private fun getModeDescription(mode: CardMode): String {
        return when (mode) {
            CardMode.INITIAL -> "Card is in initial state"
            CardMode.AUTHENTICATION_PENDING -> "Authentication required"
            CardMode.AUTHENTICATED -> "Card is authenticated"
            CardMode.ISSUANCE -> "Card is in issuance mode"
        }
    }

    private suspend fun executePollingSystemCode(target: FeliCaTarget): String {
        val systemCodeCommand = PollingCommand(requestCode = RequestCode.SYSTEM_CODE_REQUEST)
        val parsedSystemCodeResponse = target.transceive(systemCodeCommand)

        // Store system code in context
        if (parsedSystemCodeResponse.hasRequestData) {
            scanContext = scanContext.copy(primarySystemCode = parsedSystemCodeResponse.systemCode)

            // Handle special system codes and ensure system contexts exist
            val updatedSystemContexts =
                handleDiscoveredSystemCodes(
                    listOf(parsedSystemCodeResponse.systemCode),
                    target,
                )

            // Update scan context with the new system contexts
            scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)
        } else {
            // Update existing system context or create a placeholder one (fallback for legacy code)
            if (scanContext.systemScanContexts.isNotEmpty()) {
                val updatedSystemContext =
                    scanContext.systemScanContexts
                        .first()
                        .copy(systemCode = parsedSystemCodeResponse.systemCode)
                scanContext = scanContext.copy(systemScanContexts = listOf(updatedSystemContext))
            } else {
                // Create a basic system context if none exists yet
                val systemContext =
                    SystemScanContext(
                        systemCode = parsedSystemCodeResponse.systemCode,
                        idm = target.idm,
                    )
                scanContext = scanContext.copy(systemScanContexts = listOf(systemContext))
            }
        }

        return if (parsedSystemCodeResponse.hasRequestData) {
            val systemCodeHex = parsedSystemCodeResponse.systemCode.toHexString().uppercase()
            "System Code: $systemCodeHex"
        } else {
            "System Code: Not available"
        }
    }

    private suspend fun executePollingCommunicationPerformance(target: FeliCaTarget): String {
        val commPerfCommand =
            PollingCommand(requestCode = RequestCode.COMMUNICATION_PERFORMANCE_REQUEST)
        val parsedCommPerfResponse = target.transceive(commPerfCommand)

        // Store communication performance in context
        if (parsedCommPerfResponse.hasRequestData) {
            scanContext =
                scanContext.copy(
                    communicationPerformance = parsedCommPerfResponse.communicationPerformance
                )
        }

        return if (parsedCommPerfResponse.hasRequestData) {
            val commPerf = parsedCommPerfResponse.communicationPerformance
            buildString {
                    appendLine("212 kbps: ${if (commPerf.supports212kbps) "✓" else "✗"}")
                    appendLine("424 kbps: ${if (commPerf.supports424kbps) "✓" else "✗"}")
                    appendLine(
                        "848 kbps: ${if (commPerf.supports848kbps) "✓" else "✗"} (reserved)"
                    )
                    appendLine(
                        "1696 kbps: ${if (commPerf.supports1696kbps) "✓" else "✗"} (reserved)"
                    )
                    appendLine(
                        "Auto Detection: ${if (commPerf.isAutomaticDetectionCompliant) "✓" else "✗"}"
                    )
                    appendLine("Highest Rate: ${commPerf.getHighestSupportedRate()}")
                }
                .trim()
        } else {
            "Communication Performance: Not available"
        }
    }

    private suspend fun executeRequestCodeList(target: FeliCaTarget): String {
        val results = mutableListOf<String>()
        val allAreas = mutableListOf<Area>()
        val allServices = mutableListOf<Service>()

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val contextAreas = mutableListOf<Area>()
            val contextServices = mutableListOf<Service>()

            // Use AREA ROOT as the parent to get all codes for this system
            val parentNode = Area.ROOT
            // 0e 0e 01405337eb37b66000000100

            // Iterate through request code list commands from 0 to MAX_ITERATOR_INDEX
            for (index in 1..RequestCodeListCommand.MAX_ITERATOR_INDEX) {
                val requestCodeListCommand = RequestCodeListCommand(target.idm, parentNode, index)
                val requestCodeListResponse = target.transceive(requestCodeListCommand)

                // Check for error response (non-zero status flags indicate error)
                if (!requestCodeListResponse.isStatusSuccessful) {
                    Log.d(
                        "CardScanService",
                        "RequestCodeList error at index $index for system ${systemContext.systemCode?.toHexString()}: status1=0x${requestCodeListResponse.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')}, status2=0x${requestCodeListResponse.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}",
                    )
                    break
                }

                contextAreas.addAll(requestCodeListResponse.areas)
                contextServices.addAll(requestCodeListResponse.services)

                // Stop iteration if continueFlag is false
                if (!requestCodeListResponse.continueFlag) {
                    Log.d(
                        "CardScanService",
                        "RequestCodeList completed at index $index for system ${systemContext.systemCode?.toHexString()}, continueFlag=false",
                    )
                    break
                }
            }

            allAreas.addAll(contextAreas)
            allServices.addAll(contextServices)

            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"
            results.add(
                buildString {
                    appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                    appendLine()

                    if (contextAreas.isNotEmpty()) {
                        appendLine("  Areas (${contextAreas.size}):")
                        contextAreas.forEach { area ->
                            appendLine(
                                "  - Area ${area.code.toHexString()}: Range ${area.number}-${area.endNumber}"
                            )
                        }
                    } else {
                        appendLine("  Areas: None found")
                    }

                    appendLine()

                    if (contextServices.isNotEmpty()) {
                        appendLine("  Services (${contextServices.size}):")
                        contextServices.forEach { service ->
                            appendLine(
                                "  - Service ${service.code.toHexString()}: ${service.attribute.name}"
                            )
                        }
                    } else {
                        appendLine("  Services: None found")
                    }
                }
            )
        }

        return buildString {
                appendLine("Request Code List Results:")
                appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                appendLine()

                results.forEach { result ->
                    appendLine(result)
                    appendLine()
                }

                appendLine("Total areas: ${allAreas.size}, Total services: ${allServices.size}")
            }
            .trim()
    }

    private suspend fun executeSearchServiceCode(target: FeliCaTarget): Pair<String, String> {
        val allDiscoveredNodes = mutableListOf<Node>()
        val systemContextsToUpdate = mutableListOf<SystemScanContext>()

        // Process each system context separately
        for (systemContext in scanContext.systemScanContexts) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val nodeArray = mutableListOf<Node>()

            // Iterate through service codes from 0x0000 to 0xFFFF
            for (index in 0x0000..SearchServiceCodeCommand.MAX_ITERATOR_INDEX) {
                val searchServiceCodeCommand = SearchServiceCodeCommand(target.idm, index)
                val parsedSearchResponse = target.transceive(searchServiceCodeCommand)

                val node = parsedSearchResponse.node
                if (node != null) {
                    nodeArray.add(node)

                    // Stop iteration if we found a system node
                    if (node is System) {
                        Log.d(
                            "CardScanService",
                            "Found system node at index $index for system ${systemContext.systemCode?.toHexString()}, stopping iteration",
                        )
                        break
                    }
                } else {
                    // No more nodes found, stop iteration
                    Log.d(
                        "CardScanService",
                        "No node found at index $index for system ${systemContext.systemCode?.toHexString()}, stopping iteration",
                    )
                    break
                }
            }

            allDiscoveredNodes.addAll(nodeArray)

            // Update system context with discovered nodes
            val updatedSystemContext = systemContext.copy(nodes = nodeArray)
            systemContextsToUpdate.add(updatedSystemContext)
        }

        // Handle case where no system contexts exist yet (fallback for legacy compatibility)
        if (scanContext.systemScanContexts.isEmpty()) {
            val nodeArray = mutableListOf<Node>()

            // Iterate through service codes from 0x0000 to 0xFFFF
            for (index in 0x0000..SearchServiceCodeCommand.MAX_ITERATOR_INDEX) {
                val searchServiceCodeCommand = SearchServiceCodeCommand(target.idm, index)
                val parsedSearchResponse = target.transceive(searchServiceCodeCommand)

                val node = parsedSearchResponse.node
                if (node != null) {
                    nodeArray.add(node)

                    // Stop iteration if we found a system node
                    if (node is System) {
                        Log.d(
                            "CardScanService",
                            "Found system node at index $index, stopping iteration",
                        )
                        break
                    }
                } else {
                    // No more nodes found, stop iteration
                    Log.d("CardScanService", "No node found at index $index, stopping iteration")
                    break
                }
            }

            allDiscoveredNodes.addAll(nodeArray)

            // Create new system context
            val systemContext =
                SystemScanContext(
                    systemCode = scanContext.primarySystemCode,
                    nodes = nodeArray,
                    idm = target.idm,
                )
            systemContextsToUpdate.add(systemContext)
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = systemContextsToUpdate)

        // Calculate statistics across all discovered nodes
        val areas = allDiscoveredNodes.filterIsInstance<Area>()
        val services = allDiscoveredNodes.filterIsInstance<Service>()
        val systems = allDiscoveredNodes.filterIsInstance<System>()

        return if (allDiscoveredNodes.isNotEmpty()) {
            // Collapsed view - just summary
            val collapsedResult =
                "Found ${areas.size} areas, ${services.size} services across ${systemContextsToUpdate.size} system(s)"

            // Expanded view - detailed list
            val expandedResult =
                buildString {
                        appendLine(
                            "Discovered Nodes across ${systemContextsToUpdate.size} system(s):"
                        )

                        // Show breakdown by system context
                        systemContextsToUpdate.forEachIndexed { index, context ->
                            val contextAreas = context.nodes.filterIsInstance<Area>()
                            val contextServices = context.nodes.filterIsInstance<Service>()
                            val contextSystems = context.nodes.filterIsInstance<System>()

                            appendLine(
                                "System Context ${index + 1} (${context.systemCode?.toHexString() ?: "unknown"}):"
                            )
                            appendLine(" Areas (${contextAreas.size}):")
                            contextAreas.forEach { area ->
                                appendLine(
                                    "  - Area ${area.code.toHexString()}: Range ${area.number}-${area.endNumber}"
                                )
                            }
                            if (contextAreas.isEmpty()) {
                                appendLine("    - None")
                            }

                            appendLine(" Services (${contextServices.size}):")
                            contextServices.forEach { service ->
                                appendLine(
                                    "  - Service ${service.code.toHexString()}: ${service.attribute.name}"
                                )
                            }
                            if (contextServices.isEmpty()) {
                                appendLine("    - None")
                            }

                            appendLine("  Systems (${contextSystems.size}):")
                            contextSystems.forEach { system ->
                                appendLine("    - System ${system.code.toHexString()}")
                            }
                            if (contextSystems.isEmpty()) {
                                appendLine("    - None")
                            }
                            appendLine()
                        }

                        appendLine("Total Summary:")
                        appendLine(
                            "Areas: ${areas.size}, Services: ${services.size}, Systems: ${systems.size}"
                        )
                    }
                    .trim()

            collapsedResult to expandedResult
        } else {
            val noResultsMessage = "Available Services: None found"
            noResultsMessage to noResultsMessage
        }
    }

    private suspend fun executeRequestService(target: FeliCaTarget): Pair<String, String> {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val areas = allDiscoveredNodes.filterIsInstance<Area>()
        val services = allDiscoveredNodes.filterIsInstance<Service>()
        val systems = allDiscoveredNodes.filterIsInstance<System>()

        // Check if no areas are known - consider this a failure
        if (allDiscoveredNodes.isEmpty()) {
            throw RuntimeException(
                "No areas found. Request service key versions require at least one area to be discovered."
            )
        }

        // Request key versions in batches (max 32 nodes per request)
        val maxNodesPerRequest = 32
        val keyVersionResults = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // Process each system context separately
        for (systemContext in scanContext.systemScanContexts) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val systemNodes = systemContext.nodes
            val nodeKeyVersionsMap = mutableMapOf<Node, KeyVersion>()

            if (systemNodes.isEmpty()) {
                continue
            }
            systemNodes.chunked(maxNodesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
                val requestServiceCommand = RequestServiceCommand(target.idm, nodeCodes)
                val requestServiceResponse = target.transceive(requestServiceCommand)

                // Collect key version results for this batch
                nodeBatch.forEachIndexed { index, node ->
                    val keyVersion = requestServiceResponse.keyVersions[index]
                    val exists = !requestServiceResponse.keyVersions[index].isMissing
                    val nodeType =
                        when (node) {
                            is Area -> "Area"
                            is Service -> "Service"
                            is System -> "System"
                            else -> "Unknown"
                        }
                    val status = if (exists) "Key Version: ${keyVersion.toInt()}" else "Not found"
                    keyVersionResults.add(
                        "${systemContext.systemCode?.toHexString() ?: "unknown"} - $nodeType ${node.code.toHexString()}: $status"
                    )

                    // Store key version in map
                    if (exists) {
                        nodeKeyVersionsMap[node] = keyVersion
                    }
                }
            }
            // Update context with key version data
            val updatedSystemContext = systemContext.copy(nodeKeyVersions = nodeKeyVersionsMap)
            updatedSystemContexts.add(updatedSystemContext)
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        // Return summary with key version details
        val collapsedSummary =
            "Requested key versions for ${areas.size} areas, ${services.size} services across ${updatedSystemContexts.size} system(s)"
        val expandedResult =
            if (keyVersionResults.isNotEmpty()) {
                collapsedSummary + "\n" + keyVersionResults.joinToString("\n")
            } else {
                collapsedSummary + " (no details available)"
            }

        return collapsedSummary to expandedResult
    }

    private suspend fun executeRequestServiceV2(target: FeliCaTarget): Pair<String, String> {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val areas = allDiscoveredNodes.filterIsInstance<Area>()
        val services = allDiscoveredNodes.filterIsInstance<Service>()
        val systems = allDiscoveredNodes.filterIsInstance<System>()

        // Check if no areas are known - consider this a failure
        if (allDiscoveredNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes found. Request service key versions require at least one area to be discovered."
            )
        }

        // Request key versions in batches (max 32 nodes per request)
        val maxNodesPerRequest = 32
        val keyVersionResults = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var globalEncryptionId: EncryptionIdentifier? = null

        // Process each system context separately
        for (systemContext in scanContext.systemScanContexts) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val nodes = systemContext.nodes
            val nodeAesKeyVersionsMap = mutableMapOf<Node, KeyVersion>()
            val nodeDesKeyVersionsMap = mutableMapOf<Node, KeyVersion>()
            var encryptionId: EncryptionIdentifier? = null

            if (nodes.isEmpty()) {
                continue
            }
            nodes.chunked(maxNodesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
                val requestServiceV2Command = RequestServiceV2Command(target.idm, nodeCodes)
                val requestServiceV2Response = target.transceive(requestServiceV2Command)

                if (requestServiceV2Response.isStatusSuccessful) {
                    // Store encryption identifier from first successful response
                    if (encryptionId == null) {
                        encryptionId = requestServiceV2Response.encryptionIdentifier
                        if (globalEncryptionId == null) {
                            globalEncryptionId = encryptionId
                        }
                    }

                    // Collect key version results for this batch
                    nodeBatch.forEachIndexed { index, node ->
                        val aesKeyVersion =
                            requestServiceV2Response.aesKeyVersions[index]
                                ?: throw RuntimeException(
                                    "AES key version missing for node at index $index"
                                )
                        val desKeyVersion =
                            requestServiceV2Response.desKeyVersions[index]
                                ?: throw RuntimeException(
                                    "DES key version missing for node at index $index"
                                )
                        val aesExists = !requestServiceV2Response.aesKeyVersions[index].isMissing
                        val desExists = !requestServiceV2Response.desKeyVersions[index].isMissing

                        val nodeType =
                            when (node) {
                                is Area -> "Area"
                                is Service -> "Service"
                                is System -> "System"
                                else -> "Unknown"
                            }

                        val nodeCodeHex = node.code.toHexString()
                        val encryptionInfo =
                            requestServiceV2Response.encryptionIdentifier?.let { encId ->
                                " (${encId.name})"
                            } ?: ""

                        val aesStatus =
                            if (aesExists) "AES: ${aesKeyVersion.toInt()}" else "AES: N/A"
                        val desStatus =
                            if (desExists) {
                                "DES: ${desKeyVersion.toInt()}"
                            } else "DES: Not supported"

                        keyVersionResults.add(
                            "${systemContext.systemCode?.toHexString() ?: "unknown"} - $nodeType $nodeCodeHex$encryptionInfo: $aesStatus, $desStatus"
                        )

                        // Store key versions in maps
                        if (aesExists) {
                            nodeAesKeyVersionsMap[node] = aesKeyVersion
                        }
                        // Only store DES key version if it exists and the node supports DES
                        if (desExists) {
                            nodeDesKeyVersionsMap[node] = desKeyVersion
                        }
                    }
                } else {
                    keyVersionResults.add(
                        "${systemContext.systemCode?.toHexString() ?: "unknown"} - Batch ${batchIndex + 1}: Error - Status: ${requestServiceV2Response.statusFlag1.toInt() and 0xFF}"
                    )
                }
            }

            // Update context with key version data
            val updatedSystemContext =
                systemContext.copy(
                    nodeAesKeyVersions = nodeAesKeyVersionsMap,
                    nodeDesKeyVersions = nodeDesKeyVersionsMap,
                    encryptionIdentifier = encryptionId,
                )
            updatedSystemContexts.add(updatedSystemContext)
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        // Return summary with key version details
        val collapsedSummary =
            "Requested enhanced key versions for ${areas.size} areas, ${services.size} services across ${updatedSystemContexts.size} system(s)"
        val expandedResult =
            if (keyVersionResults.isNotEmpty()) {
                collapsedSummary + "\n" + keyVersionResults.joinToString("\n")
            } else {
                collapsedSummary + " (no details available)"
            }

        return collapsedSummary to expandedResult
    }

    private suspend fun executeSetParameter(target: FeliCaTarget): String {
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
                "Set Parameter command failed with status1=0x${setParameterResponse1.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')}, status2=0x${setParameterResponse1.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
            )
        }

        results.add(
            buildString {
                appendLine("Set Parameter Test 1 (SRM_TYPE1, NODECODESIZE_2):")
                appendLine("  Response IDM: ${setParameterResponse1.idm.toHexString()}")
                appendLine(
                    "  Status: 0x${setParameterResponse1.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')} 0x${setParameterResponse1.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                )
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
                    "Set Parameter Test 2 failed with status1=0x${setParameterResponse2.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')}, status2=0x${setParameterResponse2.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                )
            }

            results.add(
                buildString {
                    appendLine("Set Parameter Test 2 (SRM_TYPE2, NODECODESIZE_4):")
                    appendLine("  Response IDM: ${setParameterResponse2.idm.toHexString()}")
                    appendLine(
                        "  Status: 0x${setParameterResponse2.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')} 0x${setParameterResponse2.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                    )
                    appendLine("  Result: SUCCESS")
                }
            )
        } catch (e: Exception) {
            throw RuntimeException(
                "Set Parameter Test 2 (SRM_TYPE2, NODECODESIZE_4) failed: ${e.message}"
            )
        }

        return buildString {
                appendLine("Set Parameter Command Tests:")
                appendLine()
                results.forEach { result ->
                    appendLine(result)
                    appendLine()
                }
            }
            .trim()
    }

    private suspend fun executeGetContainerIssueInformation(target: FeliCaTarget): String {
        val getContainerIssueInformationCommand = GetContainerIssueInformationCommand(target.idm)
        val getContainerIssueInformationResponse =
            target.transceive(getContainerIssueInformationCommand)
        val containerInformation = getContainerIssueInformationResponse.containerInformation

        // Store container issue information in context
        scanContext = scanContext.copy(containerIssueInformation = containerInformation)

        val formatVersionHex = containerInformation.formatVersionCarrierInformation.toHexString()
        val modelInfoHex = containerInformation.mobilePhoneModelInformation.toHexString()

        // Try to decode mobile phone model as printable string
        val modelString =
            try {
                val printableBytes =
                    containerInformation.mobilePhoneModelInformation.filter { it in 32..126 }
                if (printableBytes.size >= 3) { // At least 3 printable characters
                    String(printableBytes.toByteArray(), Charsets.UTF_8)
                } else {
                    modelInfoHex
                }
            } catch (e: Exception) {
                modelInfoHex
            }

        return buildString {
                appendLine("Format Version & Carrier Info: $formatVersionHex")
                appendLine("Mobile Phone Model: $modelString")
            }
            .trim()
    }

    private suspend fun executeGetSecureElementInformation(target: FeliCaTarget): String {
        val getSecureElementInformationCommand = GetSecureElementInformationCommand(target.idm)
        val getSecureElementInformationResponse =
            target.transceive(getSecureElementInformationCommand)

        // Store secure element information in context
        scanContext =
            scanContext.copy(secureElementInformation = getSecureElementInformationResponse)

        return buildString {
                appendLine(
                    "Status Flags: 0x${getSecureElementInformationResponse.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')} 0x${getSecureElementInformationResponse.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                )

                if (getSecureElementInformationResponse.isStatusSuccessful) {
                    appendLine(
                        "Secure Element Data: ${getSecureElementInformationResponse.secureElementData.toHexString()}"
                    )
                } else {
                    appendLine("Failed to retrieve secure element information")
                }
            }
            .trim()
    }

    private suspend fun executeResetMode(target: FeliCaTarget): String {
        val resetModeCommand = ResetModeCommand(target.idm)
        val resetModeResponse = target.transceive(resetModeCommand)

        return buildString {
                appendLine(
                    "Status Flags: 0x${resetModeResponse.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')} 0x${resetModeResponse.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                )

                // appendLine("Note: Reset Mode command resets the card's mode to Mode0.")
                // appendLine("This command is supported by AES and AES/DES cards.")
            }
            .trim()
    }

    private suspend fun executeGetContainerId(target: FeliCaTarget): String {
        val getContainerIdCommand = GetContainerIdCommand()
        val getContainerIdResponse = target.transceive(getContainerIdCommand)

        // Store container IDM in context
        scanContext = scanContext.copy(containerIdm = getContainerIdResponse.containerIdm)

        return buildString {
                appendLine("Container IDM: ${getContainerIdResponse.containerIdm.toHexString()}")
            }
            .trim()
    }

    private suspend fun executeEcho(target: FeliCaTarget): String {
        val echoCommand = EchoCommand("H".repeat(72).encodeToByteArray())
        val echoResponse = target.transceive(echoCommand)

        return buildString {
                appendLine("Sent Data: ${echoCommand.data.toHexString()}")
                appendLine("Received Data: ${echoResponse.data.toHexString()}")
                appendLine("Data Match: ${echoCommand.data.contentEquals(echoResponse.data)}")
                appendLine()
            }
            .trim()
    }

    private suspend fun executeReadWithoutEncryptionDetermineErrorIndication(
        target: FeliCaTarget
    ): String {
        // Find the system with at least one readable service
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val allServices = allDiscoveredNodes.filterIsInstance<Service>()

        if (allServices.isEmpty()) {
            throw RuntimeException("No services available for determining error indication")
        }

        // Filter services that don't require authentication
        val allServicesWithoutAuth = allServices.filter { !it.attribute.authenticationRequired }

        if (allServicesWithoutAuth.isEmpty()) {
            throw RuntimeException(
                "No services found that don't require authentication for determining error indication"
            )
        }

        // Find the system with the most services that don't require authentication
        val bestSystemContext =
            scanContext.systemScanContexts.maxByOrNull { systemContext ->
                val services = systemContext.nodes.filterIsInstance<Service>()
                // Count services that don't require authentication (potentially readable)
                services.count { service -> !service.attribute.authenticationRequired }
            }

        if (bestSystemContext == null) {
            throw RuntimeException("No system context found with readable services")
        }

        // Switch to the best system for testing
        pollSystemCode(target, bestSystemContext.systemCode)

        val services = bestSystemContext.nodes.filterIsInstance<Service>()
        val servicesWithoutAuth = services.filter { !it.attribute.authenticationRequired }

        if (servicesWithoutAuth.isEmpty()) {
            throw RuntimeException("No readable services found in the selected system")
        }

        // Prefer the service with attribute name 'RANDOM' if available
        val testService =
            servicesWithoutAuth.firstOrNull { it.attribute.type == ServiceType.RANDOM }
                ?: servicesWithoutAuth.last()

        val blocksToRead =
            listOf(
                BlockListElement(serviceCodeListOrder = 0, blockNumber = 0),
                BlockListElement(serviceCodeListOrder = 0, blockNumber = 0),
                // Third element should be out of range to trigger error indication, with 0x03 for
                // NUMERIC and 0x04 for BITMASK
                BlockListElement(serviceCodeListOrder = 0, blockNumber = 127),
            )

        val readCommand =
            ReadWithoutEncryptionCommand(
                idm = target.idm,
                serviceCodes = arrayOf(testService.code),
                blockListElements = blocksToRead.toTypedArray(),
            )

        val response = target.transceive(readCommand)
        val statusFlag1 = response.statusFlag1
        val statusFlag2 = response.statusFlag2

        if (response.isStatusSuccessful) {
            throw RuntimeException(
                "ReadWithoutEncryption failed to determine error indication, status1=0x${statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')}, status2=0x${statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
            )
        }

        // Analyze response status to determine error indication type
        val errorIndicationType =
            when {
                statusFlag1.toInt() and 0xFF == 0xFF -> {
                    Log.d("CardScanService", "Determined FLAG error indication (status1=0xFF)")
                    ErrorLocationIndication.FLAG
                }
                statusFlag1.toInt() and 0xFF == 0x04 -> {
                    Log.d("CardScanService", "Determined BITMASK error indication (status1=0x03)")
                    ErrorLocationIndication.BITMASK
                }
                statusFlag1.toInt() and 0xFF == 0x03 -> {
                    Log.d("CardScanService", "Determined NUMBER error indication (status1=0x01)")
                    ErrorLocationIndication.INDEX
                }
                else -> {
                    throw RuntimeException(
                        "Unexpected response status for error indication determination: status1=0x${statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')}, status2=0x${statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                    )
                }
            }

        // Update scan context with determined error indication type
        scanContext =
            scanContext.copy(
                errorLocationIndication = errorIndicationType,
                readBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED,
            )

        Log.d("CardScanService", "Determined error indication type: $errorIndicationType")

        return buildString {
                appendLine(
                    "Error indication type: ${errorIndicationType.name} 0x${statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')} 0x${statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                )
            }
            .trim()
    }

    private suspend fun executeReadWithoutEncryptionDetermineMaxServices(
        target: FeliCaTarget
    ): String {
        // Find services that don't require authentication across all system contexts
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val allServices = allDiscoveredNodes.filterIsInstance<Service>()
        val allServicesWithoutAuth = allServices.filter { !it.attribute.authenticationRequired }

        if (allServicesWithoutAuth.isEmpty()) {
            throw RuntimeException(
                "No services available that don't require authentication for determining max services"
            )
        }

        // Find the system with the most readable services
        val bestSystemContext =
            scanContext.systemScanContexts.maxByOrNull { systemContext ->
                val services = systemContext.nodes.filterIsInstance<Service>()
                services.count { service -> !service.attribute.authenticationRequired }
            }

        if (bestSystemContext == null) {
            throw RuntimeException("No system context found with readable services")
        }

        // Switch to the best system for testing
        pollSystemCode(target, bestSystemContext.systemCode)

        val services = bestSystemContext.nodes.filterIsInstance<Service>()
        val servicesWithoutAuth = services.filter { !it.attribute.authenticationRequired }

        if (servicesWithoutAuth.isEmpty()) {
            throw RuntimeException("No readable services found in the selected system")
        }

        // Prefer the service with attribute name 'RANDOM' if available
        val testService =
            servicesWithoutAuth.firstOrNull { it.attribute.type == ServiceType.RANDOM }
                ?: servicesWithoutAuth.first()

        // Start with theoretical maximum and work down
        var maxServices =
            ReadWithoutEncryptionCommand
                .MAX_SERVICE_CODES // FeliCa specification limit for service codes

        while (maxServices > 0) {
            // Create array of the same service code repeated maxServices times
            val serviceCodes = Array(maxServices) { testService.code }
            // Create block list elements for block 0, one for each service
            val blockListElements =
                Array(maxServices) { serviceIndex ->
                    BlockListElement(serviceCodeListOrder = serviceIndex, blockNumber = 0)
                }

            // Create the read command with the repeated service and corresponding block elements
            val readCommand =
                ReadWithoutEncryptionCommand(
                    idm = target.idm,
                    serviceCodes = serviceCodes,
                    blockListElements = blockListElements,
                )

            val response = target.transceive(readCommand)
            if (response.isStatusSuccessful) {
                // Command succeeded, we found the maximum
                Log.d(
                    "CardScanService",
                    "ReadWithoutEncryption succeeded with $maxServices services",
                )
                break
            }
            if (response.statusFlag2.toByte() != 0xA1.toByte()) {
                throw RuntimeException(
                    "ReadWithoutEncryption failed with unexpected error (not 0xA1) at $maxServices services, status1=0x${response.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')}, status2=0x${response.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                )
            }
            Log.d(
                "CardScanService",
                "ReadWithoutEncryption failed with $maxServices services, status1=0x${response.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')}, status2=0x${response.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}",
            )
            maxServices--
        }

        if (maxServices == 0) {
            throw RuntimeException(
                "Unable to determine maximum services per request - even 1 service failed"
            )
        }

        // Update scan context with the determined maximum
        scanContext =
            scanContext.copy(
                maxServicesPerRequest = maxServices,
                readBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED,
            )

        return buildString { appendLine("Maximum services per request: $maxServices") }.trim()
    }

    private suspend fun executeReadWithoutEncryptionDetermineMaxBlocks(
        target: FeliCaTarget
    ): String {
        // Find services that don't require authentication across all system contexts
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val allServices = allDiscoveredNodes.filterIsInstance<Service>()
        val allServicesWithoutAuth = allServices.filter { !it.attribute.authenticationRequired }

        if (allServicesWithoutAuth.isEmpty()) {
            throw RuntimeException(
                "No services available that don't require authentication for determining max blocks"
            )
        }

        // Find the system with the most readable services
        val bestSystemContext =
            scanContext.systemScanContexts.maxByOrNull { systemContext ->
                val services = systemContext.nodes.filterIsInstance<Service>()
                services.count { service -> !service.attribute.authenticationRequired }
            }

        if (bestSystemContext == null) {
            throw RuntimeException("No system context found with readable services")
        }

        // Switch to the best system for testing
        pollSystemCode(target, bestSystemContext.systemCode)

        val services = bestSystemContext.nodes.filterIsInstance<Service>()
        val servicesWithoutAuth = services.filter { !it.attribute.authenticationRequired }

        if (servicesWithoutAuth.isEmpty()) {
            throw RuntimeException("No readable services found in the selected system")
        }

        // Prefer the service with attribute name 'RANDOM' if available
        val testService =
            servicesWithoutAuth.firstOrNull { it.attribute.type == ServiceType.RANDOM }
                ?: servicesWithoutAuth.first()

        // Start with theoretical maximum and work down
        var maxBlocks =
            ReadWithoutEncryptionCommand.MAX_BLOCKS // FeliCa specification limit for blocks

        while (maxBlocks > 0) {
            // Create block list elements for blocks 0 through (maxBlocks-1)
            val blockListElements =
                Array(maxBlocks) { blockIndex ->
                    BlockListElement(serviceCodeListOrder = 0, blockNumber = 0)
                }

            // Create the read command with single service and multiple blocks
            val readCommand =
                ReadWithoutEncryptionCommand(
                    idm = target.idm,
                    serviceCodes = arrayOf(testService.code),
                    blockListElements = blockListElements,
                )

            val response = target.transceive(readCommand)
            if (response.isStatusSuccessful) {
                // Command succeeded, we found the maximum
                Log.d("CardScanService", "ReadWithoutEncryption succeeded with $maxBlocks blocks")
                break
            }
            if (
                response.statusFlag2.toByte() != 0xA2.toByte() &&
                    response.statusFlag2.toByte() != 0xA8.toByte()
            ) {
                throw RuntimeException(
                    "ReadWithoutEncryption failed with unexpected error (not 0xA2 or 0xA8) at $maxBlocks blocks, status1=0x${response.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')}, status2=0x${response.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}"
                )
            }
            Log.d(
                "CardScanService",
                "ReadWithoutEncryption failed with $maxBlocks blocks, status1=0x${response.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')}, status2=0x${response.statusFlag2.toUByte().toString(16).uppercase().padStart(2, '0')}",
            )
            maxBlocks--
        }

        if (maxBlocks == 0) {
            throw RuntimeException(
                "Unable to determine maximum blocks per request - even 1 block failed"
            )
        }

        // Update scan context with the determined maximum
        scanContext =
            scanContext.copy(
                maxBlocksPerRequest = maxBlocks,
                readBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED,
            )

        return buildString { appendLine("Maximum blocks per request: $maxBlocks") }.trim()
    }

    private suspend fun executeReadBlocksWithoutEncryption(
        target: FeliCaTarget
    ): Pair<String, String> {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val allServices = allDiscoveredNodes.filterIsInstance<Service>()

        if (allServices.isEmpty()) {
            throw RuntimeException("No services available for block reading")
        }

        // Filter services that don't require authentication across all system contexts
        val allServicesWithoutAuth = allServices.filter { !it.attribute.authenticationRequired }

        if (allServicesWithoutAuth.isEmpty()) {
            val noAuthServicesMessage = "No services found that don't require authentication"
            return noAuthServicesMessage to noAuthServicesMessage
        }

        // Process each system context separately
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        val contextResults = mutableListOf<String>()
        var totalBlocksRead = 0
        var totalServicesProcessed = 0
        var maxBlocksPerRequest = 0
        var maxServicesPerRequest = 0

        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val services = systemContext.nodes.filterIsInstance<Service>()
            val servicesWithoutAuth = services.filter { !it.attribute.authenticationRequired }
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (servicesWithoutAuth.isEmpty()) {
                contextResults.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No services without authentication found"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            // Use the utility function to read blocks with appropriate error indication mode
            val blockReader =
                BlockReader(
                    target = target,
                    errorLocationIndication = scanContext.errorLocationIndication,
                    maxBlocksPerRequest = scanContext.maxBlocksPerRequest ?: 15,
                    maxServicesPerRequest = scanContext.maxServicesPerRequest ?: 16,
                )
            val blockReadResult = blockReader.readBlocksFromServices(servicesWithoutAuth)

            // Store block data in context for this system context
            val serviceBlockDataMap = mutableMapOf<Node, ByteArray>()
            blockReadResult.blockDataByService.forEach { (service, blockData) ->
                serviceBlockDataMap[service] = blockData
            }

            // Update system context with block data
            val updatedSystemContext = systemContext.copy(serviceBlockData = serviceBlockDataMap)
            updatedSystemContexts.add(updatedSystemContext)

            // Update totals
            val contextBlocksRead = blockReadResult.blockDataByService.values.sumOf { it.size / 16 }
            totalBlocksRead += contextBlocksRead
            totalServicesProcessed += blockReadResult.blockDataByService.size
            maxBlocksPerRequest = maxOf(maxBlocksPerRequest, blockReadResult.maxBlocksPerRequest)
            maxServicesPerRequest =
                maxOf(maxServicesPerRequest, blockReadResult.maxServicesPerRequest)

            // Build context-specific results
            val contextResult = buildString {
                appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                appendLine("  Blocks read: $contextBlocksRead")
                appendLine("  Services processed: ${blockReadResult.blockDataByService.size}")
                appendLine()

                blockReadResult.blockDataByService.forEach { (service, blockData) ->
                    val blockCount = blockData.size / 16
                    appendLine("  Service ${service.code.toHexString()}: $blockCount blocks")
                    if (blockData.isNotEmpty()) {
                        val previewData = blockData.take(64).toByteArray()
                        appendLine("    Block data (first 64 bytes): ${previewData.toHexString()}")
                        if (blockData.size > 64) {
                            appendLine("    ... (${blockData.size - 64} more bytes)")
                        }
                    }
                    appendLine()
                }
            }
            contextResults.add(contextResult)
        }

        // Update scan context with all system contexts and global limits
        scanContext =
            scanContext.copy(
                systemScanContexts = updatedSystemContexts,
                maxBlocksPerRequest = maxBlocksPerRequest,
                maxServicesPerRequest = maxServicesPerRequest,
            )

        // Format results
        val collapsedResult =
            "Read $totalBlocksRead blocks from $totalServicesProcessed services across ${updatedSystemContexts.size} system(s)"

        val expandedResult =
            buildString {
                    appendLine("Block Reading Results:")
                    appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                    appendLine("Total blocks read: $totalBlocksRead")
                    appendLine("Total services processed: $totalServicesProcessed")
                    appendLine("Max blocks per request: $maxBlocksPerRequest")
                    appendLine("Max services per request: $maxServicesPerRequest")
                    appendLine()

                    contextResults.forEach { result -> appendLine(result) }

                    appendLine(
                        "Note: Only services that don't require authentication are processed."
                    )
                    appendLine(
                        "Block data is stored per system context for comprehensive analysis."
                    )
                }
                .trim()

        return collapsedResult to expandedResult
    }

    private suspend fun executeGetNodePropertyValueLimitedService(target: FeliCaTarget): String {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allDiscoveredNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Get Node Property (Value-Limited Service) requires discovered nodes from Search Service Codes step."
            )
        }

        var errors = 0
        val results = mutableListOf<String>()
        val maxNodesPerRequest = 16 // FeliCa specification limit
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val discoveredNodes = systemContext.nodes
            val nodeValueLimitedPurseProperties =
                systemContext.nodeValueLimitedPurseProperties.toMutableMap()
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (discoveredNodes.isEmpty()) {
                results.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No nodes discovered"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            val contextResults = mutableListOf<String>()

            // Try to get Value-Limited Purse Service properties in batches
            val valueLimitedPurseResults = mutableListOf<String>()

            discoveredNodes.chunked(maxNodesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val valueLimitedPurseCommand =
                    GetNodePropertyCommand(
                        target.idm,
                        NodePropertyType.VALUE_LIMITED_PURSE_SERVICE,
                        nodeBatch,
                    )
                val valueLimitedPurseResponse = target.transceive(valueLimitedPurseCommand)

                if (valueLimitedPurseResponse.isStatusSuccessful) {
                    nodeBatch.zip(valueLimitedPurseResponse.nodeProperties).forEach {
                        (node, property) ->
                        if (property is ValueLimitedPurseServiceProperty) {
                            val nodeType =
                                when (node) {
                                    is Area -> "Area"
                                    is Service -> "Service"
                                    is System -> "System"
                                    else -> "Unknown"
                                }

                            valueLimitedPurseResults.add(
                                buildString {
                                    appendLine("    $nodeType ${node.code.toHexString()}:")
                                    appendLine(
                                        "      Enabled: ${if (property.enabled) "Yes" else "No"}"
                                    )
                                    if (property.enabled) {
                                        appendLine("      Upper Limit: ${property.upperLimit}")
                                        appendLine("      Lower Limit: ${property.lowerLimit}")
                                        appendLine(
                                            "      Generation Number: ${property.generationNumber}"
                                        )
                                    }
                                }
                            )
                            nodeValueLimitedPurseProperties[node] = property
                        }
                    }
                } else {
                    valueLimitedPurseResults.add(
                        "    Batch ${batchIndex + 1}: Failed to retrieve Value-Limited Purse Service properties (Status: 0x${valueLimitedPurseResponse.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')})"
                    )
                }
            }

            contextResults.add(
                buildString {
                    appendLine("  Value-Limited Purse Service Properties:")
                    if (valueLimitedPurseResults.isNotEmpty()) {
                        appendLine(
                            "  Properties retrieved for ${valueLimitedPurseResults.size} nodes:"
                        )
                        appendLine()
                        valueLimitedPurseResults.forEach { result -> appendLine(result) }
                    } else {
                        appendLine("  No properties retrieved")
                    }
                }
            )
            // Update context with properties
            val updatedSystemContext =
                systemContext.copy(
                    nodeValueLimitedPurseProperties = nodeValueLimitedPurseProperties
                )
            updatedSystemContexts.add(updatedSystemContext)

            // Add context results to main results
            results.add(
                buildString {
                    appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                    contextResults.forEach { result ->
                        appendLine(result)
                        appendLine()
                    }
                }
            )
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        if (errors > 0) {
            throw RuntimeException(
                "Get Node Property (Value-Limited Service) encountered $errors error(s)"
            )
        }

        return buildString {
                appendLine("Get Node Property (Value-Limited Service) Results:")
                appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                appendLine()
                results.forEach { result ->
                    appendLine(result)
                    appendLine()
                }
            }
            .trim()
    }

    private suspend fun executeGetNodePropertyMacCommunication(target: FeliCaTarget): String {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allDiscoveredNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Get Node Property (MAC Communication) requires discovered nodes from Search Service Codes step."
            )
        }

        var errors = 0
        val results = mutableListOf<String>()
        val maxNodesPerRequest = 16 // FeliCa specification limit
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val discoveredNodes = systemContext.nodes
            val nodeMacCommunicationProperties =
                systemContext.nodeMacCommunicationProperties.toMutableMap()
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (discoveredNodes.isEmpty()) {
                results.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No nodes discovered"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            val contextResults = mutableListOf<String>()

            // Try to get MAC Communication properties in batches
            val macCommunicationResults = mutableListOf<String>()

            discoveredNodes.chunked(maxNodesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val macCommunicationCommand =
                    GetNodePropertyCommand(
                        target.idm,
                        NodePropertyType.MAC_COMMUNICATION,
                        nodeBatch,
                    )
                val macCommunicationResponse = target.transceive(macCommunicationCommand)

                if (macCommunicationResponse.isStatusSuccessful) {
                    nodeBatch.zip(macCommunicationResponse.nodeProperties).forEach {
                        (node, property) ->
                        if (property is MacCommunicationProperty) {
                            val nodeType =
                                when (node) {
                                    is Area -> "Area"
                                    is Service -> "Service"
                                    is System -> "System"
                                    else -> "Unknown"
                                }

                            macCommunicationResults.add(
                                "    $nodeType ${node.code.toHexString()}: MAC Communication ${if (property.enabled) "Enabled" else "Disabled"}"
                            )
                            nodeMacCommunicationProperties[node] = property
                        }
                    }
                } else {
                    macCommunicationResults.add(
                        "    Batch ${batchIndex + 1}: Failed to retrieve MAC Communication properties (Status: 0x${macCommunicationResponse.statusFlag1.toUByte().toString(16).uppercase().padStart(2, '0')})"
                    )
                }
            }

            contextResults.add(
                buildString {
                    appendLine("  MAC Communication Properties:")
                    if (macCommunicationResults.isNotEmpty()) {
                        appendLine(
                            "  Properties retrieved for ${macCommunicationResults.size} nodes:"
                        )
                        appendLine()
                        macCommunicationResults.forEach { result -> appendLine(result) }
                    } else {
                        appendLine("  No properties retrieved")
                    }
                }
            )

            // Update context with properties
            val updatedSystemContext =
                systemContext.copy(nodeMacCommunicationProperties = nodeMacCommunicationProperties)
            updatedSystemContexts.add(updatedSystemContext)

            // Add context results to main results
            results.add(
                buildString {
                    appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                    contextResults.forEach { result ->
                        appendLine(result)
                        appendLine()
                    }
                }
            )
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        if (errors > 0) {
            throw RuntimeException(
                "Get Node Property (MAC Communication) encountered $errors error(s)"
            )
        }

        return buildString {
                appendLine("Get Node Property (MAC Communication) Results:")
                appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                appendLine()
                results.forEach { result ->
                    appendLine(result)
                    appendLine()
                }
                appendLine(
                    "Note: MAC Communication properties indicate if MAC authentication is required."
                )
                appendLine(
                    "Nodes are processed in batches of up to $maxNodesPerRequest due to protocol limits."
                )
            }
            .trim()
    }

    private suspend fun executeRequestBlockInformation(target: FeliCaTarget): String {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allDiscoveredNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Request Block Information requires discovered nodes from Search Service Codes step."
            )
        }

        // Filter to get only services (areas and systems don't have block information in the same
        // way)
        val allServices = allDiscoveredNodes.filterIsInstance<Service>()

        if (allServices.isEmpty()) {
            throw RuntimeException(
                "No services discovered. Request Block Information requires at least one service to be discovered."
            )
        }

        // Request block information in batches (max 32 services per request as per FeliCa spec)
        val maxServicesPerRequest = 32
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val nodes = systemContext.nodes
            val blockInfoResults = mutableListOf<String>()
            val nodeBlockCountsMap = mutableMapOf<Node, CountInformation>()
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (nodes.isEmpty()) {
                results.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No nodes found"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            nodes.chunked(maxServicesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
                val requestBlockInfoCommand = RequestBlockInformationCommand(target.idm, nodeCodes)
                val requestBlockInfoResponse = target.transceive(requestBlockInfoCommand)

                // Process the block information for each service in this batch
                nodeBatch.zip(requestBlockInfoResponse.assignedBlockCountInformation).forEach {
                    (node, blockInfo) ->
                    val blockCount = blockInfo.toInt()
                    blockInfoResults.add(
                        "${node.fullCode.toHexString().padStart(8, ' ')}: ${blockCount.toString().padStart(5, ' ')} blocks"
                    )
                    // Store block count information object in map
                    nodeBlockCountsMap[node] = blockInfo
                }
            }

            // Update context with block count data
            val updatedSystemContext = systemContext.copy(nodeBlockCounts = nodeBlockCountsMap)
            updatedSystemContexts.add(updatedSystemContext)

            results.add(
                buildString {
                    appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                    appendLine("  Services processed: ${nodes.size}")
                    appendLine()

                    if (blockInfoResults.isNotEmpty()) {
                        blockInfoResults.forEach { result -> appendLine(result) }
                    } else {
                        appendLine("  No block information retrieved")
                    }
                }
            )
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        return buildString {
                appendLine("Request Block Information Results:")
                appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                appendLine("Total services processed: ${allServices.size}")
                appendLine()

                results.forEach { result ->
                    appendLine(result)
                    appendLine()
                }
            }
            .trim()
    }

    private suspend fun executeRequestBlockInformationEx(target: FeliCaTarget): String {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allDiscoveredNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Request Block Information Ex requires discovered nodes from Search Service Codes step."
            )
        }

        // Filter to get only services (areas and systems don't have block information in the same
        // way)
        val allServices = allDiscoveredNodes.filterIsInstance<Service>()

        if (allServices.isEmpty()) {
            throw RuntimeException(
                "No services discovered. Request Block Information Ex requires at least one service to be discovered."
            )
        }

        // Request block information in batches (max 32 services per request as per FeliCa spec)
        val maxServicesPerRequest = 16
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // Process each system context separately
        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val nodes = systemContext.nodes
            val blockInfoResults = mutableListOf<String>()
            val nodeAssignedBlockCountsMap = mutableMapOf<Node, CountInformation>()
            val nodeFreeBlockCountsMap = mutableMapOf<Node, CountInformation>()
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (nodes.isEmpty()) {
                results.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No services found"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            blockInfoResults.add("           Assign |  Free  |  Total")

            nodes.chunked(maxServicesPerRequest).forEachIndexed { batchIndex, nodeBatch ->
                val nodeCodes = nodeBatch.map { it.code }.toTypedArray()
                val requestBlockInfoExCommand =
                    RequestBlockInformationExCommand(target.idm, nodeCodes)
                val requestBlockInfoExResponse = target.transceive(requestBlockInfoExCommand)


                println("${nodeBatch.size} -> ${requestBlockInfoExResponse.assignedBlockCount.size}")
                // Process the extended block information for each service in this batch
                nodeBatch
                    .zip(
                        requestBlockInfoExResponse.assignedBlockCount.zip(
                            requestBlockInfoExResponse.freeBlockCount
                        )
                    )
                    .forEach { (node, blockCounts) ->
                        val (assignedCount, freeCount) = blockCounts
                        val assignedBlocks = assignedCount.toInt()
                        val freeBlocks = freeCount.toInt()
                        val totalBlocks = assignedBlocks + freeBlocks
                        blockInfoResults.add(
                            " ${node.fullCode.toHexString().padStart(8, ' ')}: ${assignedBlocks.toString().padStart(6, ' ')} | ${freeBlocks.toString().padStart(6, ' ')} | ${totalBlocks.toString().padStart(6, ' ')}"
                        )

                        // Store block count information objects in maps
                        nodeAssignedBlockCountsMap[node] = assignedCount
                        nodeFreeBlockCountsMap[node] = freeCount
                    }
            }

            // Update context with block count data
            val updatedSystemContext =
                systemContext.copy(
                    nodeAssignedBlockCounts = nodeAssignedBlockCountsMap,
                    nodeFreeBlockCounts = nodeFreeBlockCountsMap,
                )
            updatedSystemContexts.add(updatedSystemContext)

            results.add(
                buildString {
                    appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                    appendLine("  Services processed: ${nodes.size}")
                    appendLine()

                    if (blockInfoResults.isNotEmpty()) {
                        blockInfoResults.forEach { result -> appendLine(result) }
                    } else {
                        appendLine("  No block information retrieved")
                    }
                }
            )
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        return buildString {
                appendLine("Request Block Information Ex Results:")
                appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                appendLine("Total services processed: ${allServices.size}")
                appendLine()

                results.forEach { result ->
                    appendLine(result)
                    appendLine()
                }
            }
            .trim()
    }

    /**
     * Formats timeout information as a formula showing constant + per-unit components If the
     * command is not supported (timeout byte is 0x00), displays "Not supported"
     */
    private fun formatTimeoutFormula(
        constant: kotlin.time.Duration,
        perUnit: kotlin.time.Duration,
        isSupported: Boolean,
    ): String {
        if (!isSupported) {
            return "Not supported"
        }
        val constMs = String.format("%.2f", constant.inWholeNanoseconds / 1_000_000.0)
        val perUnitMs = String.format("%.2f", perUnit.inWholeNanoseconds / 1_000_000.0)
        return "${constMs} + (${perUnitMs} * n) ms"
    }
}
