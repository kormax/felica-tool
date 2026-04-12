package com.kormax.felicatool.service

import android.nfc.TagLostException
import android.util.Log
import com.kormax.felicatool.felica.*
import com.kormax.felicatool.service.logging.CommunicationLogEntry
import com.kormax.felicatool.service.logging.CommunicationLoggedFeliCaTarget
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.util.IcTypeMapping
import com.kormax.felicatool.util.NodeDefinitionType
import com.kormax.felicatool.util.NodeRegistry
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
    val specificationVersion: SpecificationVersion? = null,
    val containerIssueInformation: ContainerInformation? = null,
    val platformInformation: GetPlatformInformationResponse? = null,
    val containerIdm: ByteArray? = null,
    val readWithoutEncryptionErrorLocationIndication: ErrorLocationIndication =
        ErrorLocationIndication.INDEX,
    val readWithoutEncryptionMaxBlocksPerRequest: Int? = null,
    val readWithoutEncryptionMaxServicesPerRequest: Int? = null,
    val writeWithoutEncryptionErrorLocationIndication: ErrorLocationIndication? = null,
    val writeWithoutEncryptionMaxBlocksPerRequest: Int? = null,
    val echoMaxPayloadSize: Int? = null,
    val requestServiceUnknownNodeAttributesSupported: Boolean? = null,
    val authentication1DesNodeListHierarchyValidation: Authentication1DesNodeListHierarchyValidation =
        Authentication1DesNodeListHierarchyValidation.UNKNOWN,
    val readWithoutEncryptionIllegalNumberErrorPreference: IllegalNumberErrorPreference? = null,
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
    val getPlatformInformationSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getContainerIdSupport: CommandSupport = CommandSupport.UNKNOWN,
    val echoSupport: CommandSupport = CommandSupport.UNKNOWN,
    val resetModeSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getNodePropertyValueLimitedServiceSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getNodePropertyMacCommunicationSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestBlockInformationSupport: CommandSupport = CommandSupport.UNKNOWN,
    val requestBlockInformationExSupport: CommandSupport = CommandSupport.UNKNOWN,
    val readBlocksWithoutEncryptionSupport: CommandSupport = CommandSupport.UNKNOWN,
    val writeBlocksWithoutEncryptionSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getAreaInformationSupport: CommandSupport = CommandSupport.UNKNOWN,
    val getContainerPropertySupport: CommandSupport = CommandSupport.UNKNOWN,
    val authentication1DesSupport: CommandSupport = CommandSupport.UNKNOWN,
    val authentication1AesSupport: CommandSupport = CommandSupport.UNKNOWN,
    val internalAuthenticateAndReadSupport: CommandSupport = CommandSupport.UNKNOWN,
    // Container property values - map of property object to response data
    val containerPropertyValues: Map<GetContainerPropertyCommand.Property, ByteArray> = emptyMap(),
    val communicationLog: List<CommunicationLogEntry> = emptyList(),
) {}

data class SystemScanContext(
    val systemCode: ByteArray? = null,
    val mode: Mode = Mode.Mode0,
    val nodes: List<Node> = emptyList(),
    /** Set of nodes that were populated from the registry (not discovered via search) */
    val registryPopulatedNodes: Set<Node> = emptySet(),
    val nodeKeyVersions: Map<Node, KeyVersion> = emptyMap(),
    val nodeAesKeyVersions: Map<Node, KeyVersion> = emptyMap(),
    val nodeDesKeyVersions: Map<Node, KeyVersion> = emptyMap(),
    val encryptionIdentifier: EncryptionIdentifier? = null,
    val nodeBlockCounts: Map<Node, CountInformation> = emptyMap(),
    val nodeAssignedBlockCounts: Map<Node, CountInformation> = emptyMap(),
    val nodeFreeBlockCounts: Map<Node, CountInformation> = emptyMap(),
    /** Block data stored as Map<BlockNumber, BlockData> for each node */
    val serviceBlockData: Map<Node, Map<Int, ByteArray>> = emptyMap(),
    val nodeValueLimitedPurseProperties: Map<Node, ValueLimitedPurseServiceProperty> = emptyMap(),
    val nodeMacCommunicationProperties: Map<Node, MacCommunicationProperty> = emptyMap(),
    val systemStatus: ByteArray? = null,
    val idm: ByteArray? = null,
    /** Set of nodes discovered via force discovery that were not found in regular discovery */
    val hiddenNodes: Set<Node> = emptySet(),
)

class CardScanService {

    private data class ReadWithoutEncryptionTestTarget(
        val systemContext: SystemScanContext,
        val service: Service,
        val blockNumber: Int,
    )

    private data class Authentication1DesTestTarget(
        val systemContext: SystemScanContext,
        val rootArea: Area,
    )

    private data class AuthenticationStateResetResult(
        val message: String,
        val modeSetToMode0: Boolean,
    )

    companion object {
        const val CARD_LOST_MESSAGE = "Card lost during scan - scan terminated"
        private const val NO_SERVICES_AVAILABLE = "No services available"
        private const val NO_SERVICES_WITHOUT_AUTHENTICATION =
            "No services found that don't require authentication"
        private const val NO_READABLE_SERVICE_IN_SELECTED_SYSTEM =
            "No readable services found in the selected system"
        private const val NO_SYSTEM_CONTEXT_WITH_READABLE_SERVICES =
            "No system context found with readable services"
        private val FELICA_LITE_SYSTEM_CODE = byteArrayOf(0x88.toByte(), 0xB4.toByte())
        private val NDEF_SYSTEM_CODE = byteArrayOf(0x12.toByte(), 0xFC.toByte())
        private val PROTECTED_READ_TEST_SERVICE_CODES = setOf("0B00", "0900")
        private const val PROTECTED_READ_TEST_BLOCK_NUMBER = 0x0092
        private const val REQUEST_SERVICE_UNAVAILABLE_FOR_UNKNOWN_ATTRIBUTE_PROBE =
            "Request Service command is unavailable; cannot probe unknown attribute behavior"
        private const val REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS = 3
        private const val REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER = 0
        private const val AUTHENTICATION1_DES_UNAVAILABLE_FOR_NODE_LIST_HIERARCHY_VALIDATION =
            "Authenticate1 DES support is not confirmed; cannot check node-list hierarchy validation"
        private const val AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS = 3
    }

    // Context to store discovered nodes across steps
    private var scanContext = CardScanContext()

    // Scan settings for current session
    private var scanSettings = ScanSettings()

    /** Sets the scan settings for the current session */
    fun setScanSettings(settings: ScanSettings) {
        scanSettings = settings
    }

    // Public getter for context
    fun getScanContext(): CardScanContext = scanContext

    /** Returns a wrapped FeliCaTarget that logs all communications. Clears previous logs. */
    fun wrapTargetForCommunicationLogging(target: FeliCaTarget): FeliCaTarget {
        return CommunicationLoggedFeliCaTarget(target)
    }

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
                "get_platform_information" ->
                    scanContext.copy(getPlatformInformationSupport = support)
                "get_system_status" -> scanContext.copy(getSystemStatusSupport = support)
                "request_code_list" -> scanContext.copy(requestCodeListSupport = support)
                "search_service_code" -> scanContext.copy(searchServiceCodeSupport = support)
                "request_service" -> scanContext.copy(requestServiceSupport = support)
                "request_service_v2" -> scanContext.copy(requestServiceV2Support = support)
                "set_parameter" -> scanContext.copy(setParameterSupport = support)
                "get_container_issue_information" ->
                    scanContext.copy(getContainerIssueInformationSupport = support)
                "get_container_id" -> scanContext.copy(getContainerIdSupport = support)
                "get_container_property" -> scanContext.copy(getContainerPropertySupport = support)
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
                "read_without_encryption_determine_supported" ->
                    scanContext.copy(readBlocksWithoutEncryptionSupport = support)
                "read_without_encryption_determine_max_services" ->
                    scanContext.copy(readBlocksWithoutEncryptionSupport = support)
                "read_without_encryption_detect_illegal_number_error_preference" ->
                    scanContext.copy(readBlocksWithoutEncryptionSupport = support)
                "read_without_encryption_determine_max_blocks" ->
                    scanContext.copy(readBlocksWithoutEncryptionSupport = support)
                "read_blocks_without_encryption" ->
                    scanContext.copy(readBlocksWithoutEncryptionSupport = support)
                "write_without_encryption_determine_error_indication" ->
                    scanContext.copy(writeBlocksWithoutEncryptionSupport = support)
                "write_without_encryption_determine_max_services" ->
                    scanContext.copy(writeBlocksWithoutEncryptionSupport = support)
                "write_without_encryption_detect_illegal_number_error_preference" ->
                    scanContext.copy(writeBlocksWithoutEncryptionSupport = support)
                "write_without_encryption_determine_max_blocks" ->
                    scanContext.copy(writeBlocksWithoutEncryptionSupport = support)
                "get_area_information" -> scanContext.copy(getAreaInformationSupport = support)
                "authentication1_des" -> scanContext.copy(authentication1DesSupport = support)
                "authentication1_aes" -> scanContext.copy(authentication1AesSupport = support)
                "internal_authenticate_and_read" ->
                    scanContext.copy(internalAuthenticateAndReadSupport = support)
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
            "request_service_determine_unknown_node_attributes_supported" -> CommandSupport.UNKNOWN
            "authentication1_des_node_list_hierarchy_validation" -> CommandSupport.UNKNOWN
            "request_service_v2" -> scanContext.requestServiceV2Support
            "set_parameter" -> scanContext.setParameterSupport
            "get_container_issue_information" -> scanContext.getContainerIssueInformationSupport
            "get_platform_information" -> scanContext.getPlatformInformationSupport
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
            "read_without_encryption_determine_supported" ->
                scanContext.readBlocksWithoutEncryptionSupport
            "read_without_encryption_determine_max_services" ->
                scanContext.readBlocksWithoutEncryptionSupport
            "read_without_encryption_detect_illegal_number_error_preference" ->
                scanContext.readBlocksWithoutEncryptionSupport
            "read_without_encryption_determine_max_blocks" ->
                scanContext.readBlocksWithoutEncryptionSupport
            "read_blocks_without_encryption" -> scanContext.readBlocksWithoutEncryptionSupport
            "write_without_encryption_determine_error_indication" ->
                scanContext.writeBlocksWithoutEncryptionSupport
            "write_without_encryption_determine_max_services" ->
                scanContext.writeBlocksWithoutEncryptionSupport
            "write_without_encryption_detect_illegal_number_error_preference" ->
                scanContext.writeBlocksWithoutEncryptionSupport
            "write_without_encryption_determine_max_blocks" ->
                scanContext.writeBlocksWithoutEncryptionSupport
            "get_area_information" -> scanContext.getAreaInformationSupport
            "get_container_property" -> scanContext.getContainerPropertySupport
            "authentication1_des" -> scanContext.authentication1DesSupport
            "authentication1_aes" -> scanContext.authentication1AesSupport
            "internal_authenticate_and_read" -> scanContext.internalAuthenticateAndReadSupport
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
    private fun ByteArray?.sameBytes(other: ByteArray?): Boolean =
        when {
            this == null && other == null -> true
            this == null || other == null -> false
            else -> this.contentEquals(other)
        }

    private fun setSystemMode(systemCode: ByteArray?, mode: Mode) {
        var matched = false
        val updatedContexts =
            scanContext.systemScanContexts.map { context ->
                when {
                    context.systemCode.sameBytes(systemCode) -> {
                        matched = true
                        context.copy(mode = mode)
                    }
                    mode != Mode.Mode0 && context.mode != Mode.Mode0 ->
                        context.copy(mode = Mode.Mode0)
                    else -> context
                }
            }
        if (matched) {
            scanContext = scanContext.copy(systemScanContexts = updatedContexts)
        }
    }

    private fun resetAllSystemModesToMode0() {
        scanContext =
            scanContext.copy(
                systemScanContexts =
                    scanContext.systemScanContexts.map { context ->
                        if (context.mode == Mode.Mode0) {
                            context
                        } else {
                            context.copy(mode = Mode.Mode0)
                        }
                    }
            )
    }

    private fun updateSystemIdmFromPolling(polledSystemCode: ByteArray?, polledIdm: ByteArray) {
        if (scanContext.systemScanContexts.isEmpty()) {
            return
        }

        var updated = false
        val updatedContexts =
            scanContext.systemScanContexts.mapIndexed { index, context ->
                val shouldUpdate =
                    when {
                        polledSystemCode != null -> context.systemCode.sameBytes(polledSystemCode)
                        scanContext.primarySystemCode != null ->
                            context.systemCode.sameBytes(scanContext.primarySystemCode)
                        scanContext.systemScanContexts.size == 1 -> index == 0
                        else -> false
                    }
                if (shouldUpdate && context.idm?.contentEquals(polledIdm) != true) {
                    updated = true
                    context.copy(idm = polledIdm)
                } else {
                    context
                }
            }

        if (updated) {
            scanContext = scanContext.copy(systemScanContexts = updatedContexts)
        }
    }

    private fun updateModeAfterSuccessfulPolling(polledSystemCode: ByteArray?) {
        val contexts = scanContext.systemScanContexts
        val activeModes = contexts.filter { context -> context.mode != Mode.Mode0 }
        if (activeModes.isEmpty()) {
            return
        }

        val keepCurrentMode =
            if (polledSystemCode != null) {
                activeModes.any { context -> context.systemCode.sameBytes(polledSystemCode) }
            } else {
                val primarySystemCode = scanContext.primarySystemCode
                primarySystemCode != null &&
                    activeModes.any { context -> context.systemCode.sameBytes(primarySystemCode) }
            }

        if (!keepCurrentMode) {
            resetAllSystemModesToMode0()
        }
    }

    private fun setSystemModeByIdm(idm: ByteArray, mode: Mode) {
        var matched = false
        val updatedContexts =
            scanContext.systemScanContexts.map { context ->
                if (context.idm?.contentEquals(idm) == true) {
                    matched = true
                    context.copy(mode = mode)
                } else {
                    context
                }
            }

        if (matched) {
            scanContext = scanContext.copy(systemScanContexts = updatedContexts)
        } else if (scanContext.systemScanContexts.size == 1) {
            scanContext =
                scanContext.copy(
                    systemScanContexts =
                        listOf(scanContext.systemScanContexts.first().copy(mode = mode))
                )
        }
    }

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
        updateSystemIdmFromPolling(systemCode, pollingResponse.idm)
        updateModeAfterSuccessfulPolling(systemCode)
    }

    private suspend fun resetAuthenticationState(
        target: FeliCaTarget,
        authenticatedSystemCode: ByteArray?,
        authenticatedSystemIdm: ByteArray?,
        allowAlternateSystemPollingFallback: Boolean = true,
    ): AuthenticationStateResetResult {
        if (authenticatedSystemIdm == null) {
            return AuthenticationStateResetResult(
                message = "State reset skipped: selected-system IDM is unavailable",
                modeSetToMode0 = false,
            )
        }

        if (scanContext.resetModeSupport == CommandSupport.SUPPORTED) {
            return try {
                val resetModeResponse = target.transceive(ResetModeCommand(authenticatedSystemIdm))
                if (resetModeResponse.isStatusSuccessful) {
                    setSystemMode(authenticatedSystemCode, Mode.Mode0)
                    AuthenticationStateResetResult(
                        message = "Reset Mode executed - card reset to Mode0",
                        modeSetToMode0 = true,
                    )
                } else {
                    AuthenticationStateResetResult(
                        message = "Reset Mode failed (${formatStatus(resetModeResponse)})",
                        modeSetToMode0 = false,
                    )
                }
            } catch (e: Exception) {
                val details = e.message ?: e::class.simpleName ?: "Unknown error"
                AuthenticationStateResetResult(
                    message = "Reset Mode error ($details)",
                    modeSetToMode0 = false,
                )
            }
        }

        if (!allowAlternateSystemPollingFallback) {
            return AuthenticationStateResetResult(
                message = "Reset Mode not executed (not confirmed as supported)",
                modeSetToMode0 = false,
            )
        }

        val alternativeSystemCode =
            scanContext.systemScanContexts
                .firstOrNull { context ->
                    !context.systemCode.sameBytes(authenticatedSystemCode)
                }
                ?.systemCode

        if (alternativeSystemCode == null) {
            return AuthenticationStateResetResult(
                message =
                    "State reset unavailable: Reset Mode support is not confirmed and no alternate system is available for polling reset",
                modeSetToMode0 = false,
            )
        }

        return try {
            pollSystemCode(target, alternativeSystemCode)
            val returnPollingResult =
                try {
                    if (authenticatedSystemCode != null) {
                        pollSystemCode(target, authenticatedSystemCode)
                        "re-polled back to selected system (${authenticatedSystemCode.toHexString().uppercase()})"
                    } else {
                        "selected system code is unavailable for re-poll"
                    }
                } catch (e: Exception) {
                    val details = e.message ?: e::class.simpleName ?: "Unknown error"
                    "failed to re-poll selected system ($details)"
                }

            AuthenticationStateResetResult(
                message =
                    "State reset by polling another system (${alternativeSystemCode.toHexString().uppercase()}); $returnPollingResult",
                modeSetToMode0 = true,
            )
        } catch (e: Exception) {
            val details = e.message ?: e::class.simpleName ?: "Unknown error"
            AuthenticationStateResetResult(
                message = "State reset via alternate-system polling failed ($details)",
                modeSetToMode0 = false,
            )
        }
    }

    /**
     * Returns the block number to use for Read Without Encryption probe commands. For FeliCa Lite
     * (88B4) and NDEF (12FC) systems, services 0x000B (0B00) and 0x0009 (0900) must probe block
     * 0x0092 (STATE); all other cases use block 0.
     */
    private fun resolveReadWithoutEncryptionTestBlockNumber(
        systemCode: ByteArray?,
        serviceCode: ByteArray,
    ): Int {
        val isProtectedSystem =
            systemCode?.let { code ->
                code.contentEquals(FELICA_LITE_SYSTEM_CODE) || code.contentEquals(NDEF_SYSTEM_CODE)
            } ?: false
        if (!isProtectedSystem) {
            return 0
        }
        val serviceCodeHex = serviceCode.toHexString().uppercase()
        return if (serviceCodeHex in PROTECTED_READ_TEST_SERVICE_CODES) {
            PROTECTED_READ_TEST_BLOCK_NUMBER
        } else {
            0
        }
    }

    private fun byteToHex(statusFlag: Number): String =
        (statusFlag.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')

    private fun resolveUnknownNodeAttributeValue(): Int {
        val knownAttributes = buildSet {
            addAll(ServiceAttribute.entries.map { it.value })
            addAll(AreaAttribute.entries.map { it.value })
        }
        return (0..0x3F).firstOrNull { it !in knownAttributes }
            ?: throw IllegalStateException("No unknown node attribute value available for probing")
    }

    private fun describeNodeForAuthenticationCheck(node: Node): String =
        when (node) {
            is Area -> "Area ${node.number}-${node.endNumber} (${node.code.toHexString().uppercase()})"
            is Service -> "Service ${node.number} (${node.code.toHexString().uppercase()})"
            is System -> "System (${node.code.toHexString().uppercase()})"
            else -> "Node (${node.code.toHexString().uppercase()})"
        }

    private fun hasValidDesKeyOnRootArea(systemContext: SystemScanContext, rootArea: Area): Boolean {
        return systemContext.nodeDesKeyVersions.containsKey(rootArea) ||
            (!systemContext.nodeAesKeyVersions.containsKey(rootArea) &&
                systemContext.nodeKeyVersions.containsKey(rootArea))
    }

    private fun findBestAuthentication1DesTarget(
        modeAllowed: ((Mode) -> Boolean)? = null
    ): Authentication1DesTestTarget? {
        var bestTarget: Authentication1DesTestTarget? = null
        var bestNodeCount = -1

        for (systemContext in scanContext.systemScanContexts) {
            if (modeAllowed != null && !modeAllowed(systemContext.mode)) {
                continue
            }

            val rootArea = systemContext.nodes.filterIsInstance<Area>().firstOrNull { it.isRoot } ?: Area.ROOT
            if (!hasValidDesKeyOnRootArea(systemContext, rootArea)) {
                continue
            }

            val nodeCount = systemContext.nodes.size
            if (nodeCount > bestNodeCount) {
                bestNodeCount = nodeCount
                bestTarget = Authentication1DesTestTarget(systemContext = systemContext, rootArea = rootArea)
            }
        }

        return bestTarget
    }

    private fun findAuthentication1DesNonImmediateNode(
        testTarget: Authentication1DesTestTarget
    ): Node? {
        val rootArea = testTarget.rootArea
        val areasInSystem = testTarget.systemContext.nodes.filterIsInstance<Area>()
        val nonRootAreas =
            areasInSystem.filter { area ->
                area != rootArea && !area.isRoot && area.belongsTo(rootArea)
            }

        if (nonRootAreas.isEmpty()) {
            return null
        }

        val serviceCandidate =
            testTarget.systemContext.nodes
                .filterIsInstance<Service>()
                .filter { service -> nonRootAreas.any { area -> service.belongsTo(area) } }
                .sortedBy { it.number }
                .firstOrNull()
        if (serviceCandidate != null) {
            return serviceCandidate
        }

        return nonRootAreas
            .filter { candidate ->
                nonRootAreas.any { parent -> parent != candidate && candidate.belongsTo(parent) }
            }
            .sortedBy { it.number }
            .firstOrNull()
    }

    private fun formatStatus(
        statusFlag1: Number?,
        statusFlag2: Number?,
        prefix: String = "status{n}=",
    ): String {
        val value1 = "0x${statusFlag1?.let { byteToHex(it) } ?: "??"}"
        val value2 = "0x${statusFlag2?.let { byteToHex(it) } ?: "??"}"
        if (prefix.isEmpty()) {
            return "$value1 $value2"
        }

        val label1 = prefix.replace("{n}", "1")
        val label2 = prefix.replace("{n}", "2")
        val entry1 = if (label1.endsWith("=")) "$label1$value1" else "$label1=$value1"
        val entry2 = if (label2.endsWith("=")) "$label2$value2" else "$label2=$value2"
        return "$entry1, $entry2"
    }

    private fun formatStatus(response: WithStatusFlags, prefix: String = "status{n}="): String =
        formatStatus(response.statusFlag1, response.statusFlag2, prefix)

    private fun markReadWithoutEncryptionSupported() {
        scanContext =
            scanContext.copy(readBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED)
    }

    private fun throwReadWithoutEncryptionProbeFallback(message: String): Nothing {
        markReadWithoutEncryptionSupported()
        throw CommandSupportedBehaviorUnexpectedException(message)
    }

    /**
     * Finds the preferred system/service/block target for Read Without Encryption probe commands.
     * Includes all shared guard checks and system selection.
     *
     * Preference order for service selection in the chosen system:
     * 1) non-zero service number
     * 2) RANDOM service type
     * 3) READ_ONLY mode (READ_WRITE is still acceptable)
     */
    private fun findReadWithoutEncryptionTestTarget(
        allowAuthenticationRequiredFallback: Boolean = false
    ): ReadWithoutEncryptionTestTarget {
        val allServices =
            scanContext.systemScanContexts.flatMap { context ->
                context.nodes.filterIsInstance<Service>()
            }
        if (allServices.isEmpty()) {
            throw PrerequisiteException(NO_SERVICES_AVAILABLE)
        }

        val allServicesWithoutAuth =
            allServices.filter { service -> !service.attribute.authenticationRequired }
        val useAuthenticationRequiredFallback =
            allowAuthenticationRequiredFallback && allServicesWithoutAuth.isEmpty()
        if (allServicesWithoutAuth.isEmpty() && !useAuthenticationRequiredFallback) {
            throw PrerequisiteException(NO_SERVICES_WITHOUT_AUTHENTICATION)
        }

        val bestSystemContext =
            if (useAuthenticationRequiredFallback) {
                scanContext.systemScanContexts.maxByOrNull { systemContext ->
                    systemContext.nodes.filterIsInstance<Service>().size
                }
            } else {
                scanContext.systemScanContexts.maxByOrNull { systemContext ->
                    systemContext.nodes.filterIsInstance<Service>().count { service ->
                        !service.attribute.authenticationRequired
                    }
                }
            } ?: throw PrerequisiteException(NO_SYSTEM_CONTEXT_WITH_READABLE_SERVICES)

        val servicesInBestSystem = bestSystemContext.nodes.filterIsInstance<Service>()
        val servicesWithoutAuth =
            servicesInBestSystem.filter { service -> !service.attribute.authenticationRequired }
        val candidateServices =
            if (servicesWithoutAuth.isNotEmpty()) {
                servicesWithoutAuth
            } else if (useAuthenticationRequiredFallback) {
                servicesInBestSystem
            } else {
                emptyList()
            }
        if (candidateServices.isEmpty()) {
            throw PrerequisiteException(NO_READABLE_SERVICE_IN_SELECTED_SYSTEM)
        }

        val testService =
            candidateServices.maxByOrNull { service ->
                var score = 0
                if (service.number != 0) score += 4
                if (service.attribute.type == ServiceType.RANDOM) score += 2
                if (service.attribute.mode == ServiceMode.READ_ONLY) score += 1
                score
            }!!

        val testBlockNumber =
            resolveReadWithoutEncryptionTestBlockNumber(
                systemCode = bestSystemContext.systemCode,
                serviceCode = testService.code,
            )

        return ReadWithoutEncryptionTestTarget(
            systemContext = bestSystemContext,
            service = testService,
            blockNumber = testBlockNumber,
        )
    }

    private suspend fun executeReadWithoutEncryptionDetermineSupported(
        target: FeliCaTarget
    ): String {
        val testTarget =
            findReadWithoutEncryptionTestTarget(allowAuthenticationRequiredFallback = true)
        pollSystemCode(target, testTarget.systemContext.systemCode)

        val command =
            ReadWithoutEncryptionCommand(
                idm = target.idm,
                serviceCodes = arrayOf(testTarget.service.code),
                blockListElements =
                    arrayOf(
                        BlockListElement(
                            serviceCodeListOrder = 0,
                            blockNumber = testTarget.blockNumber,
                        )
                    ),
            )

        val response = target.transceive(command)
        markReadWithoutEncryptionSupported()

        val systemCodeHex = testTarget.systemContext.systemCode?.toHexString() ?: "unknown"
        val serviceCodeHex = testTarget.service.code.toHexString().uppercase()
        val authFallbackUsed = testTarget.service.attribute.authenticationRequired

        return buildString {
                appendLine("Read Without Encryption command is supported (response received)")
                appendLine(
                    "System: $systemCodeHex; Service: $serviceCodeHex; Block: ${
                    testTarget.blockNumber.toString(
                        16
                    ).uppercase().padStart(4, '0')
                }"
                )
                appendLine("(${formatStatus(response)})")
                if (authFallbackUsed) {
                    appendLine(
                        "Note: Used auth-required service fallback because no no-auth service was available."
                    )
                }
            }
            .trim()
    }

    /**
     * Ensures the card is present before executing a command. If Request Response is known to be
     * supported, it is attempted first. Request Service (service 0, read-only without key) is then
     * tried, and polling is used as the last resort.
     */
    private suspend fun ensureCardPresence(target: FeliCaTarget, stepId: String) {
        if (getCommandSupport("request_response") == CommandSupport.SUPPORTED) {
            try {
                val response = target.transceive(RequestResponseCommand(target.idm))
                target.idm = response.idm
                return
            } catch (e: Exception) {
                Log.w(
                    "CardScanService",
                    "Request Response presence check failed for step $stepId",
                    e,
                )
            }
        }

        if (getCommandSupport("request_service") == CommandSupport.SUPPORTED) {
            // Some cards, such as IC 0x24 on Octopus, may stop responding to RequestResponse in
            // Mode1, while RequestService (and Authentication1) still respond.
            try {
                val probeService = Service(0, ServiceAttribute.RandomRoWithoutKey)
                val response =
                    target.transceive(RequestServiceCommand(target.idm, arrayOf(probeService.code)))
                target.idm = response.idm
                return
            } catch (e: Exception) {
                Log.w(
                    "CardScanService",
                    "Request Service presence check failed for step $stepId",
                    e,
                )
            }
        }

        pollSystemCode(target)
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
            var presenceVerified = false

            // Try up to 3 attempts
            for (attempt in 1..3) {
                try {
                    ensureCardPresence(target, step.id)
                    presenceVerified = true
                    break
                } catch (e: Exception) {
                    val tagLostForGood =
                        when (e) {
                            is TagLostException -> false
                            is SecurityException ->
                                e.message?.contains("out of date", ignoreCase = true) == true
                            else -> true
                        }
                    lastException = e
                    if (tagLostForGood) {
                        Log.w(
                            "CardScanService",
                            "Card lost during presence check for step ${step.id}",
                            e,
                        )
                        break
                    }
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

            if (!presenceVerified) {
                Log.e(
                    "CardScanService",
                    "Card presence check failed for step ${step.id}",
                    lastException,
                )
                return step.copy(
                    status = StepStatus.ERROR,
                    errorMessage = CARD_LOST_MESSAGE,
                    duration = kotlin.time.Duration.ZERO,
                )
            }
        }

        // Check if command is already known to be unsupported
        val commandSupport = getCommandSupport(step.id)
        if (commandSupport == CommandSupport.UNSUPPORTED) {
            val duration = kotlin.time.Duration.ZERO
            // Skip execution for known unsupported commands
            return step.copy(
                status = StepStatus.COMPLETED,
                result = "Command not supported by this card",
                duration = duration,
            )
        }

        // Mark step as in progress
        val inProgressStep = step.copy(status = StepStatus.IN_PROGRESS)
        onStepUpdate(inProgressStep)

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
                    "request_service_determine_unknown_node_attributes_supported" -> {
                        val result = executeRequestServiceUnknownNodeAttributes(target)
                        step.copy(status = StepStatus.COMPLETED, result = result)
                    }
                    "authentication1_des_node_list_hierarchy_validation" -> {
                        val result = executeAuthentication1DesNodeListHierarchyValidation(target)
                        step.copy(status = StepStatus.COMPLETED, result = result)
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
                    "force_discover_nodes" -> {
                        val (collapsedResult, expandedResult) = executeForceDiscoverNodes(target)
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
                    "read_without_encryption_determine_supported" -> {
                        val result = executeReadWithoutEncryptionDetermineSupported(target)
                        updateCommandSupport(step.id, CommandSupport.SUPPORTED)
                        step.copy(status = StepStatus.COMPLETED, result = result)
                    }
                    "read_without_encryption_detect_illegal_number_error_preference" -> {
                        val result =
                            executeReadWithoutEncryptionDetectIllegalNumberErrorPreference(target)
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
                    "force_discover_blocks" -> {
                        val (collapsedResult, expandedResult) = executeForceDiscoverBlocks(target)
                        step.copy(
                            status = StepStatus.COMPLETED,
                            result = expandedResult,
                            collapsedResult = collapsedResult,
                            isCollapsed = true,
                        )
                    }
                    "scan_overview" -> {
                        // Copy logs from target into scan context at overview step
                        (target as? CommunicationLoggedFeliCaTarget)?.let { loggedTarget ->
                            scanContext = scanContext.copy(communicationLog = loggedTarget.log)
                        }

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
                                "get_area_information" -> executeGetAreaInformation(target)
                                "set_parameter" -> executeSetParameter(target)
                                "get_container_issue_information" ->
                                    executeGetContainerIssueInformation(target)
                                "get_platform_information" -> executeGetPlatformInformation(target)
                                "get_container_id" -> executeGetContainerId(target)
                                "get_container_property" -> executeGetContainerProperty(target)
                                "echo" -> executeEcho(target)
                                "internal_authenticate_and_read" ->
                                    executeInternalAuthenticateAndRead(target)
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
                                "write_without_encryption_determine_error_indication" ->
                                    executeWriteWithoutEncryptionDetermineErrorIndication(target)
                                "write_without_encryption_determine_max_blocks" ->
                                    executeWriteWithoutEncryptionDetermineMaxBlocks(target)
                                "authentication1_des" -> executeAuthentication1Des(target)
                                "authentication1_aes" -> executeAuthentication1Aes(target)
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
        } catch (e: CommandSupportedBehaviorUnexpectedException) {
            // Probe fallback applied - command responded but fallback values were used
            Log.w("CardScanService", "Probe fallback used for step ${step.id}: ${e.message}")

            val duration = startTime.elapsedNow()

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Probe fallback applied",
                duration = duration,
            )
        } catch (e: PrerequisiteException) {
            // Prerequisite not met - don't mark command as unsupported, leave as unknown
            Log.w("CardScanService", "Prerequisite not met for step ${step.id}: ${e.message}")

            val duration = startTime.elapsedNow()

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Prerequisite not met",
                duration = duration,
            )
        } catch (e: Exception) {
            Log.e("CardScanService", "Error executing step ${step.id}", e)

            // Calculate execution duration even for errors
            val duration = startTime.elapsedNow()

            // Mark command as unsupported if it fails
            updateCommandSupport(step.id, CommandSupport.UNSUPPORTED)

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Unknown error",
                duration = duration,
            )
        }
    }

    private suspend fun handleDiscoveredSystemCodes(
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
                // Verify the system can be polled before creating a context
                val canPoll =
                    try {
                        pollSystemCode(target, systemCode)
                        true
                    } catch (e: Exception) {
                        Log.d(
                            "CardScanService",
                            "Skipping system ${systemCode.toHexString().uppercase()} - polling failed: ${e.message}",
                        )
                        false
                    }

                if (canPoll) {
                    val newContext =
                        SystemScanContext(
                            systemCode = systemCode,
                            nodes = emptyList(),
                            idm = target.idm,
                        )
                    updatedSystemContexts.add(newContext)
                }
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
                appendLine("  ROM Type: 0x${byteToHex(pmm.romType)}")
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
            .trimEnd()
    }

    private suspend fun executeRequestResponse(target: FeliCaTarget): String {
        val requestResponseCommand = RequestResponseCommand(target.idm)
        val requestResponseResponse = target.transceive(requestResponseCommand)

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
        scanContext =
            scanContext.copy(specificationVersion = requestSpecVersionResponse.specificationVersion)

        return buildString {
                appendLine("Specification Version Information:")
                appendLine("Status Flags: ${formatStatus(requestSpecVersionResponse, prefix = "")}")

                if (requestSpecVersionResponse.isStatusSuccessful) {
                    appendLine(
                        "Format Version: 0x${requestSpecVersionResponse.specificationVersion?.formatVersion?.toUByte()?.toString(16)?.uppercase()?.padStart(2, '0') ?: "N/A"}"
                    )
                    appendLine()

                    requestSpecVersionResponse.specificationVersion?.basicVersion?.let {
                        basicVersion ->
                        appendLine("Basic Version: ${basicVersion.major}.${basicVersion.minor}")
                    }

                    requestSpecVersionResponse.specificationVersion?.desOptionVersion?.let {
                        desVersion ->
                        appendLine("DES Option Version: ${desVersion.major}.${desVersion.minor}")
                    }

                    requestSpecVersionResponse.specificationVersion?.specialOptionVersion?.let {
                        specialVersion ->
                        appendLine(
                            "Special Option Version: ${specialVersion.major}.${specialVersion.minor}"
                        )
                    }

                    requestSpecVersionResponse.specificationVersion
                        ?.extendedOverlapOptionVersion
                        ?.let { extendedOverlapVersion ->
                            appendLine(
                                "Extended Overlap Option Version: ${extendedOverlapVersion.major}.${extendedOverlapVersion.minor}"
                            )
                        }

                    requestSpecVersionResponse.specificationVersion
                        ?.valueLimitedPurseServiceOptionVersion
                        ?.let { valueLimitedPurseVersion ->
                            appendLine(
                                "Value-Limited Purse Service Option Version: ${valueLimitedPurseVersion.major}.${valueLimitedPurseVersion.minor}"
                            )
                        }

                    requestSpecVersionResponse.specificationVersion
                        ?.communicationWithMacOptionVersion
                        ?.let { communicationWithMacVersion ->
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
            throw RuntimeException(
                "No systems have been discovered. Please run system discovery first."
            )
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

        return buildString {
                appendLine("System Status Information:")
                appendLine("Processed ${scanContext.systemScanContexts.size} system(s)")
                appendLine()
                results.forEach { result -> appendLine(result) }
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
                handleDiscoveredSystemCodes(listOf(parsedSystemCodeResponse.systemCode), target)

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
            throw RuntimeException("Polling: System Code: Not available")
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
                    appendLine("848 kbps: ${if (commPerf.supports848kbps) "✓" else "✗"} (reserved)")
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
            throw RuntimeException("Polling: Communication Performance: Not available")
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
                        "RequestCodeList error at index $index for system ${systemContext.systemCode?.toHexString()}: ${formatStatus(requestCodeListResponse)}",
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
                                "  - Service ${service.code.toHexString()}: ${service.attribute::class.simpleName}"
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
        var supportedSystems = 0
        var unsupportedSystems = 0
        var populatedKnownFallbacks = 0

        // Process each known system context separately, probing support first
        for (systemContext in scanContext.systemScanContexts) {
            // Perform system-specific polling before probing/scanning
            pollSystemCode(target, systemContext.systemCode)

            val systemCodeHex = systemContext.systemCode?.toHexString()

            // Probe: try a single request at index 0 to detect support
            val isSupported =
                try {
                    val probe = SearchServiceCodeCommand(target.idm, 0)
                    target.transceive(probe)
                    true
                } catch (e: Exception) {
                    false
                }

            if (isSupported) {
                supportedSystems++
                val nodeArray = mutableListOf<Node>()

                // Iterate through service codes until a System node or termination
                for (index in 0x0000..SearchServiceCodeCommand.MAX_ITERATOR_INDEX) {
                    val searchServiceCodeCommand = SearchServiceCodeCommand(target.idm, index)
                    val parsedSearchResponse = target.transceive(searchServiceCodeCommand)

                    val node = parsedSearchResponse.node
                    if (node != null) {
                        nodeArray.add(node)

                        if (node is System) {
                            Log.d(
                                "CardScanService",
                                "Found system node at index $index for system ${systemCodeHex}, stopping iteration",
                            )
                            break
                        }
                    } else {
                        Log.d(
                            "CardScanService",
                            "No node found at index $index for system ${systemCodeHex}, stopping iteration",
                        )
                        break
                    }
                }

                allDiscoveredNodes.addAll(nodeArray)
                systemContextsToUpdate.add(systemContext.copy(nodes = nodeArray))
            } else {
                unsupportedSystems++
                // Try to populate known services for specific system codes
                val knownNodes = knownNodesForSystemCode(systemContext.systemCode)
                if (knownNodes.isNotEmpty()) {
                    populatedKnownFallbacks++
                }
                allDiscoveredNodes.addAll(knownNodes)
                // Mark these nodes as registry-populated so they can be filtered later
                systemContextsToUpdate.add(
                    systemContext.copy(
                        nodes = knownNodes,
                        registryPopulatedNodes = knownNodes.toSet(),
                    )
                )
            }
        }

        // Handle case where no system contexts exist yet (legacy fallback): probe globally
        if (scanContext.systemScanContexts.isEmpty()) {
            val nodeArray = mutableListOf<Node>()
            for (index in 0x0000..SearchServiceCodeCommand.MAX_ITERATOR_INDEX) {
                val searchServiceCodeCommand = SearchServiceCodeCommand(target.idm, index)
                val parsedSearchResponse = target.transceive(searchServiceCodeCommand)
                val node = parsedSearchResponse.node
                if (node != null) {
                    nodeArray.add(node)
                    if (node is System) {
                        Log.d(
                            "CardScanService",
                            "Found system node at index $index, stopping iteration",
                        )
                        break
                    }
                } else {
                    Log.d("CardScanService", "No node found at index $index, stopping iteration")
                    break
                }
            }
            allDiscoveredNodes.addAll(nodeArray)
            val systemContext =
                SystemScanContext(
                    systemCode = scanContext.primarySystemCode,
                    nodes = nodeArray,
                    idm = target.idm,
                )
            systemContextsToUpdate.add(systemContext)
            supportedSystems = if (nodeArray.isNotEmpty()) 1 else 0
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = systemContextsToUpdate)

        // If no systems supported the command, raise
        if (supportedSystems == 0) {
            throw RuntimeException(
                "Search Service Code unsupported on all systems; populated known services for ${populatedKnownFallbacks} system(s) where applicable"
            )
        }

        // Calculate statistics across all discovered nodes
        val areas = allDiscoveredNodes.filterIsInstance<Area>()
        val services = allDiscoveredNodes.filterIsInstance<Service>()
        val systems = allDiscoveredNodes.filterIsInstance<System>()

        // Collapsed view - summary
        val collapsedResult =
            "Found ${areas.size} areas, ${services.size} services across ${systemContextsToUpdate.size} system(s); supported: ${supportedSystems}, unsupported: ${unsupportedSystems}${if (populatedKnownFallbacks > 0) ", fallback populated: ${populatedKnownFallbacks}" else ""}"

        // Expanded view - detailed list
        val expandedResult =
            buildString {
                    appendLine(
                        "Discovered/Populated Nodes across ${systemContextsToUpdate.size} system(s):"
                    )
                    appendLine(
                        "Supported systems: ${supportedSystems}, Unsupported systems: ${unsupportedSystems}${if (populatedKnownFallbacks > 0) ", Fallback populated: ${populatedKnownFallbacks}" else ""}"
                    )
                    appendLine()

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
                        if (contextAreas.isEmpty()) appendLine("    - None")

                        appendLine(" Services (${contextServices.size}):")
                        contextServices.forEach { service ->
                            appendLine(
                                "  - Service ${service.code.toHexString()}: ${service.attribute::class.simpleName}"
                            )
                        }
                        if (contextServices.isEmpty()) appendLine("    - None")

                        appendLine("  Systems (${contextSystems.size}):")
                        contextSystems.forEach { system ->
                            appendLine("    - System ${system.code.toHexString()}")
                        }
                        if (contextSystems.isEmpty()) appendLine("    - None")
                        appendLine()
                    }

                    appendLine("Total Summary:")
                    appendLine(
                        "Areas: ${areas.size}, Services: ${services.size}, Systems: ${systems.size}"
                    )
                }
                .trim()

        return collapsedResult to expandedResult
    }

    private fun knownNodesForSystemCode(systemCode: ByteArray?): List<Node> {
        val hex = systemCode?.toHexString()?.uppercase() ?: return emptyList()

        // Check if NodeRegistry has data for this system code
        if (NodeRegistry.isReady() && NodeRegistry.isSystemCodeKnown(hex)) {
            val nodeDefinitions = NodeRegistry.getNodesForSystemCode(hex)
            if (nodeDefinitions.isNotEmpty()) {
                val nodes = mutableListOf<Node>()
                for (definition in nodeDefinitions) {
                    val node =
                        when (definition.type) {
                            NodeDefinitionType.AREA -> Area.fromHexString(definition.code)
                            NodeDefinitionType.SERVICE -> Service.fromHexString(definition.code)
                            else -> null
                        }
                    // Skip root area as it will be added at the beginning
                    if (node != null && !(node is Area && node.isRoot)) {
                        nodes.add(node)
                    }
                }
                // Sort nodes by code (excluding System which will be prepended)
                nodes.sortBy { it.number }
                // Prepend System and root Area at the beginning
                return listOf(System, Area.ROOT) + nodes
            }
        }

        // Fallback: for unknown system codes, return System and root Area
        return listOf(System, Area.ROOT)
    }

    private suspend fun executeRequestService(target: FeliCaTarget): Pair<String, String> {
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
            // Filter out registry-populated nodes that don't actually exist on the card
            // Only filter nodes that were populated from registry, keep discovered nodes as-is
            // Always keep System and root Area nodes as they are structural
            val filteredNodes =
                systemNodes.filter { node ->
                    // Always keep System and root Area nodes
                    if (node is System || (node is Area && node.isRoot)) {
                        return@filter true
                    }
                    val isRegistryPopulated = systemContext.registryPopulatedNodes.contains(node)
                    if (isRegistryPopulated) {
                        // Registry nodes must have a key version to be kept
                        nodeKeyVersionsMap.containsKey(node)
                    } else {
                        // Discovered nodes are always kept
                        true
                    }
                }

            // Update context with key version data and filtered nodes
            val updatedSystemContext =
                systemContext.copy(nodes = filteredNodes, nodeKeyVersions = nodeKeyVersionsMap)
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

    private suspend fun executeRequestServiceUnknownNodeAttributes(target: FeliCaTarget): String {
        if (scanContext.requestServiceSupport != CommandSupport.SUPPORTED) {
            throw PrerequisiteException(REQUEST_SERVICE_UNAVAILABLE_FOR_UNKNOWN_ATTRIBUTE_PROBE)
        }

        val systemContext = scanContext.systemScanContexts.firstOrNull()
        val unknownAttributeValue = resolveUnknownNodeAttributeValue()
        val unknownAttributeNodeCodeValue =
            (REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER shl 6) or unknownAttributeValue
        val unknownAttributeNodeCode =
            byteArrayOf(
                (unknownAttributeNodeCodeValue and 0xFF).toByte(),
                ((unknownAttributeNodeCodeValue shr 8) and 0xFF).toByte(),
            )

        val attemptResults = mutableListOf<String>()
        var responseReceived = false

        for (attempt in 1..REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS) {
            try {
                pollSystemCode(target, systemContext?.systemCode)
                val response =
                    target.transceive(
                        RequestServiceCommand(target.idm, arrayOf(unknownAttributeNodeCode))
                    )
                val keyVersionHex =
                    response.keyVersions.first().toByteArray().toHexString().uppercase()
                attemptResults += "$attempt. response received (key_version=$keyVersionHex)"
                responseReceived = true
                break
            } catch (e: Exception) {
                val error = e.message ?: e::class.simpleName ?: "Unknown error"
                attemptResults += "$attempt. no response ($error)"
                if (attempt < REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS) {
                    delay(50)
                }
            }
        }

        scanContext =
            scanContext.copy(requestServiceUnknownNodeAttributesSupported = responseReceived)

        return buildString {
                appendLine(
                    "Probe node: ${unknownAttributeNodeCode.toHexString().uppercase()} " +
                        "(service=${REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER}, " +
                        "attribute=0x${unknownAttributeValue.toString(16).uppercase().padStart(2, '0')})"
                )
                appendLine(
                    "System: ${systemContext?.systemCode?.toHexString()?.uppercase() ?: "wildcard"}"
                )
                appendLine("Attempts:")
                attemptResults.forEach { appendLine("  $it") }
                appendLine("Supported = $responseReceived")
            }
            .trim()
    }

    private suspend fun executeAuthentication1DesNodeListHierarchyValidation(
        target: FeliCaTarget
    ): String {
        if (scanContext.authentication1DesSupport != CommandSupport.SUPPORTED) {
            throw PrerequisiteException(
                AUTHENTICATION1_DES_UNAVAILABLE_FOR_NODE_LIST_HIERARCHY_VALIDATION
            )
        }

        val preferredTarget =
            findBestAuthentication1DesTarget { mode ->
                mode == Mode.Mode0
            }
        val targetForErrorMessage = preferredTarget ?: findBestAuthentication1DesTarget()

        if (targetForErrorMessage == null) {
            throw PrerequisiteException(
                "No suitable system found for Authenticate1 DES node-list hierarchy validation (root area with valid DES key is required)."
            )
        }

        if (preferredTarget == null) {
            throw PrerequisiteException(
                "Authenticate1 DES node-list hierarchy validation requires Mode0 on the selected system (current: ${targetForErrorMessage.systemContext.mode})."
            )
        }

        val nonImmediateNode = findAuthentication1DesNonImmediateNode(preferredTarget)
        if (nonImmediateNode == null) {
            throw PrerequisiteException(
                "No node found under an area under root area; cannot check Authenticate1 DES node-list hierarchy validation."
            )
        }

        val challenge1A = ByteArray(8) { 0x00.toByte() }
        val areasToAuth = listOf(preferredTarget.rootArea)
        // Area0 may appear in both lists: this is allowed because key updates can target areas.
        val nodesToAuth = listOf<Node>(preferredTarget.rootArea, nonImmediateNode)

        val attemptResults = mutableListOf<String>()
        var responseReceived = false
        var selectedSystemIdmUsed: ByteArray? = null
        var responseChallenge1BHex: String? = null
        var responseChallenge2AHex: String? = null

        for (attempt in 1..AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS) {
            try {
                pollSystemCode(target, preferredTarget.systemContext.systemCode)
                val currentSystemContext =
                    scanContext.systemScanContexts.firstOrNull { context ->
                        context.systemCode.sameBytes(preferredTarget.systemContext.systemCode)
                    }
                val selectedSystemIdm = currentSystemContext?.idm ?: target.idm
                selectedSystemIdmUsed = selectedSystemIdm

                val response =
                    target.transceive(
                        Authentication1DesCommand(
                            idm = selectedSystemIdm,
                            areaNodes = areasToAuth,
                            nodes = nodesToAuth,
                            challenge1A = challenge1A,
                        )
                    )

                responseChallenge1BHex = response.challenge1B.toHexString().uppercase()
                responseChallenge2AHex = response.challenge2A.toHexString().uppercase()
                responseReceived = true
                setSystemMode(preferredTarget.systemContext.systemCode, Mode.Mode1.Des)
                attemptResults +=
                    "$attempt. response received (challenge1B=$responseChallenge1BHex, challenge2A=$responseChallenge2AHex)"
                break
            } catch (e: Exception) {
                val error = e.message ?: e::class.simpleName ?: "Unknown error"
                attemptResults += "$attempt. no response ($error)"
                if (attempt < AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS) {
                    delay(50)
                }
            }
        }

        val resetStateResult =
            if (responseReceived) {
                resetAuthenticationState(
                        target = target,
                        authenticatedSystemCode = preferredTarget.systemContext.systemCode,
                        authenticatedSystemIdm = selectedSystemIdmUsed,
                    )
                    .message
            } else {
                null
            }

        val validationBehavior =
            if (responseReceived) {
                Authentication1DesNodeListHierarchyValidation.LENIENT
            } else {
                Authentication1DesNodeListHierarchyValidation.STRICT
            }
        scanContext =
            scanContext.copy(
                authentication1DesNodeListHierarchyValidation = validationBehavior
            )

        return buildString {
                appendLine("Authenticate1 DES node-list validation check:")
                appendLine(
                    "System: ${preferredTarget.systemContext.systemCode?.toHexString()?.uppercase() ?: "unknown"}"
                )
                appendLine("Mode before check: ${preferredTarget.systemContext.mode}")
                appendLine("Area list:")
                appendLine("  1. ${describeNodeForAuthenticationCheck(preferredTarget.rootArea)}")
                appendLine("Node list:")
                appendLine("  1. ${describeNodeForAuthenticationCheck(preferredTarget.rootArea)}")
                appendLine("  2. ${describeNodeForAuthenticationCheck(nonImmediateNode)}")
                appendLine("Challenge1A: ${challenge1A.toHexString().uppercase()}")
                appendLine("Attempts:")
                attemptResults.forEach { appendLine("  $it") }
                appendLine("Validation behavior: $validationBehavior")
                resetStateResult?.let {
                    appendLine()
                    appendLine("$it")
                }
            }
            .trim()
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
                    val encId = requestServiceV2Response.encryptionIdentifier
                    val supportsAes = encId?.aesKeyType != AesKeyType.NONE
                    val supportsDes = encId?.desKeyType != DesKeyType.NONE

                    nodeBatch.forEachIndexed { index, node ->
                        val aesKeyVersion = requestServiceV2Response.aesKeyVersions.getOrNull(index)
                        if (aesKeyVersion == null && supportsAes) {
                            throw RuntimeException(
                                "AES key version missing for node at index $index"
                            )
                        }
                        val desKeyVersion = requestServiceV2Response.desKeyVersions.getOrNull(index)
                        if (desKeyVersion == null && supportsDes) {
                            throw RuntimeException(
                                "DES key version missing for node at index $index"
                            )
                        }
                        val aesExists = aesKeyVersion?.isMissing == false
                        val desExists = desKeyVersion?.isMissing == false

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
                            if (aesExists) "AES: ${aesKeyVersion?.toInt()}" else "AES: N/A"
                        val desStatus =
                            if (desExists) {
                                "DES: ${desKeyVersion?.toInt()}"
                            } else "DES: Not supported"

                        keyVersionResults.add(
                            "${systemContext.systemCode?.toHexString() ?: "unknown"} - $nodeType $nodeCodeHex$encryptionInfo: $aesStatus, $desStatus"
                        )

                        // Store key versions in maps
                        if (aesExists && aesKeyVersion != null) {
                            nodeAesKeyVersionsMap[node] = aesKeyVersion
                        }
                        // Only store DES key version if it exists and the node supports DES
                        if (desExists && desKeyVersion != null) {
                            nodeDesKeyVersionsMap[node] = desKeyVersion
                        }
                    }
                } else {
                    keyVersionResults.add(
                        "${systemContext.systemCode?.toHexString() ?: "unknown"} - Batch ${batchIndex + 1}: Error - Status: ${requestServiceV2Response.statusFlag1.toInt() and 0xFF}"
                    )
                }
            }

            // Filter out registry-populated nodes that don't actually exist on the card
            // Only filter nodes that were populated from registry, keep discovered nodes as-is
            // Always keep System and root Area nodes as they are structural
            val filteredNodes =
                nodes.filter { node ->
                    // Always keep System and root Area nodes
                    if (node is System || (node is Area && node.isRoot)) {
                        return@filter true
                    }
                    val isRegistryPopulated = systemContext.registryPopulatedNodes.contains(node)
                    if (isRegistryPopulated) {
                        // Registry nodes must have either AES or DES key version to be kept
                        nodeAesKeyVersionsMap.containsKey(node) ||
                            nodeDesKeyVersionsMap.containsKey(node)
                    } else {
                        // Discovered nodes are always kept
                        true
                    }
                }

            // Update context with key version data and filtered nodes
            val updatedSystemContext =
                systemContext.copy(
                    nodes = filteredNodes,
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

    /**
     * Force discover all nodes by iterating through all possible node codes (0-1023) with all known
     * service and area attributes. Uses RequestServiceV2 if available, otherwise RequestService.
     * Nodes discovered this way that were not found in regular discovery are marked as hidden.
     */
    private suspend fun executeForceDiscoverNodes(target: FeliCaTarget): Pair<String, String> {
        // Check if RequestService commands are supported
        val requestServiceV2Supported =
            scanContext.requestServiceV2Support == CommandSupport.SUPPORTED
        val requestServiceSupported = scanContext.requestServiceSupport == CommandSupport.SUPPORTED

        if (!requestServiceV2Supported && !requestServiceSupported) {
            throw RuntimeException(
                "Force discover requires RequestService or RequestServiceV2 to be supported"
            )
        }

        val useV2 = requestServiceV2Supported
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()

        // All known service attributes to probe
        val serviceAttributes = ServiceAttribute.entries
        // Area attributes to probe - exclude end markers (EndRootArea, EndSubArea),
        // as they are not valid start attributes
        val areaAttributes =
            AreaAttribute.entries.filter {
                it != AreaAttribute.EndRootArea && it != AreaAttribute.EndSubArea
            }
        val maxNodeNumber = 1023 // Node codes range from 0 to 1023 (10 bits)
        val batchSize = 32 // Max nodes per request

        var totalDiscovered = 0
        var totalHidden = 0
        var totalHiddenServices = 0
        var totalHiddenAreas = 0

        // Process each system context
        for (systemContext in scanContext.systemScanContexts) {
            // Perform system-specific polling before executing commands
            pollSystemCode(target, systemContext.systemCode)

            val existingNodes = systemContext.nodes.toSet()
            val existingNodeCodes = existingNodes.map { it.code.toHexString().uppercase() }.toSet()
            val newlyDiscoveredNodes = mutableListOf<Node>()
            val hiddenNodesSet = mutableSetOf<Node>()

            // Track key versions for discovered nodes
            val newNodeKeyVersions = mutableMapOf<Node, KeyVersion>()
            val newNodeAesKeyVersions = mutableMapOf<Node, KeyVersion>()
            val newNodeDesKeyVersions = mutableMapOf<Node, KeyVersion>()

            // Generate all possible nodes to probe (services and areas),
            // skipping codes that were already discovered by normal scanning
            // For areas, we create minimal areas where endNumber equals number
            // since we don't know the actual end code from RequestService
            val nodesToProbe =
                (0..maxNodeNumber)
                    .flatMap { nodeNumber ->
                        areaAttributes.map { attribute ->
                            Area(
                                number = nodeNumber,
                                attribute = attribute,
                                endNumber = nodeNumber,
                                endAttribute = AreaAttribute.EndSubArea,
                            )
                        } + serviceAttributes.map { attribute -> Service(nodeNumber, attribute) }
                    }
                    .filter { it.code.toHexString().uppercase() !in existingNodeCodes }

            // Normalised per-slot result: the key version to check for presence,
            // a display label, and a store action that writes into the correct maps.
            data class SlotResult(
                val keyVersion: KeyVersion,
                val keyLabel: String,
                val store: (Node) -> Unit,
            )

            // Process in batches
            nodesToProbe.chunked(batchSize).forEach { batch ->
                try {
                    val nodeCodes = batch.map { it.code }.toTypedArray()

                    val slots: List<SlotResult> =
                        if (useV2) {
                            val response =
                                target.transceive(RequestServiceV2Command(target.idm, nodeCodes))
                            if (!response.isStatusSuccessful) return@forEach
                            batch.indices.map { i ->
                                val aes = response.aesKeyVersions[i]
                                val des = response.desKeyVersions[i]
                                SlotResult(aes, "AES") { node ->
                                    newNodeAesKeyVersions[node] = aes
                                    if (!des.isMissing) newNodeDesKeyVersions[node] = des
                                }
                            }
                        } else {
                            val response =
                                target.transceive(RequestServiceCommand(target.idm, nodeCodes))
                            batch.indices.map { i ->
                                val kv = response.keyVersions[i]
                                SlotResult(kv, "Key") { node -> newNodeKeyVersions[node] = kv }
                            }
                        }

                    batch.forEachIndexed { index, node ->
                        val (keyVersion, keyLabel, store) = slots[index]
                        // If key version is not FFFF, the node exists
                        if (!keyVersion.isMissing) {
                            val codeHex = node.code.toHexString().uppercase()
                            store(node)
                            if (!existingNodeCodes.contains(codeHex)) {
                                // This is a hidden node
                                newlyDiscoveredNodes.add(node)
                                hiddenNodesSet.add(node)
                                totalHidden++
                                when (node) {
                                    is Service -> {
                                        totalHiddenServices++
                                        results.add(
                                            "${systemContext.systemCode?.toHexString() ?: "unknown"} - Hidden Service $codeHex: $keyLabel v${keyVersion.toInt()}"
                                        )
                                    }
                                    is Area -> {
                                        totalHiddenAreas++
                                        results.add(
                                            "${systemContext.systemCode?.toHexString() ?: "unknown"} - Hidden Area $codeHex: $keyLabel v${keyVersion.toInt()}"
                                        )
                                    }
                                    else -> {}
                                }
                            }
                            totalDiscovered++
                        }
                    }
                } catch (e: Exception) {
                    // Log error but continue with next batch
                    Log.w("CardScanService", "Force discover batch failed: ${e.message}")
                }
            }

            // Merge newly discovered nodes with existing nodes
            val allNodes = systemContext.nodes + newlyDiscoveredNodes
            val mergedHiddenNodes = systemContext.hiddenNodes + hiddenNodesSet

            // Merge key versions (existing + newly discovered)
            val mergedNodeKeyVersions = systemContext.nodeKeyVersions + newNodeKeyVersions
            val mergedNodeAesKeyVersions = systemContext.nodeAesKeyVersions + newNodeAesKeyVersions
            val mergedNodeDesKeyVersions = systemContext.nodeDesKeyVersions + newNodeDesKeyVersions

            val updatedSystemContext =
                systemContext.copy(
                    nodes = allNodes,
                    hiddenNodes = mergedHiddenNodes,
                    nodeKeyVersions = mergedNodeKeyVersions,
                    nodeAesKeyVersions = mergedNodeAesKeyVersions,
                    nodeDesKeyVersions = mergedNodeDesKeyVersions,
                )
            updatedSystemContexts.add(updatedSystemContext)
        }

        // Update scan context with all system contexts
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        val collapsedSummary =
            "Force discovered $totalHidden hidden node(s) ($totalHiddenAreas areas, $totalHiddenServices services) out of $totalDiscovered total present"
        val expandedResult =
            if (results.isNotEmpty()) {
                collapsedSummary + "\n" + results.joinToString("\n")
            } else {
                collapsedSummary + "\nNo hidden nodes found"
            }

        return collapsedSummary to expandedResult
    }

    private suspend fun executeGetAreaInformation(target: FeliCaTarget): String {
        val allAreas = scanContext.systemScanContexts.flatMap { it.nodes.filterIsInstance<Area>() }

        if (allAreas.isEmpty()) {
            throw RuntimeException(
                "No areas discovered. Get Area Information requires discovered areas from Search Service Codes or Request Code List steps."
            )
        }

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
                        val status1Hex =
                            getAreaInformationResponse.statusFlag1
                                .toUByte()
                                .toString(16)
                                .uppercase()
                                .padStart(2, '0')
                        val status2Hex =
                            getAreaInformationResponse.statusFlag2
                                .toUByte()
                                .toString(16)
                                .uppercase()
                                .padStart(2, '0')
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

        return buildString {
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
    }

    private suspend fun executeGetContainerProperty(target: FeliCaTarget): String {
        val results = mutableListOf<String>()
        val containerPropertyValues =
            mutableMapOf<GetContainerPropertyCommand.Property, ByteArray>()

        // Test both known property types
        val propertiesToTest =
            listOf(
                GetContainerPropertyCommand.Property.PROPERTY_1,
                GetContainerPropertyCommand.Property.PROPERTY_2,
            )

        var successfulCommands = 0

        propertiesToTest.forEach { property ->
            val getContainerPropertyCommand = GetContainerPropertyCommand(property)
            val getContainerPropertyResponse = target.transceive(getContainerPropertyCommand)

            // Store the property value in the map using Property object as key
            containerPropertyValues[property] = getContainerPropertyResponse.data

            successfulCommands++
            results.add(
                buildString {
                    appendLine(
                        "Property ${property.name} (index 0x${property.index.toString(16).uppercase().padStart(2, '0')}):"
                    )
                    appendLine("  Command: SUCCESS")
                    appendLine(
                        "  Response Data: ${getContainerPropertyResponse.data.toHexString()}"
                    )
                    appendLine("  Data Size: ${getContainerPropertyResponse.data.size} bytes")
                }
            )
        }

        // Store container property values in scan context
        scanContext = scanContext.copy(containerPropertyValues = containerPropertyValues.toMap())

        return buildString {
                appendLine(
                    "Get Container Property Results: $successfulCommands/${propertiesToTest.size} properties retrieved"
                )
                appendLine()
                results.forEach { result -> appendLine(result.trimEnd()) }
            }
            .trim()
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
                "Set Parameter command failed with ${formatStatus(setParameterResponse1)}"
            )
        }

        results.add(
            buildString {
                appendLine("Set Parameter Test 1 (SRM_TYPE1, NODECODESIZE_2):")
                appendLine("  Response IDM: ${setParameterResponse1.idm.toHexString()}")
                appendLine("  Status: ${formatStatus(setParameterResponse1)}")
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
                    "Set Parameter Test 2 failed with ${formatStatus(setParameterResponse2)}"
                )
            }

            results.add(
                buildString {
                    appendLine("Set Parameter Test 2 (SRM_TYPE2, NODECODESIZE_4):")
                    appendLine("  Response IDM: ${setParameterResponse2.idm.toHexString()}")
                    appendLine("  Status: ${formatStatus(setParameterResponse2)}")
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

    private suspend fun executeGetPlatformInformation(target: FeliCaTarget): String {
        val getPlatformInformationCommand = GetPlatformInformationCommand(target.idm)
        val getPlatformInformationResponse = target.transceive(getPlatformInformationCommand)

        // Store secure element information in context
        scanContext = scanContext.copy(platformInformation = getPlatformInformationResponse)

        return buildString {
                appendLine(
                    "Status Flags: ${formatStatus(getPlatformInformationResponse, prefix = "")}"
                )

                if (getPlatformInformationResponse.isStatusSuccessful) {
                    appendLine(
                        "Platform information: ${getPlatformInformationResponse.platformInformationData.toHexString()}"
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
        if (resetModeResponse.isStatusSuccessful) {
            setSystemModeByIdm(resetModeResponse.idm, Mode.Mode0)
        }

        return buildString {
                appendLine("Status Flags: ${formatStatus(resetModeResponse, prefix = "")}")

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
        data class EchoAttemptResult(val length: Int, val success: Boolean, val error: String?)

        suspend fun attemptEcho(length: Int): EchoAttemptResult {
            val payload = ByteArray(length) { index -> (index and 0xFF).toByte() }
            val command = EchoCommand(payload)
            return try {
                val response = target.transceive(command)
                if (response.data.contentEquals(payload)) {
                    EchoAttemptResult(length, true, null)
                } else {
                    EchoAttemptResult(
                        length,
                        false,
                        "Echo mismatch (${response.data.size} bytes returned)",
                    )
                }
            } catch (e: Exception) {
                EchoAttemptResult(length, false, e.message ?: "Unknown error")
            }
        }

        fun formatResult(maxSupported: Int, attempts: List<EchoAttemptResult>): String {
            return buildString {
                    appendLine("Max echo payload: $maxSupported bytes")
                    appendLine("Attempts (${attempts.size}):")
                    attempts.forEachIndexed { index, attempt ->
                        val status =
                            if (attempt.success) {
                                "success"
                            } else {
                                "failure${attempt.error?.let { ": $it" } ?: ""}"
                            }
                        appendLine("  ${index + 1}. ${attempt.length} bytes -> $status")
                    }
                }
                .trim()
        }

        val minLength = 0
        val maxLength = 252 // 255 - 1 length byte - 2 command bytes (F000)
        val attempts = mutableListOf<EchoAttemptResult>()

        val baselineAttempt = attemptEcho(minLength)
        attempts += baselineAttempt
        if (!baselineAttempt.success) {
            val reason = baselineAttempt.error?.let { ": $it" } ?: ""
            throw RuntimeException("Echo command failed even at $minLength bytes$reason")
        }

        // Try the common maximum size first to minimize attempts on typical cards
        val maxAttempt = attemptEcho(maxLength)
        attempts += maxAttempt
        if (maxAttempt.success) {
            scanContext = scanContext.copy(echoMaxPayloadSize = maxLength)
            return formatResult(maxLength, attempts)
        }

        var lowerBound = minLength
        var upperBound = maxLength
        var bestLength = minLength

        while ((upperBound - lowerBound) > 1) {
            val candidate = (lowerBound + upperBound) / 2
            val attempt = attemptEcho(candidate)
            attempts += attempt
            if (attempt.success) {
                lowerBound = candidate
                bestLength = maxOf(bestLength, candidate)
            } else {
                upperBound = candidate
            }
        }

        scanContext = scanContext.copy(echoMaxPayloadSize = bestLength)
        return formatResult(bestLength, attempts)
    }

    private suspend fun executeInternalAuthenticateAndRead(target: FeliCaTarget): String {
        // Find a system with at least one service that has MAC communication enabled
        var bestSystemContext: SystemScanContext? = null
        var bestMacService: Service? = null

        for (systemContext in scanContext.systemScanContexts) {
            val macProperties = systemContext.nodeMacCommunicationProperties
            if (macProperties.isEmpty()) {
                continue
            }

            // Find services with MAC communication enabled
            val services = systemContext.nodes.filterIsInstance<Service>()
            for (service in services) {
                val macProperty = macProperties[service]
                if (macProperty?.enabled == true) {
                    bestSystemContext = systemContext
                    bestMacService = service
                    break
                }
            }
            if (bestMacService != null) break
        }

        if (bestSystemContext == null || bestMacService == null) {
            throw PrerequisiteException(
                "No services with MAC communication enabled found. " +
                    "Internal Authenticate and Read requires at least one service with MAC enabled."
            )
        }

        val systemCodeHex = bestSystemContext.systemCode?.toHexString() ?: "unknown"
        val serviceCodeHex = bestMacService.code.toHexString()

        Log.d(
            "CardScanService",
            "Selected system $systemCodeHex, service $serviceCodeHex for Internal Authenticate and Read",
        )

        // Poll the selected system before the command
        pollSystemCode(target, bestSystemContext.systemCode)
        val selectedSystemContext =
            scanContext.systemScanContexts.firstOrNull { context ->
                context.systemCode.sameBytes(bestSystemContext.systemCode)
            }
        val selectedSystemIdm = selectedSystemContext?.idm ?: target.idm

        // Generate a 16-byte challenge
        val challenge = ByteArray(16) { 0x00 }

        // Create block list element for block 0 of the service
        val blockListElement = BlockListElement(serviceCodeListOrder = 0, blockNumber = 0)

        val command =
            InternalAuthenticateAndReadCommand(
                idm = selectedSystemIdm,
                serviceCodes = arrayOf(bestMacService.code),
                blockListElements = arrayOf(blockListElement),
                challenge = challenge,
            )

        return try {
            val response = target.transceive(command)
            if (response.isStatusSuccessful) {
                setSystemMode(bestSystemContext.systemCode, Mode.Mode1.AesMac)
            }

            val resetResult =
                resetAuthenticationState(
                    target = target,
                    authenticatedSystemCode = bestSystemContext.systemCode,
                    authenticatedSystemIdm = selectedSystemIdm,
                )
            val resetModeResult = resetResult.message

            if (response.isStatusSuccessful) {
                buildString {
                        appendLine("Internal Authenticate and Read Results:")
                        appendLine("System: $systemCodeHex")
                        appendLine("Service: $serviceCodeHex (${bestMacService.attribute})")
                        appendLine("Challenge sent: ${challenge.toHexString()}")
                        appendLine("Status: Success")
                        appendLine("Blocks returned: ${response.blockData.size}")
                        response.blockData.forEachIndexed { index, block ->
                            appendLine("  Block $index: ${block.toHexString()}")
                        }
                        appendLine("Challenge response: ${response.challenge.toHexString()}")
                        appendLine("MAC: ${response.mac.toHexString()}")
                        appendLine()
                        appendLine("Reset Mode:")
                        appendLine("  $resetModeResult")
                    }
                    .trim()
            } else {
                buildString {
                        appendLine("Internal Authenticate and Read Results:")
                        appendLine("System: $systemCodeHex")
                        appendLine("Service: $serviceCodeHex (${bestMacService.attribute})")
                        appendLine("Challenge sent: ${challenge.toHexString()}")
                        appendLine("Status: Failed (${formatStatus(response)})")
                        appendLine()
                        appendLine("Reset Mode:")
                        appendLine("  $resetModeResult")
                    }
                    .trim()
            }
        } catch (e: Exception) {
            throw RuntimeException(
                "Internal Authenticate and Read failed for service $serviceCodeHex: ${e.message}"
            )
        }
    }

    private suspend fun executeReadWithoutEncryptionDetermineErrorIndication(
        target: FeliCaTarget
    ): String {
        val testTarget = findReadWithoutEncryptionTestTarget()

        // Switch to the best system for testing
        pollSystemCode(target, testTarget.systemContext.systemCode)
        val testService = testTarget.service
        val testBlockNumber = testTarget.blockNumber

        val blocksToRead =
            listOf(
                BlockListElement(serviceCodeListOrder = 0, blockNumber = testBlockNumber),
                BlockListElement(serviceCodeListOrder = 0, blockNumber = testBlockNumber),
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
        val fallbackType = ErrorLocationIndication.FLAG

        if (response.isStatusSuccessful) {
            val fallbackMessage =
                "Error indication fallback to ${fallbackType.name}: unexpected successful status (${formatStatus(response)})"

            scanContext =
                scanContext.copy(readWithoutEncryptionErrorLocationIndication = fallbackType)

            throwReadWithoutEncryptionProbeFallback(fallbackMessage)
        }

        if ((statusFlag2.toInt() and 0xFF) != 0xA8) {
            val fallbackMessage =
                "Error indication fallback to ${fallbackType.name}: unexpected status (${formatStatus(response)})"
            Log.w("CardScanService", fallbackMessage)

            scanContext =
                scanContext.copy(readWithoutEncryptionErrorLocationIndication = fallbackType)

            throwReadWithoutEncryptionProbeFallback(fallbackMessage)
        }

        var usedFallback = false
        var fallbackMessage: String? = null
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
                    usedFallback = true
                    fallbackMessage =
                        "Error indication fallback to ${fallbackType.name}: unexpected status (${formatStatus(response)})"
                    fallbackType
                }
            }

        // Update scan context with determined error indication type
        scanContext =
            scanContext.copy(
                readWithoutEncryptionErrorLocationIndication = errorIndicationType,
                readBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED,
            )

        if (usedFallback) {
            throwReadWithoutEncryptionProbeFallback(
                fallbackMessage ?: "Error indication fallback to ${fallbackType.name}"
            )
        }

        Log.d("CardScanService", "Determined error indication type: $errorIndicationType")

        return buildString {
                appendLine(
                    "Error indication type: ${errorIndicationType.name} (${formatStatus(response)})"
                )
            }
            .trim()
    }

    private suspend fun executeReadWithoutEncryptionDetermineMaxServices(
        target: FeliCaTarget
    ): String {
        val testTarget = findReadWithoutEncryptionTestTarget()

        // Switch to the best system for testing
        pollSystemCode(target, testTarget.systemContext.systemCode)
        val testService = testTarget.service
        val testBlockNumber = testTarget.blockNumber

        // Start with theoretical maximum and work down
        var maxServices =
            ReadWithoutEncryptionCommand
                .MAX_SERVICE_CODES // FeliCa specification limit for service codes
        var usedFallback = false
        var fallbackStatus1: Byte? = null
        var fallbackStatus2: Byte? = null
        var observedIllegalNumberPreference: IllegalNumberErrorPreference? = null

        while (maxServices > 0) {
            // Create array of the same service code repeated maxServices times
            val serviceCodes = Array(maxServices) { testService.code }
            // Create block list elements for block 0, one for each service
            val blockListElements =
                Array(maxServices) { serviceIndex ->
                    BlockListElement(
                        serviceCodeListOrder = serviceIndex,
                        blockNumber = testBlockNumber,
                    )
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
            val status2 = response.statusFlag2.toByte()
            observedIllegalNumberPreference =
                when (status2) {
                    0xA1.toByte() -> IllegalNumberErrorPreference.SERVICE_ERROR
                    0xA2.toByte() -> IllegalNumberErrorPreference.BLOCK_ERROR
                    else -> null
                }

            if (observedIllegalNumberPreference == null) {
                usedFallback = true
                maxServices = 1
                fallbackStatus1 = response.statusFlag1
                fallbackStatus2 = response.statusFlag2
                Log.w(
                    "CardScanService",
                    "ReadWithoutEncryption returned unexpected status while determining max services, falling back to 1 service (${formatStatus(fallbackStatus1, fallbackStatus2)})",
                )
                break
            }

            Log.d(
                "CardScanService",
                "ReadWithoutEncryption failed with $maxServices services, ${formatStatus(response)} (${observedIllegalNumberPreference.name})",
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
                readWithoutEncryptionMaxServicesPerRequest = maxServices,
                readBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED,
            )

        if (usedFallback) {
            throwReadWithoutEncryptionProbeFallback(
                "Maximum services fallback to 1: unexpected status (${formatStatus(fallbackStatus1, fallbackStatus2)})"
            )
        }

        return buildString { appendLine("Maximum services per request: $maxServices") }.trim()
    }

    private suspend fun executeReadWithoutEncryptionDetectIllegalNumberErrorPreference(
        target: FeliCaTarget
    ): String {
        val testTarget = findReadWithoutEncryptionTestTarget()

        pollSystemCode(target, testTarget.systemContext.systemCode)

        val requestedCount =
            minOf(
                ReadWithoutEncryptionCommand.MAX_SERVICE_CODES,
                ReadWithoutEncryptionCommand.MAX_BLOCKS,
            )

        val testService = testTarget.service
        val testBlockNumber = testTarget.blockNumber

        val serviceCodes = Array(requestedCount) { testService.code }
        val blockListElements =
            Array(requestedCount) { index ->
                BlockListElement(serviceCodeListOrder = index, blockNumber = testBlockNumber)
            }

        val readCommand =
            ReadWithoutEncryptionCommand(
                idm = target.idm,
                serviceCodes = serviceCodes,
                blockListElements = blockListElements,
            )

        val response = target.transceive(readCommand)
        val statusFlag1 = response.statusFlag1
        val statusFlag2 = response.statusFlag2

        if (response.isStatusSuccessful) {
            Log.w(
                "CardScanService",
                "Limit error detection request succeeded unexpectedly with $requestedCount services/blocks",
            )
            return buildString {
                    appendLine(
                        "Card accepted $requestedCount services and $requestedCount blocks (${formatStatus(response)})"
                    )
                    appendLine("Limit error preference unchanged")
                }
                .trim()
        }

        val observedPreference =
            when (statusFlag2.toByte()) {
                0xA1.toByte() -> IllegalNumberErrorPreference.SERVICE_ERROR
                0xA2.toByte() -> IllegalNumberErrorPreference.BLOCK_ERROR
                else -> null
            }

        if (observedPreference == null) {
            val fallbackPreference = scanContext.readWithoutEncryptionIllegalNumberErrorPreference
            val fallbackLabel = fallbackPreference?.name ?: "UNCHANGED"
            val fallbackMessage =
                "Limit error preference fallback to $fallbackLabel: unexpected status (${formatStatus(response)})"
            throwReadWithoutEncryptionProbeFallback(fallbackMessage)
        }

        scanContext =
            scanContext.copy(
                readWithoutEncryptionIllegalNumberErrorPreference = observedPreference,
                readBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED,
            )

        Log.d(
            "CardScanService",
            "Detected Read Without Encryption limit preference: ${observedPreference.name} (${formatStatus(response)})",
        )

        val preferenceLabel =
            when (observedPreference) {
                IllegalNumberErrorPreference.SERVICE_ERROR -> "SERVICE"
                IllegalNumberErrorPreference.BLOCK_ERROR -> "BLOCK"
            }

        return buildString {
                appendLine("Limit error preference: $preferenceLabel (${formatStatus(response)})")
            }
            .trim()
    }

    private suspend fun executeReadWithoutEncryptionDetermineMaxBlocks(
        target: FeliCaTarget
    ): String {
        val testTarget = findReadWithoutEncryptionTestTarget()
        val testService = testTarget.service
        val testBlockNumber = testTarget.blockNumber

        // Start with theoretical maximum and work down
        var maxBlocks = ReadWithoutEncryptionCommand.MAX_BLOCKS
        var usedFallback = false
        var fallbackStatus1: Byte? = null
        var fallbackStatus2: Byte? = null

        while (maxBlocks > 0) {
            pollSystemCode(target, testTarget.systemContext.systemCode)

            // Create block list elements for blocks 0 through (maxBlocks-1)
            val blockListElements =
                Array(maxBlocks) { blockIndex ->
                    BlockListElement(serviceCodeListOrder = 0, blockNumber = testBlockNumber)
                }

            // Create the read command with single service and multiple blocks
            val readCommand =
                ReadWithoutEncryptionCommand(
                    idm = target.idm,
                    serviceCodes = arrayOf(testService.code),
                    blockListElements = blockListElements,
                )

            try {
                val response = target.transceive(readCommand)
                if (response.isStatusSuccessful) {
                    // Command succeeded, we found the maximum
                    Log.d(
                        "CardScanService",
                        "ReadWithoutEncryption succeeded with $maxBlocks blocks",
                    )
                    break
                }
                if (
                    response.statusFlag2.toByte() != 0xA2.toByte() &&
                        response.statusFlag2.toByte() != 0xA8.toByte()
                ) {
                    usedFallback = true
                    maxBlocks = 1
                    fallbackStatus1 = response.statusFlag1
                    fallbackStatus2 = response.statusFlag2
                    Log.w(
                        "CardScanService",
                        "ReadWithoutEncryption returned unexpected status while determining max blocks, falling back to 1 block (${formatStatus(fallbackStatus1, fallbackStatus2)})",
                    )
                    break
                }
                Log.d(
                    "CardScanService",
                    "ReadWithoutEncryption failed with $maxBlocks blocks, ${formatStatus(response)}",
                )
            } catch (e: Exception) {
                // Card may not respond if command is too large (e.g., FeliCa Lite)
                // Poll to check if card is still present, then continue with smaller size
                Log.d(
                    "CardScanService",
                    "ReadWithoutEncryption got no response with $maxBlocks blocks: ${e.message}",
                )
            }
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
                readWithoutEncryptionMaxBlocksPerRequest = maxBlocks,
                readBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED,
            )

        if (usedFallback) {
            throwReadWithoutEncryptionProbeFallback(
                "Maximum blocks fallback to 1: unexpected status (${formatStatus(fallbackStatus1, fallbackStatus2)})"
            )
        }

        return buildString { appendLine("Maximum blocks per request: $maxBlocks") }.trim()
    }

    private suspend fun executeWriteWithoutEncryptionDetermineErrorIndication(
        target: FeliCaTarget
    ): String {
        // Find a writable service and its blocks
        val serviceInfo = findWritableServiceAndEmptyBlock()
        pollSystemCode(target, serviceInfo.systemCode)

        val writableService = serviceInfo.service
        val emptyBlockNumber = serviceInfo.availableBlocks.keys.minOrNull()!!
        val emptyBlockData = serviceInfo.availableBlocks[emptyBlockNumber]!!

        // Find the lowest block number not present in available or unavailable blocks
        val allKnownBlocks = serviceInfo.availableBlocks.keys + serviceInfo.unavailableBlocks
        var invalidBlockNumber = 0
        while (invalidBlockNumber in allKnownBlocks) {
            invalidBlockNumber++
        }

        val blocksToWrite =
            listOf(
                BlockListElement(serviceCodeListOrder = 0, blockNumber = emptyBlockNumber),
                BlockListElement(serviceCodeListOrder = 0, blockNumber = emptyBlockNumber),
                // Third element should be out of range to trigger error indication
                BlockListElement(serviceCodeListOrder = 0, blockNumber = invalidBlockNumber),
            )

        // Use the same data for the valid blocks (safe - no actual change), dummy for invalid block
        val blockData = arrayOf(emptyBlockData, emptyBlockData, emptyBlockData)

        val writeCommand =
            WriteWithoutEncryptionCommand(
                idm = target.idm,
                serviceCodes = arrayOf(writableService.code),
                blockListElements = blocksToWrite.toTypedArray(),
                blockData = blockData,
            )

        val response = target.transceive(writeCommand)
        val statusFlag1 = response.statusFlag1
        val statusFlag2 = response.statusFlag2

        if (response.isStatusSuccessful) {
            throw RuntimeException(
                "WriteWithoutEncryption failed to determine error indication, ${formatStatus(statusFlag1, statusFlag2)}"
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
                    Log.d("CardScanService", "Determined BITMASK error indication (status1=0x04)")
                    ErrorLocationIndication.BITMASK
                }
                statusFlag1.toInt() and 0xFF == 0x03 -> {
                    Log.d("CardScanService", "Determined INDEX error indication (status1=0x03)")
                    ErrorLocationIndication.INDEX
                }
                else -> {
                    throw RuntimeException(
                        "Unexpected response status for error indication determination: ${formatStatus(statusFlag1, statusFlag2)}"
                    )
                }
            }

        // Update scan context with determined error indication type
        scanContext =
            scanContext.copy(
                writeWithoutEncryptionErrorLocationIndication = errorIndicationType,
                writeBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED,
            )

        Log.d("CardScanService", "Determined error indication type: $errorIndicationType")

        return buildString {
                appendLine(
                    "Error indication type: ${errorIndicationType.name} (${formatStatus(statusFlag1, statusFlag2)})"
                )
            }
            .trim()
    }

    private suspend fun executeWriteWithoutEncryptionDetermineMaxBlocks(
        target: FeliCaTarget
    ): String {
        // Find a writable service and its blocks
        val serviceInfo = findWritableServiceAndEmptyBlock()
        pollSystemCode(target, serviceInfo.systemCode)

        val writableService = serviceInfo.service
        val emptyBlockNumber = serviceInfo.availableBlocks.keys.minOrNull()!!
        val emptyBlockData = serviceInfo.availableBlocks[emptyBlockNumber]!!

        // Start with theoretical maximum and work down
        var maxBlocks = WriteWithoutEncryptionCommand.MAX_BLOCKS

        while (maxBlocks > 0) {
            // Create block list elements using the empty block (safe rewrite)
            val blockListElements =
                Array(maxBlocks) { blockIndex ->
                    BlockListElement(serviceCodeListOrder = 0, blockNumber = emptyBlockNumber)
                }
            // Use the same data as the empty block (safe - no actual change)
            val blockData = Array(maxBlocks) { emptyBlockData.copyOf() }

            // Create the write command with single service and multiple blocks
            val writeCommand =
                WriteWithoutEncryptionCommand(
                    idm = target.idm,
                    serviceCodes = arrayOf(writableService.code),
                    blockListElements = blockListElements,
                    blockData = blockData,
                )

            try {
                val response = target.transceive(writeCommand)
                if (response.isStatusSuccessful) {
                    // Command succeeded, we found the maximum
                    Log.d(
                        "CardScanService",
                        "WriteWithoutEncryption succeeded with $maxBlocks blocks",
                    )
                    break
                }
                if (
                    response.statusFlag2.toByte() != 0xA2.toByte() &&
                        response.statusFlag2.toByte() != 0xA8.toByte()
                ) {
                    throw RuntimeException(
                        "WriteWithoutEncryption failed with unexpected error (not 0xA2 or 0xA8) at $maxBlocks blocks, ${formatStatus(response)}"
                    )
                }
                Log.d(
                    "CardScanService",
                    "WriteWithoutEncryption failed with $maxBlocks blocks, ${formatStatus(response)}",
                )
            } catch (e: Exception) {
                // Card may not respond if command is too large (e.g., FeliCa Lite)
                // Poll to check if card is still present, then continue with smaller size
                Log.d(
                    "CardScanService",
                    "WriteWithoutEncryption got no response with $maxBlocks blocks: ${e.message}",
                )
                pollSystemCode(target, serviceInfo.systemCode)
            }
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
                writeWithoutEncryptionMaxBlocksPerRequest = maxBlocks,
                writeBlocksWithoutEncryptionSupport = CommandSupport.SUPPORTED,
            )

        return buildString { appendLine("Maximum blocks per request: $maxBlocks") }.trim()
    }

    /** Result of finding a writable service and its blocks for safe testing. */
    private data class WritableServiceInfo(
        val service: Service,
        val systemCode: ByteArray?,
        /**
         * Map of block numbers to their data for blocks that are safe to write (all 0x00 or 0xFF)
         */
        val availableBlocks: Map<Int, ByteArray>,
        /** Set of block numbers that exist but are not safe to write (contain data) */
        val unavailableBlocks: Set<Int>,
    )

    /**
     * Helper function to find a suitable writable service and an "empty" block for safe testing.
     * Returns a WritableServiceInfo containing the service, available empty blocks, and unavailable
     * blocks.
     *
     * Requirements:
     * - Service number should not be 0 or 1023 (except 0x0900 for FeliCa Lite/NDEF)
     * - Service type should be R/W RANDOM (cyclic, purse should be ignored)
     * - Block must contain all 0x00 or all 0xFF (safe to rewrite with same data)
     */
    private fun findWritableServiceAndEmptyBlock(): WritableServiceInfo {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }
        val allServices = allDiscoveredNodes.filterIsInstance<Service>()

        if (allServices.isEmpty()) {
            throw PrerequisiteException("No services available for write testing")
        }

        // FeliCa Lite (88B4) and NDEF (12FC) system codes that have protected system blocks
        val felicaLiteSystemCode = byteArrayOf(0x88.toByte(), 0xB4.toByte())
        val ndefSystemCode = byteArrayOf(0x12.toByte(), 0xFC.toByte())

        // Filter services that don't require authentication and are writable R/W RANDOM
        // Service number restrictions are applied per-system in the loop below
        val writableServices =
            allServices.filter { service ->
                !service.attribute.authenticationRequired &&
                    service.attribute.type == ServiceType.RANDOM &&
                    service.attribute.mode == ServiceMode.READ_WRITE
            }

        if (writableServices.isEmpty()) {
            throw PrerequisiteException(
                "No suitable writable services found (R/W RANDOM, no auth required)"
            )
        }

        // Find a service with empty blocks, tracking both available and unavailable blocks
        data class ServiceCandidate(
            val service: Service,
            val systemContext: SystemScanContext,
            val availableBlocks: MutableMap<Int, ByteArray>,
            val unavailableBlocks: MutableSet<Int>,
        )

        val serviceCandidates = mutableListOf<ServiceCandidate>()

        for (service in writableServices) {
            val systemContext =
                scanContext.systemScanContexts.find { context -> context.nodes.contains(service) }
                    ?: continue

            // Check if this is a FeliCa Lite or NDEF system
            val isProtectedSystem =
                systemContext.systemCode?.let { code ->
                    code.contentEquals(felicaLiteSystemCode) || code.contentEquals(ndefSystemCode)
                } ?: false

            // Apply service number restrictions based on system type
            // For FeliCa Lite/NDEF: allow service 0x0900 (scratch pad area)
            // For other systems: service number must be != 0 and < 1023
            val isValidServiceNumber =
                if (isProtectedSystem) {
                    service.number == 0 || (service.number != 0 && service.number < 1023)
                } else {
                    service.number != 0 && service.number < 1023
                }
            if (!isValidServiceNumber) {
                continue
            }

            // Skip services that have MAC communication enabled (requires authentication)
            val macProperties = systemContext.nodeMacCommunicationProperties[service]
            if (macProperties != null && macProperties.enabled) {
                continue
            }

            val blockData = systemContext.serviceBlockData[service] ?: continue

            // For FeliCa Lite/NDEF systems, blocks >= 0x0E are system blocks
            val maxSafeBlockNumber = if (isProtectedSystem) 0x0D else Int.MAX_VALUE

            val availableBlocks = mutableMapOf<Int, ByteArray>()
            val unavailableBlocks = mutableSetOf<Int>()

            // Categorize blocks as available (empty) or unavailable (has data or is protected)
            for ((blockNumber, data) in blockData) {
                // Blocks >= 0x0E for FeliCa Lite/NDEF systems are protected system blocks
                if (blockNumber > maxSafeBlockNumber) {
                    unavailableBlocks.add(blockNumber)
                    continue
                }

                // For FeliCa Lite/NDEF, allow writing to any block
                // For other systems, only allow blocks that are all 0x00 or 0xFF (safe to rewrite)
                val isAllZero = data.all { it == 0x00.toByte() }
                val isAllFff = data.all { it == 0xFF.toByte() }
                if (isProtectedSystem || isAllZero || isAllFff) {
                    availableBlocks[blockNumber] = data
                } else {
                    unavailableBlocks.add(blockNumber)
                }
            }

            if (availableBlocks.isNotEmpty()) {
                serviceCandidates.add(
                    ServiceCandidate(service, systemContext, availableBlocks, unavailableBlocks)
                )
            }
        }

        if (serviceCandidates.isEmpty()) {
            throw PrerequisiteException(
                "No empty blocks (all 0x00 or 0xFF) found in any writable service. " +
                    "Cannot safely test write commands without risking data modification."
            )
        }

        // Prefer candidates with more available blocks (for multi-block tests)
        val bestCandidate = serviceCandidates.maxByOrNull { it.availableBlocks.size }!!

        val firstAvailableBlock = bestCandidate.availableBlocks.keys.minOrNull()!!
        val firstBlockData = bestCandidate.availableBlocks[firstAvailableBlock]!!

        Log.d(
            "CardScanService",
            "Selected writable service ${bestCandidate.service.code.toHexString()}, " +
                "${bestCandidate.availableBlocks.size} available blocks, " +
                "${bestCandidate.unavailableBlocks.size} unavailable blocks, " +
                "first available: block $firstAvailableBlock (${if (firstBlockData.all { it == 0x00.toByte() }) "all-zero" else "all-FF"}) " +
                "for safe write testing",
        )

        return WritableServiceInfo(
            service = bestCandidate.service,
            systemCode = bestCandidate.systemContext.systemCode,
            availableBlocks = bestCandidate.availableBlocks,
            unavailableBlocks = bestCandidate.unavailableBlocks,
        )
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
            throw PrerequisiteException(NO_SERVICES_WITHOUT_AUTHENTICATION)
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
            // Look up extra blocks for each service from the node registry
            val extraBlocksByServiceCode = mutableMapOf<String, Map<Int, String>>()
            val systemCodeHexForLookup = systemContext.systemCode?.toHexString()?.uppercase()
            if (systemCodeHexForLookup != null) {
                for (service in servicesWithoutAuth) {
                    val serviceCodeHex = service.code.toHexString().uppercase()
                    val extraBlocks =
                        com.kormax.felicatool.util.NodeRegistry.getExtraBlocks(
                            systemCodeHexForLookup,
                            serviceCodeHex,
                        )
                    if (extraBlocks.isNotEmpty()) {
                        extraBlocksByServiceCode[service.code.toHexString().uppercase()] =
                            extraBlocks
                    }
                }
            }

            val blockReader =
                BlockReader(
                    target = target,
                    errorLocationIndication =
                        scanContext.readWithoutEncryptionErrorLocationIndication,
                    maxBlocksPerRequest =
                        scanContext.readWithoutEncryptionMaxBlocksPerRequest ?: 15,
                    maxServicesPerRequest =
                        scanContext.readWithoutEncryptionMaxServicesPerRequest ?: 16,
                    extraBlocksByServiceCode = extraBlocksByServiceCode,
                )
            val blockDataByService = blockReader.readBlocksFromServices(servicesWithoutAuth)
            val extraBlockDataByService =
                blockReader.readExtraBlocksFromServices(servicesWithoutAuth)

            // Store block data in context for this system context, merging regular and extra blocks
            val serviceBlockDataMap = mutableMapOf<Node, Map<Int, ByteArray>>()
            blockDataByService.forEach { (service, blockData) ->
                val mergedBlockData = blockData.toMutableMap()
                // Merge extra blocks if available for this service
                extraBlockDataByService[service]?.let { extraBlocks ->
                    mergedBlockData.putAll(extraBlocks)
                }
                serviceBlockDataMap[service] = mergedBlockData
            }
            // Also add services that only have extra blocks (no regular blocks)
            extraBlockDataByService.forEach { (service, extraBlocks) ->
                if (!serviceBlockDataMap.containsKey(service)) {
                    serviceBlockDataMap[service] = extraBlocks
                }
            }

            // Update system context with block data
            val updatedSystemContext = systemContext.copy(serviceBlockData = serviceBlockDataMap)
            updatedSystemContexts.add(updatedSystemContext)

            // Update totals (using merged data)
            val contextBlocksRead = serviceBlockDataMap.values.sumOf { it.size }
            totalBlocksRead += contextBlocksRead
            totalServicesProcessed += serviceBlockDataMap.size

            // Build context-specific results
            val contextResult = buildString {
                appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                appendLine("  Blocks read: $contextBlocksRead")
                appendLine("  Services processed: ${serviceBlockDataMap.size}")
                appendLine()

                serviceBlockDataMap.forEach { (node, blockData) ->
                    val service = node as? Service ?: return@forEach
                    val blockCount = blockData.size
                    val regularBlocks = blockData.keys.filter { it < 0x80 }.size
                    val extraBlocks = blockData.keys.filter { it >= 0x80 }.size
                    appendLine(
                        "  Service ${service.code.toHexString()}: $blockCount blocks ($regularBlocks regular, $extraBlocks extra)"
                    )
                    if (blockData.isNotEmpty()) {
                        // Show first few block numbers and their data
                        val previewBlocks = blockData.entries.sortedBy { it.key }.take(4)
                        previewBlocks.forEach { (blockNum, data) ->
                            appendLine(
                                "    Block 0x${blockNum.toString(16).uppercase().padStart(4, '0')}: ${data.toHexString()}"
                            )
                        }
                        if (blockData.size > 4) {
                            appendLine("    ... (${blockData.size - 4} more blocks)")
                        }
                    }
                    appendLine()
                }
            }
            contextResults.add(contextResult)
        }

        // Update scan context with all system contexts and global limits
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

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

    /**
     * Execute force block discovery - exhaustively search for blocks in readable services by
     * iterating through all possible block numbers (0x0000 to 0xFFFF).
     */
    private suspend fun executeForceDiscoverBlocks(target: FeliCaTarget): Pair<String, String> {
        val contextResults = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalNewBlocksFound = 0
        var totalServicesProcessed = 0
        var totalBlocksScanned = 0

        val maxBlocksPerRequest = scanContext.readWithoutEncryptionMaxBlocksPerRequest ?: 15

        for ((contextIndex, systemContext) in scanContext.systemScanContexts.withIndex()) {
            pollSystemCode(target, systemContext.systemCode)

            val services = systemContext.nodes.filterIsInstance<Service>()
            val servicesWithoutAuth = services.filter { !it.attribute.authenticationRequired }
            val systemCodeHex = systemContext.systemCode?.toHexString() ?: "unknown"

            if (servicesWithoutAuth.isEmpty()) {
                contextResults.add(
                    "System Context ${contextIndex + 1} ($systemCodeHex): No readable services found"
                )
                updatedSystemContexts.add(systemContext)
                continue
            }

            // Get existing block data for this system context
            val existingBlockData = systemContext.serviceBlockData.toMutableMap()
            val newBlocksFoundInContext = mutableMapOf<Service, MutableMap<Int, ByteArray>>()

            for (service in servicesWithoutAuth) {
                totalServicesProcessed++
                val existingBlocks = existingBlockData[service]?.keys ?: emptySet()
                val newBlocks = mutableMapOf<Int, ByteArray>()

                // Determine the starting block number - start after the last known block
                val maxExistingBlock = existingBlocks.maxOrNull() ?: -1
                var startBlock = maxExistingBlock + 1

                // Skip if we've already scanned up to the maximum
                if (startBlock > 0xFFFF) {
                    continue
                }

                // Iterate through blocks in batches
                var currentBlock = startBlock
                var consecutiveFailures = 0
                val maxConsecutiveFailures = 256 // Stop after this many consecutive failures

                while (currentBlock <= 0xFFFF && consecutiveFailures < maxConsecutiveFailures) {
                    totalBlocksScanned++

                    try {
                        val blockElement =
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = currentBlock,
                                accessMode = BlockListElement.AccessMode.NORMAL,
                                extended = currentBlock > 255,
                            )
                        val command =
                            ReadWithoutEncryptionCommand(
                                idm = target.idm,
                                serviceCodes = arrayOf(service.code),
                                blockListElements = arrayOf(blockElement),
                            )
                        val response = target.transceive(command)

                        if (
                            response.statusFlag1 == 0x00.toByte() && response.blockData.isNotEmpty()
                        ) {
                            val readBlockData = response.blockData.first()
                            if (!existingBlocks.contains(currentBlock)) {
                                newBlocks[currentBlock] = readBlockData
                                totalNewBlocksFound++
                            }
                            consecutiveFailures = 0
                        } else {
                            consecutiveFailures++
                        }
                    } catch (e: Exception) {
                        consecutiveFailures++
                    }

                    currentBlock++
                }

                if (newBlocks.isNotEmpty()) {
                    newBlocksFoundInContext[service] = newBlocks
                }
            }

            // Merge new blocks with existing block data
            val mergedBlockData = existingBlockData.toMutableMap()
            for ((service, newBlocks) in newBlocksFoundInContext) {
                val existing = mergedBlockData[service]?.toMutableMap() ?: mutableMapOf()
                existing.putAll(newBlocks)
                mergedBlockData[service] = existing
            }

            val updatedSystemContext = systemContext.copy(serviceBlockData = mergedBlockData)
            updatedSystemContexts.add(updatedSystemContext)

            // Build context results
            val contextNewBlocks = newBlocksFoundInContext.values.sumOf { it.size }
            val contextResult = buildString {
                appendLine("System Context ${contextIndex + 1} ($systemCodeHex):")
                appendLine("  New blocks discovered: $contextNewBlocks")
                if (newBlocksFoundInContext.isNotEmpty()) {
                    newBlocksFoundInContext.forEach { (service, blocks) ->
                        appendLine(
                            "  Service ${service.code.toHexString()}: ${blocks.size} new blocks"
                        )
                        val previewBlocks = blocks.entries.sortedBy { it.key }.take(4)
                        previewBlocks.forEach { (blockNum, data) ->
                            appendLine(
                                "    Block 0x${blockNum.toString(16).uppercase().padStart(4, '0')}: ${data.toHexString()}"
                            )
                        }
                        if (blocks.size > 4) {
                            appendLine("    ... (${blocks.size - 4} more blocks)")
                        }
                    }
                }
            }
            contextResults.add(contextResult)
        }

        // Update scan context
        scanContext = scanContext.copy(systemScanContexts = updatedSystemContexts)

        val collapsedResult =
            "Discovered $totalNewBlocksFound new blocks from $totalServicesProcessed services"
        val expandedResult =
            buildString {
                    appendLine("Force Block Discovery Results:")
                    appendLine("Total new blocks discovered: $totalNewBlocksFound")
                    appendLine("Services processed: $totalServicesProcessed")
                    appendLine("Total blocks scanned: $totalBlocksScanned")
                    appendLine()
                    contextResults.forEach { appendLine(it) }
                    appendLine()
                    appendLine(
                        "Note: Scanning stops after $maxBlocksPerRequest consecutive read failures per service."
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
                            val nodeCode = node.fullCode.toHexString()
                            val formatted =
                                if (property.enabled) {
                                    buildString {
                                            appendLine(" ${nodeCode.padStart(8, ' ')}:")
                                            appendLine("   Upper Limit: ${property.upperLimit}")
                                            appendLine("   Lower Limit: ${property.lowerLimit}")
                                            appendLine(
                                                "   Generation Number: ${property.generationNumber}"
                                            )
                                        }
                                        .trimEnd()
                                } else {
                                    " ${nodeCode.padStart(8, ' ')}: Disabled"
                                }

                            valueLimitedPurseResults.add(formatted)
                            nodeValueLimitedPurseProperties[node] = property
                        }
                    }
                } else {
                    valueLimitedPurseResults.add(
                        "Batch ${batchIndex + 1}: Failed to retrieve Value-Limited Purse Service properties (Status: 0x${byteToHex(valueLimitedPurseResponse.statusFlag1)})"
                    )
                }
            }

            contextResults.add(
                buildString {
                    if (valueLimitedPurseResults.isNotEmpty()) {
                        valueLimitedPurseResults.forEachIndexed { index, result ->
                            append(result)
                            if (index < valueLimitedPurseResults.lastIndex) {
                                appendLine()
                            }
                        }
                    } else {
                        appendLine("No properties retrieved")
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
                    contextResults.forEachIndexed { index, result ->
                        append(result)
                        if (index < contextResults.lastIndex) {
                            appendLine()
                        }
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
                results.forEachIndexed { index, result ->
                    appendLine(result.trimEnd())
                    if (index < results.lastIndex) {
                        appendLine()
                    }
                }
            }
            .trimEnd()
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
                            val nodeCode = node.fullCode.toHexString().padStart(8, ' ')
                            macCommunicationResults.add(
                                " $nodeCode: ${if (property.enabled) "Enabled" else "Disabled"}"
                            )
                            nodeMacCommunicationProperties[node] = property
                        }
                    }
                } else {
                    macCommunicationResults.add(
                        "Batch ${batchIndex + 1}: Failed to retrieve MAC Communication properties (Status: 0x${byteToHex(macCommunicationResponse.statusFlag1)})"
                    )
                }
            }

            contextResults.add(
                buildString {
                    if (macCommunicationResults.isNotEmpty()) {
                        macCommunicationResults.forEachIndexed { index, result ->
                            append(result)
                            if (index < macCommunicationResults.lastIndex) {
                                appendLine()
                            }
                        }
                    } else {
                        appendLine("No properties retrieved")
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
                    contextResults.forEachIndexed { index, result ->
                        append(result)
                        if (index < contextResults.lastIndex) {
                            appendLine()
                        }
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
                results.forEachIndexed { index, result ->
                    appendLine(result.trimEnd())
                    if (index < results.lastIndex) {
                        appendLine()
                    }
                }
            }
            .trim()
    }

    private suspend fun executeRequestBlockInformation(target: FeliCaTarget): String {
        val allNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Request Block Information requires discovered nodes from Search Service Codes step."
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
                results.add("System Context ${contextIndex + 1} ($systemCodeHex): No nodes found")
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
                appendLine("Total services processed: ${allNodes.size}")
                appendLine()

                results.forEach { result ->
                    appendLine(result)
                    appendLine()
                }
            }
            .trim()
    }

    private suspend fun executeRequestBlockInformationEx(target: FeliCaTarget): String {
        val allNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Request Block Information Ex requires discovered nodes from Search Service Codes step."
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
                appendLine("Total nodes processed: ${allNodes.size}")
                appendLine()

                results.forEach { result ->
                    appendLine(result)
                    appendLine()
                }
            }
            .trim()
    }

    private suspend fun executeAuthentication1Des(target: FeliCaTarget): String {
        val testTarget = findBestAuthentication1DesTarget()
        if (testTarget == null) {
            throw RuntimeException(
                "No suitable system found for DES authentication (root area with valid DES key is required)."
            )
        }

        val systemCodeHex = testTarget.systemContext.systemCode?.toHexString() ?: "unknown"
        Log.d(
            "CardScanService",
            "Selected system $systemCodeHex for DES authentication using root area ${testTarget.rootArea.code.toHexString()} in area and node lists (node count: ${testTarget.systemContext.nodes.size})",
        )

        // Poll the selected system before authentication
        pollSystemCode(target, testTarget.systemContext.systemCode)
        val selectedSystemContext =
            scanContext.systemScanContexts.firstOrNull { context ->
                context.systemCode.sameBytes(testTarget.systemContext.systemCode)
            }
        val selectedSystemIdm = selectedSystemContext?.idm ?: target.idm

        // Generate a random challenge1A (8 bytes)
        val challenge1A = ByteArray(8) { 0x00.toByte() }

        val areasToAuth = listOf(testTarget.rootArea)
        // Area0 may appear in both lists: this is allowed because key updates can target areas.
        val nodesToAuth = listOf<Node>(testTarget.rootArea)

        val authenticateCommand =
            Authentication1DesCommand(
                idm = selectedSystemIdm,
                areaNodes = areasToAuth,
                nodes = nodesToAuth,
                challenge1A = challenge1A,
            )

        val authenticateResponse = target.transceive(authenticateCommand)
        setSystemMode(testTarget.systemContext.systemCode, Mode.Mode1.Des)

        val resetResult =
            resetAuthenticationState(
                target = target,
                authenticatedSystemCode = testTarget.systemContext.systemCode,
                authenticatedSystemIdm = selectedSystemIdm,
            )
        val resetModeResult = resetResult.message

        return buildString {
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
                        val nodeDescription =
                            when (node) {
                                is Area -> "Area ${node.number}-${node.endNumber}"
                                is Service -> "Service ${node.number}"
                                else -> "Node"
                            }
                        appendLine(
                            "  ${index + 1}. $nodeDescription (${node.code.toHexString()}) - $keyType"
                        )
                    }
                    appendLine()
                }

                appendLine()
                appendLine("$resetModeResult")
            }
            .trim()
    }

    private suspend fun executeAuthentication1Aes(target: FeliCaTarget): String {
        // Find the best system context that contains AES-compatible nodes requiring authentication
        var bestSystemContext: SystemScanContext? = null
        var bestAesCompatibleAreas = emptyList<Area>()
        var bestAesCompatibleServices = emptyList<Service>()
        var bestScore = 0

        for (systemContext in scanContext.systemScanContexts) {
            val areas = systemContext.nodes.filterIsInstance<Area>()
            val services = systemContext.nodes.filterIsInstance<Service>()
            val authServices = services.filter { it.attribute.authenticationRequired }

            // Collect key version information for this system context
            val systemAesKeyVersions = systemContext.nodeAesKeyVersions

            // Filter areas and services for AES authentication:
            // Has nodeAesKeyVersion
            val aesCompatibleAreas = areas.filter { area -> systemAesKeyVersions.containsKey(area) }
            val aesCompatibleServices =
                authServices.filter { service -> systemAesKeyVersions.containsKey(service) }

            // Score this system context: prefer systems with both areas and services, then by total
            // count
            val score =
                (if (aesCompatibleAreas.isNotEmpty()) 100 else 0) +
                    (if (aesCompatibleServices.isNotEmpty()) 100 else 0) +
                    aesCompatibleAreas.size +
                    aesCompatibleServices.size

            if (
                score > bestScore &&
                    (aesCompatibleAreas.isNotEmpty() || aesCompatibleServices.isNotEmpty())
            ) {
                bestScore = score
                bestSystemContext = systemContext
                bestAesCompatibleAreas = aesCompatibleAreas
                bestAesCompatibleServices = aesCompatibleServices
            }
        }

        if (bestSystemContext == null) {
            throw RuntimeException(
                "No system found with AES-compatible nodes. AES authentication requires nodes with AES key versions."
            )
        }

        val systemCodeHex = bestSystemContext.systemCode?.toHexString() ?: "unknown"
        Log.d(
            "CardScanService",
            "Selected system $systemCodeHex for AES authentication with ${bestAesCompatibleAreas.size} areas and ${bestAesCompatibleServices.size} services",
        )

        // Poll the selected system before authentication
        pollSystemCode(target, bestSystemContext.systemCode)
        val selectedSystemContext =
            scanContext.systemScanContexts.firstOrNull { context ->
                context.systemCode.sameBytes(bestSystemContext.systemCode)
            }
        val selectedSystemIdm = selectedSystemContext?.idm ?: target.idm

        // Generate a random challenge1A (16 bytes for AES)
        val challenge1A = ByteArray(16) { 0x0.toByte() }

        // Take a subset of AES-compatible nodes from the selected system (areas and services
        // combined in single field)
        // According to user feedback: areas and services are sent in a single field,
        // with the first byte being a flag (default 0x00)
        // Up to 16 nodes in total
        val aesCompatibleNodes =
            (bestAesCompatibleAreas.take(1) + bestAesCompatibleServices.take(1))

        val authenticateCommand =
            Authentication1AesCommand(
                idm = selectedSystemIdm,
                nodeCodes = aesCompatibleNodes.map { it.code }.toTypedArray(),
                challenge1A = challenge1A,
            )

        val authenticateResponse = target.transceive(authenticateCommand)
        setSystemMode(bestSystemContext.systemCode, Mode.Mode1.Aes)

        val resetResult =
            resetAuthenticationState(
                target = target,
                authenticatedSystemCode = bestSystemContext.systemCode,
                authenticatedSystemIdm = selectedSystemIdm,
            )
        val resetModeResult = resetResult.message

        return buildString {
                appendLine("AES Authentication Results:")
                appendLine("Selected system: $systemCodeHex")
                appendLine(
                    "AES-compatible nodes (${aesCompatibleNodes.size}) used in combined field"
                )
                appendLine("Challenge1A (sent): ${challenge1A.toHexString()}")
                appendLine("Response data (received): ${authenticateResponse.data.toHexString()}")
                appendLine()

                if (aesCompatibleNodes.isNotEmpty()) {
                    appendLine("Nodes authenticated (areas and services combined):")
                    aesCompatibleNodes.forEachIndexed { index, node ->
                        val nodeType =
                            when (node) {
                                is Area -> "Area ${node.number}-${node.endNumber}"
                                is Service -> "Service ${node.number}"
                                else -> "Node"
                            }
                        appendLine(
                            "  ${index + 1}. $nodeType (${node.code.toHexString()}) - AES key"
                        )
                    }
                    appendLine()
                }

                appendLine()
                appendLine("$resetModeResult")
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
