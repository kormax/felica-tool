package com.kormax.felicatool.service

import com.kormax.felicatool.felica.*
import com.kormax.felicatool.nfc.TagUnavailableException
import com.kormax.felicatool.service.logging.CommunicationLogEntry
import com.kormax.felicatool.service.logging.CommunicationLoggedFeliCaTarget
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.util.EmptyNodeMetadataProvider
import com.kormax.felicatool.util.IcTypeRegistry
import com.kormax.felicatool.util.NodeDefinitionType
import com.kormax.felicatool.util.NodeMetadataProvider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit
import kotlin.time.TimeSource
import kotlin.time.toDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay

private suspend fun FeliCaTarget.dropAndRediscover(timeout: Duration): FeliCaTarget {
    val initialIdm = initialIdm.copyOf()
    val rediscoveredTarget =
        try {
            drop()
            readerSession.discoverFeliCaTarget(timeout = timeout)
        } catch (e: TimeoutCancellationException) {
            throw TagUnavailableException("card was not rediscovered", e)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val details = e.message ?: e::class.simpleName ?: "Unknown error"
            throw TagUnavailableException("rediscovery failed: $details", e)
        }

    if (!rediscoveredTarget.initialIdm.contentEquals(initialIdm)) {
        throw TagUnavailableException("rediscovered a different card")
    }

    return rediscoveredTarget
}

/** Context class to store discovered card data across multiple scan steps */
data class CardScanContext(
    val systemScanContexts: List<SystemScanContext> = emptyList(),
    val primaryIdm: ByteArray? = null,
    val pmm: Pmm? = null,
    val primarySystemCode: ByteArray? = null,
    val discoveredSystemCodes: List<ByteArray> = emptyList(),
    val communicationPerformance: CommunicationPerformance? = null,
    val pollingCommandTrailingDataSupported: Boolean? = null,
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
    val authentication1DesNodeListHierarchyValidation:
        Authentication1DesNodeListHierarchyValidation =
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

class CardScanService(
    private val nodeMetadataProvider: NodeMetadataProvider = EmptyNodeMetadataProvider
) {

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

    private data class NodeDiscoveryResult(
        val methodLabel: String,
        val systemContexts: List<SystemScanContext>,
        val details: List<String>,
    )

    private data class ManualSystemProbeTarget(val systemCode: ByteArray, val label: String)

    private data class ManualSystemProbeResult(
        val target: ManualSystemProbeTarget,
        val found: Boolean,
        val contextAdded: Boolean,
        val idm: ByteArray? = null,
        val error: String? = null,
    )

    private data class WildcardSystemProbeResult(
        val probeSystemCode: ByteArray,
        val discoveredSystemCode: ByteArray,
        val contextAdded: Boolean,
        val idm: ByteArray,
    )

    companion object {
        const val CARD_LOST_MESSAGE = "Card lost during scan - scan terminated"
        private const val PRESENCE_CHECK_ATTEMPTS = 5
        private const val PRESENCE_CHECK_RETRY_DELAY_STEP_MS = 50L
        private const val PRESENCE_CHECK_REDISCOVERY_TIMEOUT_MILLIS = 600
        private const val RETRY_ATTEMPTS = 3
        private const val ATTEMPTS_DETERMINE_SUPPORTED = 5
        private const val RETRY_DELAY_STEP_MS = 10L
        private const val FIELD_RESET_REDISCOVERY_TIMEOUT_MILLIS = 600
        private const val NO_SERVICES_AVAILABLE = "No services available"
        private const val NO_SERVICES_WITHOUT_AUTHENTICATION =
            "No services found that don't require authentication"
        private const val NO_READABLE_SERVICE_IN_SELECTED_SYSTEM =
            "No readable services found in the selected system"
        private const val NO_SYSTEM_CONTEXT_WITH_READABLE_SERVICES =
            "No system context found with readable services"
        private val FELICA_LITE_SYSTEM_CODE = byteArrayOf(0x88.toByte(), 0xB4.toByte())
        private val NDEF_SYSTEM_CODE = byteArrayOf(0x12.toByte(), 0xFC.toByte())
        private val SECURE_ID_SYSTEM_CODE = byteArrayOf(0x95.toByte(), 0x7A.toByte())
        private val MANUAL_SYSTEM_PROBE_TARGETS =
            listOf(
                ManualSystemProbeTarget(NDEF_SYSTEM_CODE, "NDEF"),
                ManualSystemProbeTarget(FELICA_LITE_SYSTEM_CODE, "FeliCa Lite"),
                ManualSystemProbeTarget(SECURE_ID_SYSTEM_CODE, "FeliCa Secure ID"),
            )
        private const val WILDCARD_SYSTEM_PROBE_FIRST_PREFIX = 0x00
        private const val WILDCARD_SYSTEM_PROBE_LAST_PREFIX = 0xFE
        private val PROTECTED_READ_TEST_SERVICE_CODES = setOf("0B00", "0900")
        private const val PROTECTED_READ_TEST_BLOCK_NUMBER = 0x0092
        private const val REQUEST_SERVICE_UNAVAILABLE_FOR_UNKNOWN_ATTRIBUTE_PROBE =
            "Request Service command is unavailable; cannot probe unknown attribute behavior"
        private const val REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS = 3
        private const val REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER = 0
        private const val AUTHENTICATION1_DES_UNAVAILABLE_FOR_NODE_LIST_HIERARCHY_VALIDATION =
            "Authenticate1 DES support is not confirmed; cannot check node-list hierarchy validation"
        private const val AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS = 3
        private const val POLLING_TRAILING_DATA_PROBE_ATTEMPTS = 3
        private val POLLING_TRAILING_DATA_PROBE_BYTES = byteArrayOf(0x00)
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
        if (
            support == CommandSupport.UNSUPPORTED &&
                getCommandSupport(commandId) == CommandSupport.SUPPORTED
        ) {
            return
        }

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
                "request_code_list_determine_supported" ->
                    scanContext.copy(requestCodeListSupport = support)
                "search_service_code_determine_supported" ->
                    scanContext.copy(searchServiceCodeSupport = support)
                "request_service_determine_supported" ->
                    scanContext.copy(requestServiceSupport = support)
                "request_service_v2_determine_supported" ->
                    scanContext.copy(requestServiceV2Support = support)
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
                "authentication1_des_determine_supported" ->
                    scanContext.copy(authentication1DesSupport = support)
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
            "request_code_list_determine_supported" -> scanContext.requestCodeListSupport
            "search_service_code_determine_supported" -> scanContext.searchServiceCodeSupport
            "request_service_determine_supported" -> scanContext.requestServiceSupport
            "request_service_determine_unknown_node_attributes_supported" -> CommandSupport.UNKNOWN
            "get_node_key_versions" -> CommandSupport.UNKNOWN
            "authentication1_des_node_list_hierarchy_validation" -> CommandSupport.UNKNOWN
            "request_service_v2_determine_supported" -> scanContext.requestServiceV2Support
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
            "authentication1_des_determine_supported" -> scanContext.authentication1DesSupport
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

    private fun Iterable<ByteArray>.containsBytes(bytes: ByteArray): Boolean = any {
        it.contentEquals(bytes)
    }

    private fun Iterable<ByteArray>.toUniqueByteArrays(): List<ByteArray> {
        val unique = mutableListOf<ByteArray>()
        forEach { bytes ->
            if (!unique.containsBytes(bytes)) {
                unique += bytes
            }
        }
        return unique
    }

    private fun addOrUpdateSystemContext(systemCode: ByteArray, idm: ByteArray): Boolean {
        var existingMatched = false
        var idmUpdated = false
        val updatedContexts =
            scanContext.systemScanContexts.map { context ->
                if (context.systemCode.sameBytes(systemCode)) {
                    existingMatched = true
                    if (context.idm?.contentEquals(idm) != true) {
                        idmUpdated = true
                        context.copy(idm = idm)
                    } else {
                        context
                    }
                } else {
                    context
                }
            }

        scanContext =
            if (existingMatched) {
                if (idmUpdated) {
                    scanContext.copy(systemScanContexts = updatedContexts)
                } else {
                    scanContext
                }
            } else {
                scanContext.copy(
                    systemScanContexts =
                        scanContext.systemScanContexts +
                            SystemScanContext(systemCode = systemCode, idm = idm)
                )
            }

        return !existingMatched
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
                ?: target.systemCode
                ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte()) // Use wildcard if no system code
        val pollingCommand =
            PollingCommand(
                systemCode = pollingSystemCode,
                requestCode = RequestCode.NO_REQUEST,
                timeSlot = TimeSlot.SLOT_1,
            )
        val pollingResponse = target.transceive(pollingCommand)
        updateSystemIdmFromPolling(systemCode, pollingResponse.idm)
        updateModeAfterSuccessfulPolling(systemCode)
    }

    private suspend fun resetAuthenticationState(
        target: FeliCaTarget,
        authenticatedSystemCode: ByteArray?,
        authenticatedSystemIdm: ByteArray?,
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
                        message = "State reset to Mode 0 by executing Reset Mode command",
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

        var fieldResetFailurePrefix =
            "Reset Mode support is not confirmed; field-drop reset to Mode 0"
        var fieldResetSuccessMessage =
            "State reset to Mode 0 by dropping reader field and rediscovering the card"

        if (scanContext.resetModeSupport == CommandSupport.UNSUPPORTED) {
            fieldResetFailurePrefix = "Reset Mode is unsupported; field-drop reset to Mode 0"
        } else {
            val alternativeSystemCode =
                scanContext.systemScanContexts
                    .firstOrNull { context ->
                        !context.systemCode.sameBytes(authenticatedSystemCode)
                    }
                    ?.systemCode

            if (alternativeSystemCode != null) {
                try {
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

                    return AuthenticationStateResetResult(
                        message =
                            "State reset to Mode 0 by polling another system (${alternativeSystemCode.toHexString().uppercase()}); $returnPollingResult",
                        modeSetToMode0 = true,
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val details = e.message ?: e::class.simpleName ?: "Unknown error"
                    fieldResetFailurePrefix =
                        "State reset via alternate-system polling failed ($details); field-drop reset to Mode 0"
                    fieldResetSuccessMessage =
                        "State reset to Mode 0 by dropping reader field after alternate-system polling failed ($details)"
                }
            } else {
                fieldResetFailurePrefix =
                    "Reset Mode support is not confirmed and no alternate system is available for polling reset; field-drop reset to Mode 0"
            }
        }

        return try {
            val rediscoveredTarget =
                target.dropAndRediscover(FIELD_RESET_REDISCOVERY_TIMEOUT_MILLIS.milliseconds)
            (target as? CommunicationLoggedFeliCaTarget)?.replaceTarget(rediscoveredTarget)
            resetAllSystemModesToMode0()
            AuthenticationStateResetResult(
                message = fieldResetSuccessMessage,
                modeSetToMode0 = true,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: TagUnavailableException) {
            AuthenticationStateResetResult(
                message = "$fieldResetFailurePrefix failed (${e.message})",
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
            is Area ->
                "Area ${node.number}-${node.endNumber} (${node.code.toHexString().uppercase()})"
            is Service -> "Service ${node.number} (${node.code.toHexString().uppercase()})"
            is System -> "System (${node.code.toHexString().uppercase()})"
            else -> "Node (${node.code.toHexString().uppercase()})"
        }

    private fun systemCodeLabel(systemCode: ByteArray?): String =
        systemCode?.toHexString()?.uppercase() ?: "unknown"

    private fun hasNonStructuralNodes(nodes: List<Node>): Boolean = nodes.any { node ->
        node !is System && !(node is Area && node.isRoot)
    }

    private fun normalizeDiscoveredNodes(nodes: List<Node>): List<Node> {
        val normalized = mutableListOf<Node>()

        fun addNode(node: Node) {
            if (normalized.none { existing -> existing == node }) {
                normalized.add(node)
            }
        }

        addNode(System)
        addNode(Area.ROOT)
        nodes.forEach(::addNode)
        return normalized
    }

    private fun describeNodeForDiscovery(node: Node): String =
        when (node) {
            is Area ->
                if (node.isRoot) {
                    "Root Area (${node.code.toHexString().uppercase()})"
                } else {
                    "Area ${node.number}-${node.endNumber} (${node.code.toHexString().uppercase()})"
                }
            is Service -> "Service ${node.number} (${node.code.toHexString().uppercase()})"
            is System -> "System (${node.code.toHexString().uppercase()})"
            else -> "Node (${node.code.toHexString().uppercase()})"
        }

    private fun ensureNodeDiscoverySystemContexts(target: FeliCaTarget): List<SystemScanContext> {
        if (scanContext.systemScanContexts.isNotEmpty()) {
            return scanContext.systemScanContexts
        }

        val fallbackContext =
            SystemScanContext(systemCode = scanContext.primarySystemCode, idm = target.idm)
        scanContext = scanContext.copy(systemScanContexts = listOf(fallbackContext))
        return scanContext.systemScanContexts
    }

    private fun applyKnownNodeFallbacks(
        systemContexts: List<SystemScanContext>,
        force: Boolean,
        details: MutableList<String>,
    ): Pair<List<SystemScanContext>, Int> {
        var fallbackSystems = 0
        val updatedContexts = systemContexts.map { systemContext ->
            val fallbackNodes =
                knownNodesForSystemCode(systemContext.systemCode).ifEmpty {
                    normalizeDiscoveredNodes(emptyList())
                }
            val fallbackHasKnownNodes = hasNonStructuralNodes(fallbackNodes)
            val shouldUseFallback =
                force ||
                    systemContext.nodes.isEmpty() ||
                    (!hasNonStructuralNodes(systemContext.nodes) && fallbackHasKnownNodes)

            if (!shouldUseFallback) {
                systemContext
            } else {
                fallbackSystems++
                val source =
                    if (fallbackHasKnownNodes) {
                        "registry"
                    } else {
                        "default structural nodes"
                    }
                details.add(
                    "System ${systemCodeLabel(systemContext.systemCode)}: populated ${fallbackNodes.size} fallback node(s) from $source"
                )
                systemContext.copy(
                    nodes = fallbackNodes,
                    registryPopulatedNodes = fallbackNodes.toSet(),
                )
            }
        }

        return updatedContexts to fallbackSystems
    }

    private fun hasValidDesKeyOnRootArea(
        systemContext: SystemScanContext,
        rootArea: Area,
    ): Boolean {
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

            val rootArea =
                systemContext.nodes.filterIsInstance<Area>().firstOrNull { it.isRoot } ?: Area.ROOT
            if (!hasValidDesKeyOnRootArea(systemContext, rootArea)) {
                continue
            }

            val nodeCount = systemContext.nodes.size
            if (nodeCount > bestNodeCount) {
                bestNodeCount = nodeCount
                bestTarget =
                    Authentication1DesTestTarget(systemContext = systemContext, rootArea = rootArea)
            }
        }

        return bestTarget
    }

    private fun findAuthentication1DesNonImmediateNode(
        testTarget: Authentication1DesTestTarget
    ): Node? {
        val rootArea = testTarget.rootArea
        val areasInSystem = testTarget.systemContext.nodes.filterIsInstance<Area>()
        val nonRootAreas = areasInSystem.filter { area ->
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

        val allServicesWithoutAuth = allServices.filter { service ->
            !service.attribute.authenticationRequired
        }
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
        val servicesWithoutAuth = servicesInBestSystem.filter { service ->
            !service.attribute.authenticationRequired
        }
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

        val response =
            executeWithRetries(
                target = target,
                commandLabel = "ReadWithoutEncryptionCommand",
                systemCode = testTarget.systemContext.systemCode,
                maxAttempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) { activeTarget, _ ->
                ReadWithoutEncryptionCommand(
                    idm = activeTarget.idm,
                    serviceCodes = arrayOf(testTarget.service.code),
                    blockListElements =
                        arrayOf(
                            BlockListElement(
                                serviceCodeListOrder = 0,
                                blockNumber = testTarget.blockNumber,
                            )
                        ),
                )
            }
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
    private suspend fun ensureCardPresence(
        target: FeliCaTarget,
        stepId: String,
        maxAttempts: Int = PRESENCE_CHECK_ATTEMPTS,
    ) {
        var lastException: Exception? = null

        // Try a few times before treating brief presence-check failures as card loss.
        var attempt = 1
        while (attempt <= maxAttempts) {
            if (!target.isAvailable) {
                try {
                    val rediscoveredTarget =
                        target.dropAndRediscover(
                            PRESENCE_CHECK_REDISCOVERY_TIMEOUT_MILLIS.milliseconds
                        )
                    ScanLog.w("CardScanService", "Card rediscovered")
                    (target as? CommunicationLoggedFeliCaTarget)?.replaceTarget(rediscoveredTarget)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ScanLog.w(
                        "CardScanService",
                        "Card rediscovery failed during presence check for step $stepId",
                        e,
                    )
                    throw TagUnavailableException(CARD_LOST_MESSAGE, e)
                }
            }

            try {
                if (scanContext.requestResponseSupport == CommandSupport.SUPPORTED) {
                    try {
                        target.transceive(RequestResponseCommand(target.idm))
                        return
                    } catch (e: Exception) {
                        when (e) {
                            is CancellationException,
                            is TagUnavailableException -> throw e
                        }
                        ScanLog.w(
                            "CardScanService",
                            "Request Response presence check failed for step $stepId",
                            e,
                        )
                    }
                }

                if (scanContext.requestServiceSupport == CommandSupport.SUPPORTED) {
                    // Some cards, such as IC 0x24 on Octopus, may stop responding to
                    // RequestResponse in Mode1, while RequestService still responds.
                    try {
                        val probeService = Service(0, ServiceAttribute.RandomRoWithoutKey)
                        target.transceive(
                            RequestServiceCommand(target.idm, arrayOf(probeService.code))
                        )
                        return
                    } catch (e: Exception) {
                        when (e) {
                            is CancellationException,
                            is TagUnavailableException -> throw e
                        }
                        ScanLog.w(
                            "CardScanService",
                            "Request Service presence check failed for step $stepId",
                            e,
                        )
                    }
                }

                pollSystemCode(target)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                ScanLog.w(
                    "CardScanService",
                    "Card presence check attempt $attempt failed for step $stepId",
                    e,
                )
                delay(PRESENCE_CHECK_RETRY_DELAY_STEP_MS * attempt)
            }
            attempt++
        }

        throw TagUnavailableException(CARD_LOST_MESSAGE, lastException)
    }

    private suspend fun <T : FelicaResponse> executeWithRetries(
        target: FeliCaTarget,
        command: FelicaCommand<T>,
        systemCode: ByteArray? = null,
        maxAttempts: Int = RETRY_ATTEMPTS,
        retryDelayStepMs: Long = RETRY_DELAY_STEP_MS,
    ): T =
        executeWithRetries(
            target = target,
            commandLabel = command::class.simpleName ?: "FeliCa command",
            systemCode = systemCode,
            maxAttempts = maxAttempts,
            retryDelayStepMs = retryDelayStepMs,
        ) { _, _ ->
            command
        }

    private suspend fun <T : FelicaResponse> executeWithRetries(
        target: FeliCaTarget,
        commandLabel: String,
        systemCode: ByteArray? = null,
        maxAttempts: Int = RETRY_ATTEMPTS,
        retryDelayStepMs: Long = RETRY_DELAY_STEP_MS,
        createCommand: (FeliCaTarget, Int) -> FelicaCommand<T>,
    ): T {
        var lastException: Exception? = null
        var activeTarget = target

        for (attempt in 1..maxAttempts) {
            try {
                if (!activeTarget.isAvailable) {
                    val rediscoveredTarget =
                        try {
                            activeTarget.dropAndRediscover(
                                PRESENCE_CHECK_REDISCOVERY_TIMEOUT_MILLIS.milliseconds
                            )
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            throw TagUnavailableException(CARD_LOST_MESSAGE, e)
                        }

                    ScanLog.w("CardScanService", "Card rediscovered")
                    (target as? CommunicationLoggedFeliCaTarget)?.replaceTarget(rediscoveredTarget)
                    activeTarget =
                        if (target is CommunicationLoggedFeliCaTarget) {
                            target
                        } else {
                            rediscoveredTarget
                        }
                }

                systemCode?.let { pollSystemCode(activeTarget, it) }
                val retryTimeoutExtension =
                    (retryDelayStepMs * (attempt - 1)).toDuration(DurationUnit.MILLISECONDS)
                val command = createCommand(activeTarget, attempt)
                return activeTarget.transceive(
                    command,
                    activeTarget.inferTimeout(command) + retryTimeoutExtension,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt >= maxAttempts) {
                    break
                }

                ScanLog.w("CardScanService", "$commandLabel attempt $attempt failed; retrying", e)
            }
        }

        if (!activeTarget.isAvailable) {
            throw TagUnavailableException(CARD_LOST_MESSAGE, lastException)
        }

        throw lastException ?: RuntimeException("$commandLabel failed without an exception")
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

        nodeMetadataProvider.ensureReady()

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
            val result =
                when (step.id) {
                    "polling" -> executeInitialInfo(target)
                    "request_response" -> executeRequestResponse(target)
                    "request_system_code" -> executeRequestSystemCode(target)
                    "probe_system_codes_manually" -> executeProbeSystemCodesManually(target)
                    "request_specification_version" -> executeRequestSpecificationVersion(target)
                    "get_system_status" -> executeGetSystemStatus(target)
                    "polling_system_code" -> executePollingSystemCode(target)
                    "polling_communication_performance" ->
                        executePollingCommunicationPerformance(target)
                    "polling_determine_trailing_data_supported" ->
                        executePollingDetermineTrailingDataSupported(target)
                    "request_code_list_determine_supported" -> executeRequestCodeList(target)
                    "search_service_code_determine_supported" ->
                        executeSearchServiceCodeDetermineSupported(target)
                    "request_service_determine_supported" ->
                        executeRequestServiceDetermineSupported(target)
                    "request_service_v2_determine_supported" ->
                        executeRequestServiceV2DetermineSupported(target)
                    "request_service_determine_unknown_node_attributes_supported" ->
                        executeRequestServiceUnknownNodeAttributes(target)
                    "get_node_key_versions" -> executeGetNodeKeyVersions(target)
                    "discover_nodes" -> executeDiscoverNodes(target)
                    "force_discover_nodes" -> executeForceDiscoverNodes(target)
                    "set_parameter" -> executeSetParameter(target)
                    "get_container_issue_information" -> executeGetContainerIssueInformation(target)
                    "get_platform_information" -> executeGetPlatformInformation(target)
                    "get_container_id" -> executeGetContainerId(target)
                    "get_container_property" -> executeGetContainerProperty(target)
                    "echo" -> executeEcho(target)
                    "internal_authenticate_and_read" -> executeInternalAuthenticateAndRead(target)
                    "reset_mode" -> executeResetMode(target)
                    "get_area_information" -> executeGetAreaInformation(target)
                    "get_node_property_value_limited_service" ->
                        executeGetNodePropertyValueLimitedService(target)
                    "get_node_property_mac_communication" ->
                        executeGetNodePropertyMacCommunication(target)
                    "request_block_information" -> executeRequestBlockInformation(target)
                    "request_block_information_ex" -> executeRequestBlockInformationEx(target)
                    "read_without_encryption_determine_error_indication" ->
                        executeReadWithoutEncryptionDetermineErrorIndication(target)
                    "read_without_encryption_determine_max_services" ->
                        executeReadWithoutEncryptionDetermineMaxServices(target)
                    "read_without_encryption_determine_supported" ->
                        executeReadWithoutEncryptionDetermineSupported(target)
                    "read_without_encryption_detect_illegal_number_error_preference" ->
                        executeReadWithoutEncryptionDetectIllegalNumberErrorPreference(target)
                    "read_without_encryption_determine_max_blocks" ->
                        executeReadWithoutEncryptionDetermineMaxBlocks(target)
                    "read_blocks_without_encryption" -> executeReadBlocksWithoutEncryption(target)
                    "force_discover_blocks" -> executeForceDiscoverBlocks(target)
                    "write_without_encryption_determine_error_indication" ->
                        executeWriteWithoutEncryptionDetermineErrorIndication(target)
                    "write_without_encryption_determine_max_blocks" ->
                        executeWriteWithoutEncryptionDetermineMaxBlocks(target)
                    "authentication1_des_node_list_hierarchy_validation" ->
                        executeAuthentication1DesNodeListHierarchyValidation(target)
                    "authentication1_des_determine_supported" ->
                        executeAuthentication1DesDetermineSupported(target)
                    "authentication1_aes" -> executeAuthentication1Aes(target)
                    "scan_overview" -> {
                        // Copy logs from target into scan context at overview step
                        (target as? CommunicationLoggedFeliCaTarget)?.let { loggedTarget ->
                            scanContext = scanContext.copy(communicationLog = loggedTarget.log)
                        }
                        "Click to view comprehensive overview of all discovered card data"
                    }
                    else -> "Unknown step"
                }

            // Mark command as supported if we reach this point.
            updateCommandSupport(step.id, CommandSupport.SUPPORTED)

            val resultStep = step.completedWith(result)

            // Calculate execution duration
            val duration = startTime.elapsedNow()

            // Return step with duration
            return resultStep.copy(duration = duration)
        } catch (e: CommandSupportedBehaviorUnexpectedException) {
            // Probe fallback applied - command responded but fallback values were used
            ScanLog.w("CardScanService", "Probe fallback used for step ${step.id}: ${e.message}")

            val duration = startTime.elapsedNow()

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Probe fallback applied",
                duration = duration,
            )
        } catch (e: PrerequisiteException) {
            // Prerequisite not met - don't mark command as unsupported, leave as unknown
            ScanLog.w("CardScanService", "Prerequisite not met for step ${step.id}: ${e.message}")

            val duration = startTime.elapsedNow()

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Prerequisite not met",
                duration = duration,
            )
        } catch (e: TagUnavailableException) {
            ScanLog.e("CardScanService", "Card unavailable for step ${step.id}", e)

            val duration = startTime.elapsedNow()

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = CARD_LOST_MESSAGE,
                duration = duration,
            )
        } catch (e: Exception) {
            ScanLog.e("CardScanService", "Error executing step ${step.id}", e)

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

    private fun CardScanStep.completedWith(result: Any): CardScanStep =
        when (result) {
            is Pair<*, *> ->
                copy(
                    status = StepStatus.COMPLETED,
                    result = result.second.toStepResultText(),
                    collapsedResult = result.first.toStepResultText(),
                    isCollapsed = true,
                )
            else -> copy(status = StepStatus.COMPLETED, result = result.toStepResultText())
        }

    private fun Any?.toStepResultText(): String = this?.toString().orEmpty()

    private suspend fun handleDiscoveredSystemCodes(
        discoveredSystemCodes: List<ByteArray>,
        target: FeliCaTarget,
    ): List<SystemScanContext> {
        val allSystemCodes = discoveredSystemCodes.toUniqueByteArrays()

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
                        ScanLog.d(
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

    private suspend fun executeProbeSystemCodesManually(target: FeliCaTarget): String {
        ensureCardPresence(target, "probe_system_codes_manually")

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
                MANUAL_SYSTEM_PROBE_TARGETS.filterNot { probeTarget ->
                    reportedSystemCodes.containsBytes(probeTarget.systemCode)
                }
            } else {
                MANUAL_SYSTEM_PROBE_TARGETS
            }
        val skippedTargets = MANUAL_SYSTEM_PROBE_TARGETS.filter { probeTarget ->
            probeTarget !in targetsToProbe
        }

        val results = mutableListOf<ManualSystemProbeResult>()

        for (probeTarget in targetsToProbe) {
            try {
                pollSystemCode(target, probeTarget.systemCode)
                val contextAdded = addOrUpdateSystemContext(probeTarget.systemCode, target.idm)

                results +=
                    ManualSystemProbeResult(
                        target = probeTarget,
                        found = true,
                        contextAdded = contextAdded,
                        idm = target.idm,
                    )
            } catch (e: Exception) {
                results +=
                    ManualSystemProbeResult(
                        target = probeTarget,
                        found = false,
                        contextAdded = false,
                        error = e.message ?: e::class.simpleName ?: "Unknown error",
                    )
            }
        }

        val wildcardResults = mutableListOf<WildcardSystemProbeResult>()
        var wildcardSkipped = 0
        var wildcardNoResponse = 0

        if (scanSettings.bruteForceSystemCodePrefixes) {
            for (prefix in WILDCARD_SYSTEM_PROBE_FIRST_PREFIX..WILDCARD_SYSTEM_PROBE_LAST_PREFIX) {
                val probeSystemCode = byteArrayOf(prefix.toByte(), 0xFF.toByte())
                val knownSystemCodes =
                    reportedSystemCodes.toUniqueByteArrays() +
                        scanContext.systemScanContexts.mapNotNull { context -> context.systemCode }

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
                    val pollingResponse =
                        target.transceive(
                            PollingCommand(
                                systemCode = probeSystemCode,
                                requestCode = RequestCode.SYSTEM_CODE_REQUEST,
                                timeSlot = TimeSlot.SLOT_1,
                            )
                        )

                    val discoveredSystemCode =
                        if (pollingResponse.hasRequestData) {
                            pollingResponse.systemCode
                        } else {
                            probeSystemCode
                        }

                    val contextAdded =
                        addOrUpdateSystemContext(discoveredSystemCode, pollingResponse.idm)
                    wildcardResults +=
                        WildcardSystemProbeResult(
                            probeSystemCode = probeSystemCode,
                            discoveredSystemCode = discoveredSystemCode,
                            contextAdded = contextAdded,
                            idm = pollingResponse.idm,
                        )
                } catch (e: Exception) {
                    wildcardNoResponse++
                }
            }
        }

        val foundCount = results.count { it.found }
        val addedCount = results.count { it.contextAdded }
        val wildcardAddedCount = wildcardResults.count { it.contextAdded }
        return buildString {
                appendLine("Probe System Codes Manually Results:")

                if (skippedTargets.isNotEmpty()) {
                    appendLine("Skipped reported candidate(s):")
                    skippedTargets.forEach { probeTarget ->
                        appendLine(
                            "  - ${probeTarget.systemCode.toHexString().uppercase()} (${probeTarget.label})"
                        )
                    }
                    appendLine()
                }

                if (results.isEmpty()) {
                    appendLine("No manual probes needed.")
                } else {
                    appendLine("Probed candidate(s):")
                    results.forEach { result ->
                        val systemCodeHex = result.target.systemCode.toHexString().uppercase()
                        val status =
                            if (result.found) {
                                val idmHex = result.idm?.toHexString()?.uppercase() ?: "unknown"
                                val contextStatus =
                                    if (result.contextAdded) {
                                        "added system context"
                                    } else {
                                        "system context already present"
                                    }
                                "found (IDM $idmHex; $contextStatus)"
                            } else {
                                "not found (${result.error})"
                            }
                        appendLine("  - $systemCodeHex (${result.target.label}): $status")
                    }
                }

                if (scanSettings.bruteForceSystemCodePrefixes) {
                    appendLine()
                    appendLine("Wildcard suffix brute force:")
                    appendLine(
                        "  Range: ${
                            WILDCARD_SYSTEM_PROBE_FIRST_PREFIX.toString(16).uppercase().padStart(2, '0')
                        }FF-${
                            WILDCARD_SYSTEM_PROBE_LAST_PREFIX.toString(16).uppercase().padStart(2, '0')
                        }FF"
                    )
                    appendLine("  Skipped known prefixes: $wildcardSkipped")
                    appendLine("  No response: $wildcardNoResponse")
                    if (wildcardResults.isNotEmpty()) {
                        appendLine("  Found:")
                        wildcardResults.forEach { result ->
                            val probeSystemCodeHex =
                                result.probeSystemCode.toHexString().uppercase()
                            val discoveredSystemCodeHex =
                                result.discoveredSystemCode.toHexString().uppercase()
                            val contextStatus =
                                if (result.contextAdded) {
                                    "added system context"
                                } else {
                                    "system context already present"
                                }
                            appendLine(
                                "    - $probeSystemCodeHex -> $discoveredSystemCodeHex " +
                                    "(IDM ${result.idm.toHexString().uppercase()}; $contextStatus)"
                            )
                        }
                    }
                }

                appendLine()
                appendLine(
                    "Found ${foundCount + wildcardResults.size} system(s); added ${addedCount + wildcardAddedCount} new system context(s)."
                )
            }
            .trim()
    }

    private suspend fun executeInitialInfo(target: FeliCaTarget): String {
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
    }

    private suspend fun executeRequestResponse(target: FeliCaTarget): String {
        val requestResponseResponse = executeWithRetries(target, RequestResponseCommand(target.idm))

        val mode = requestResponseResponse.mode

        return buildString {
                appendLine("Card is present and responding")
                appendLine("Current Mode: ${mode.name} (${mode.value})")
            }
            .trim()
    }

    private suspend fun executeRequestSystemCode(target: FeliCaTarget): String {
        val requestSystemCodeResponse =
            executeWithRetries(target, RequestSystemCodeCommand(target.idm))

        // Handle special system codes and ensure system contexts exist
        val updatedSystemContexts =
            handleDiscoveredSystemCodes(requestSystemCodeResponse.systemCodes, target)

        // Store discovered system codes in context and update system contexts
        scanContext =
            scanContext.copy(
                discoveredSystemCodes = requestSystemCodeResponse.systemCodes,
                systemScanContexts = updatedSystemContexts,
            )

        return if (requestSystemCodeResponse.systemCodes.isNotEmpty()) {
            buildString {
                    appendLine(
                        "Discovered System Codes (${requestSystemCodeResponse.systemCodes.size}):"
                    )
                    requestSystemCodeResponse.systemCodes.forEachIndexed { index, systemCode ->
                        val systemCodeHex = systemCode.toHexString().uppercase()
                        appendLine("  ${index + 1}. $systemCodeHex")
                    }
                }
                .trim()
        } else {
            "No system codes discovered"
        }
    }

    private suspend fun executeRequestSpecificationVersion(target: FeliCaTarget): String {
        ensureCardPresence(target, "request_specification_version")

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
        ensureCardPresence(target, "get_system_status")

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
        ensureCardPresence(target, "polling_system_code")

        val systemCodeCommand =
            PollingCommand(
                systemCode = target.systemCode ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                requestCode = RequestCode.SYSTEM_CODE_REQUEST,
            )
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
        ensureCardPresence(target, "polling_communication_performance")

        val commPerfCommand =
            PollingCommand(
                systemCode = target.systemCode ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte()),
                requestCode = RequestCode.COMMUNICATION_PERFORMANCE_REQUEST,
            )
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

    private suspend fun executePollingDetermineTrailingDataSupported(target: FeliCaTarget): String {
        ensureCardPresence(target, "polling_determine_trailing_data_supported")

        val systemCode = target.systemCode ?: byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val command =
            PollingCommand(
                systemCode = systemCode,
                requestCode = RequestCode.NO_REQUEST,
                timeSlot = TimeSlot.SLOT_1,
                trailingData = POLLING_TRAILING_DATA_PROBE_BYTES,
            )
        val commandLength = command.toByteArray().size

        val response =
            try {
                executeWithRetries(
                    target = target,
                    command = command,
                    maxAttempts = POLLING_TRAILING_DATA_PROBE_ATTEMPTS,
                    retryDelayStepMs = 50,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                null
            }

        if (response != null) {
            updateSystemIdmFromPolling(null, response.idm)
            updateModeAfterSuccessfulPolling(null)
            scanContext = scanContext.copy(pollingCommandTrailingDataSupported = true)

            val responseIdmHex = response.idm.toHexString().uppercase()
            val responsePmmHex = response.pmm.toHexString().uppercase()

            return buildString {
                    appendLine("Polling with trailing data: supported")
                    appendLine("Command length: $commandLength bytes")
                    appendLine("Trailing data: ${POLLING_TRAILING_DATA_PROBE_BYTES.toHexString()}")
                    appendLine("Response IDM: $responseIdmHex")
                    appendLine("Response PMM: $responsePmmHex")
                }
                .trim()
        }

        scanContext = scanContext.copy(pollingCommandTrailingDataSupported = false)

        return buildString {
                appendLine("Polling with trailing data: not supported")
                appendLine("Command length: $commandLength bytes")
                appendLine("Trailing data: ${POLLING_TRAILING_DATA_PROBE_BYTES.toHexString()}")
                appendLine("No response after $POLLING_TRAILING_DATA_PROBE_ATTEMPTS attempts")
            }
            .trim()
    }

    private suspend fun executeRequestCodeList(target: FeliCaTarget): String {
        val systemContext = scanContext.systemScanContexts.firstOrNull()

        val index = 1
        val requestCodeListResponse =
            executeWithRetries(
                target,
                RequestCodeListCommand(target.idm, Area.ROOT, index),
                systemCode = systemContext?.systemCode,
            )
        val systemCodeHex = systemCodeLabel(systemContext?.systemCode)

        return buildString {
                appendLine("Request Code List command is supported (response received)")
                appendLine("System: $systemCodeHex")
                appendLine("Parent node: ${Area.ROOT.code.toHexString().uppercase()}")
                appendLine("Index: $index")
                appendLine("Status: ${formatStatus(requestCodeListResponse)}")
                appendLine(
                    "Returned ${requestCodeListResponse.areas.size} area(s), ${requestCodeListResponse.services.size} service(s)"
                )
                appendLine("Continue flag: ${requestCodeListResponse.continueFlag}")
                if (!requestCodeListResponse.isStatusSuccessful) {
                    appendLine(
                        "Note: Response status is not successful, but command support is confirmed."
                    )
                }
            }
            .trim()
    }

    private suspend fun executeSearchServiceCodeDetermineSupported(target: FeliCaTarget): String {
        val systemContext = scanContext.systemScanContexts.firstOrNull()

        val index = 0
        val searchServiceCodeResponse =
            executeWithRetries(
                target,
                SearchServiceCodeCommand(target.idm, index),
                systemCode = systemContext?.systemCode,
                maxAttempts = ATTEMPTS_DETERMINE_SUPPORTED,
            )
        val systemCodeHex = systemCodeLabel(systemContext?.systemCode)
        val node = searchServiceCodeResponse.node

        return buildString {
                appendLine("Search Service Code command is supported (response received)")
                appendLine("System: $systemCodeHex")
                appendLine("Index: $index")
                appendLine("Node: ${describeNodeForDiscovery(node)}")
            }
            .trim()
    }

    private suspend fun discoverNodesWithRequestCodeList(
        target: FeliCaTarget
    ): NodeDiscoveryResult {
        val details = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        val systemContexts = ensureNodeDiscoverySystemContexts(target)

        for ((contextIndex, systemContext) in systemContexts.withIndex()) {
            val systemCodeHex = systemCodeLabel(systemContext.systemCode)

            try {
                pollSystemCode(target, systemContext.systemCode)

                val areas = mutableListOf<Area>()
                val services = mutableListOf<Service>()
                var requestCount = 0
                var stopReason = "completed"

                for (index in 1..RequestCodeListCommand.MAX_ITERATOR_INDEX) {
                    val requestCodeListCommand =
                        RequestCodeListCommand(target.idm, Area.ROOT, index)
                    val requestCodeListResponse = target.transceive(requestCodeListCommand)
                    requestCount++

                    if (!requestCodeListResponse.isStatusSuccessful) {
                        stopReason =
                            "status ${formatStatus(requestCodeListResponse)} at index $index"
                        ScanLog.d(
                            "CardScanService",
                            "RequestCodeList error at index $index for system $systemCodeHex: ${formatStatus(requestCodeListResponse)}",
                        )
                        break
                    }

                    areas.addAll(requestCodeListResponse.areas)
                    services.addAll(requestCodeListResponse.services)

                    if (!requestCodeListResponse.continueFlag) {
                        ScanLog.d(
                            "CardScanService",
                            "RequestCodeList completed at index $index for system $systemCodeHex, continueFlag=false",
                        )
                        break
                    }
                }

                val nodes = normalizeDiscoveredNodes(areas + services)
                updatedSystemContexts.add(
                    systemContext.copy(nodes = nodes, registryPopulatedNodes = emptySet())
                )
                details.add(
                    "System ${contextIndex + 1} ($systemCodeHex): Request Code List found ${areas.size} area(s), ${services.size} service(s) in $requestCount request(s); $stopReason"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                ScanLog.w(
                    "CardScanService",
                    "Request Code List discovery failed for system $systemCodeHex",
                    e,
                )
                updatedSystemContexts.add(systemContext)
                details.add(
                    "System ${contextIndex + 1} ($systemCodeHex): Request Code List discovery failed (${e.message ?: e::class.simpleName ?: "Unknown error"})"
                )
            }
        }

        return NodeDiscoveryResult(
            methodLabel = "Request Code List",
            systemContexts = updatedSystemContexts,
            details = details,
        )
    }

    private suspend fun discoverNodesWithSearchServiceCode(
        target: FeliCaTarget
    ): NodeDiscoveryResult {
        val details = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        val systemContexts = ensureNodeDiscoverySystemContexts(target)

        for ((contextIndex, systemContext) in systemContexts.withIndex()) {
            val systemCodeHex = systemCodeLabel(systemContext.systemCode)

            try {
                val nodes = mutableListOf<Node>()
                var requestCount = 0
                var stopReason = "completed"

                for (index in 0x0000..SearchServiceCodeCommand.MAX_ITERATOR_INDEX) {
                    val parsedSearchResponse =
                        executeWithRetries(
                            target = target,
                            commandLabel = "SearchServiceCodeCommand",
                            systemCode = systemContext.systemCode,
                        ) { activeTarget, _ ->
                            SearchServiceCodeCommand(activeTarget.idm, index)
                        }
                    requestCount++

                    val node = parsedSearchResponse.node
                    nodes.add(node)

                    if (node is System) {
                        ScanLog.d(
                            "CardScanService",
                            "Found system node at index $index for system $systemCodeHex, stopping iteration",
                        )
                        stopReason = "system node at index $index"
                        break
                    }
                }

                val normalizedNodes = normalizeDiscoveredNodes(nodes)
                val areaCount = normalizedNodes.filterIsInstance<Area>().size
                val serviceCount = normalizedNodes.filterIsInstance<Service>().size
                updatedSystemContexts.add(
                    systemContext.copy(nodes = normalizedNodes, registryPopulatedNodes = emptySet())
                )
                details.add(
                    "System ${contextIndex + 1} ($systemCodeHex): Search Service Code found $areaCount area(s), $serviceCount service(s) in $requestCount request(s); $stopReason"
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                ScanLog.w(
                    "CardScanService",
                    "Search Service Code discovery failed for system $systemCodeHex",
                    e,
                )
                updatedSystemContexts.add(systemContext)
                details.add(
                    "System ${contextIndex + 1} ($systemCodeHex): Search Service Code discovery failed (${e.message ?: e::class.simpleName ?: "Unknown error"})"
                )
            }
        }

        return NodeDiscoveryResult(
            methodLabel = "Search Service Code",
            systemContexts = updatedSystemContexts,
            details = details,
        )
    }

    private suspend fun executeDiscoverNodes(target: FeliCaTarget): Pair<String, String> {
        val requestCodeListSupported =
            scanContext.requestCodeListSupport == CommandSupport.SUPPORTED
        val searchServiceCodeSupported =
            scanContext.searchServiceCodeSupport == CommandSupport.SUPPORTED
        if (requestCodeListSupported || searchServiceCodeSupported) {
            ensureCardPresence(target, "discover_nodes")
        }
        val details = mutableListOf<String>()

        val discoveryResult =
            when {
                requestCodeListSupported -> {
                    val requestCodeListResult = discoverNodesWithRequestCodeList(target)
                    details.addAll(requestCodeListResult.details)

                    if (
                        !requestCodeListResult.systemContexts.any { context ->
                            hasNonStructuralNodes(context.nodes)
                        } && searchServiceCodeSupported
                    ) {
                        details.add(
                            "Request Code List did not find non-structural nodes; retrying with Search Service Code"
                        )
                        val searchServiceCodeResult = discoverNodesWithSearchServiceCode(target)
                        details.addAll(searchServiceCodeResult.details)
                        searchServiceCodeResult
                    } else {
                        requestCodeListResult
                    }
                }
                searchServiceCodeSupported -> {
                    val searchServiceCodeResult = discoverNodesWithSearchServiceCode(target)
                    details.addAll(searchServiceCodeResult.details)
                    searchServiceCodeResult
                }
                else -> {
                    val systemContexts = ensureNodeDiscoverySystemContexts(target)
                    details.add(
                        "No supported node discovery command; using known-node fallback where available"
                    )
                    NodeDiscoveryResult(
                        methodLabel = "Known Node Fallback",
                        systemContexts = systemContexts,
                        details = emptyList(),
                    )
                }
            }

        val fallbackAllowed = !requestCodeListSupported && !searchServiceCodeSupported
        val (finalSystemContexts, fallbackSystems) =
            if (fallbackAllowed) {
                applyKnownNodeFallbacks(
                    discoveryResult.systemContexts,
                    force = true,
                    details = details,
                )
            } else {
                discoveryResult.systemContexts to 0
            }

        scanContext = scanContext.copy(systemScanContexts = finalSystemContexts)

        val allDiscoveredNodes = finalSystemContexts.flatMap { it.nodes }
        val areas = allDiscoveredNodes.filterIsInstance<Area>()
        val services = allDiscoveredNodes.filterIsInstance<Service>()
        val systems = allDiscoveredNodes.filterIsInstance<System>()
        val fallbackSummary =
            if (fallbackSystems > 0) {
                ", fallback populated: $fallbackSystems"
            } else {
                ""
            }
        val collapsedResult =
            "Found ${areas.size} areas, ${services.size} services across ${finalSystemContexts.size} system(s) using ${discoveryResult.methodLabel}$fallbackSummary"

        val expandedResult =
            buildString {
                    appendLine("Discover Nodes Results:")
                    appendLine("Method: ${discoveryResult.methodLabel}")
                    appendLine("Request Code List support: ${scanContext.requestCodeListSupport}")
                    appendLine(
                        "Search Service Code support: ${scanContext.searchServiceCodeSupport}"
                    )
                    appendLine()

                    if (details.isNotEmpty()) {
                        appendLine("Discovery Log:")
                        details.forEach { detail -> appendLine("  - $detail") }
                        appendLine()
                    }

                    finalSystemContexts.forEachIndexed { index, context ->
                        val contextAreas = context.nodes.filterIsInstance<Area>()
                        val contextServices = context.nodes.filterIsInstance<Service>()
                        val contextSystems = context.nodes.filterIsInstance<System>()

                        appendLine(
                            "System Context ${index + 1} (${systemCodeLabel(context.systemCode)}):"
                        )
                        appendLine("  Areas (${contextAreas.size}):")
                        contextAreas.forEach { area ->
                            appendLine(
                                "    - ${describeNodeForDiscovery(area)}: Range ${area.number}-${area.endNumber}"
                            )
                        }
                        if (contextAreas.isEmpty()) appendLine("    - None")

                        appendLine("  Services (${contextServices.size}):")
                        contextServices.forEach { service ->
                            appendLine(
                                "    - ${describeNodeForDiscovery(service)}: ${service.attribute::class.simpleName}"
                            )
                        }
                        if (contextServices.isEmpty()) appendLine("    - None")

                        appendLine("  Systems (${contextSystems.size}):")
                        contextSystems.forEach { system ->
                            appendLine("    - ${describeNodeForDiscovery(system)}")
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
        if (nodeMetadataProvider.isReady() && nodeMetadataProvider.isSystemCodeKnown(hex)) {
            val nodeDefinitions = nodeMetadataProvider.getNodesForSystemCode(hex)
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
                // Sort unique nodes by code (excluding System/root, which will be prepended).
                val sortedNodes = nodes.distinct().sortedBy { it.number }
                return normalizeDiscoveredNodes(sortedNodes)
            }
        }

        // Fallback: for unknown system codes, return System and root Area
        return listOf(System, Area.ROOT)
    }

    private suspend fun executeRequestServiceDetermineSupported(target: FeliCaTarget): String {
        val systemContext = scanContext.systemScanContexts.firstOrNull()
        var requestedNodeCodes: Array<ByteArray> = emptyArray()

        val requestServiceResponse =
            executeWithRetries(
                target = target,
                commandLabel = "RequestServiceCommand",
                systemCode = systemContext?.systemCode,
                maxAttempts = ATTEMPTS_DETERMINE_SUPPORTED,
            ) { activeTarget, attempt ->
                requestedNodeCodes =
                    when (attempt) {
                        // Heuristics
                        // For some reason, the special Octopus variant (IC 24) succeeds more if
                        // we try different values when re-attempting the command
                        1 -> arrayOf(System.code)
                        2 -> arrayOf(Area.ROOT.code)
                        else -> arrayOf(System.code, Area.ROOT.code)
                    }
                RequestServiceCommand(activeTarget.idm, requestedNodeCodes)
            }

        return buildString {
                appendLine("Request Service command is supported (response received)")
                appendLine("System: ${systemCodeLabel(systemContext?.systemCode)}")
                appendLine("Nodes:")
                requestedNodeCodes.zip(requestServiceResponse.keyVersions).forEach {
                    (nodeCode, keyVersion) ->
                    val nodeLabel =
                        when {
                            nodeCode.contentEquals(System.code) -> "System"
                            nodeCode.contentEquals(Area.ROOT.code) -> "Root Area"
                            else -> "Node"
                        }
                    appendLine(
                        "  ${nodeCode.toHexString().uppercase()} ($nodeLabel): ${
                        if (keyVersion.isMissing) {
                            "Not found"
                        } else {
                            keyVersion.toInt().toString()
                        }
                    }"
                    )
                }
            }
            .trim()
    }

    private suspend fun executeRequestServiceKeyVersions(
        target: FeliCaTarget
    ): Pair<String, String> {
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
        ensureCardPresence(target, "get_node_key_versions")

        // Get key versions in batches (max 32 nodes per request)
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
                val requestServiceResponse =
                    executeWithRetries(target = target, command = requestServiceCommand)

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
            val filteredNodes = systemNodes.filter { node ->
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
            "Got node key versions for ${areas.size} areas, ${services.size} services across ${updatedSystemContexts.size} system(s)"
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
        ensureCardPresence(target, "request_service_determine_unknown_node_attributes_supported")

        val systemContext = scanContext.systemScanContexts.firstOrNull()
        val unknownAttributeValue = resolveUnknownNodeAttributeValue()
        val unknownAttributeNodeCodeValue =
            (REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_SERVICE_NUMBER shl 6) or unknownAttributeValue
        val unknownAttributeNodeCode =
            byteArrayOf(
                (unknownAttributeNodeCodeValue and 0xFF).toByte(),
                ((unknownAttributeNodeCodeValue shr 8) and 0xFF).toByte(),
            )

        val response =
            try {
                executeWithRetries(
                    target = target,
                    commandLabel = "RequestServiceCommand",
                    systemCode = systemContext?.systemCode,
                    maxAttempts = REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS,
                    retryDelayStepMs = 50,
                ) { activeTarget, _ ->
                    RequestServiceCommand(activeTarget.idm, arrayOf(unknownAttributeNodeCode))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                null
            }
        val responseReceived = response != null

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
                appendLine("Supported = $responseReceived")
                if (response != null) {
                    val keyVersionHex =
                        response.keyVersions.first().toByteArray().toHexString().uppercase()
                    appendLine("Key version: $keyVersionHex")
                } else {
                    appendLine(
                        "No response after $REQUEST_SERVICE_UNKNOWN_ATTRIBUTE_PROBE_ATTEMPTS attempts"
                    )
                }
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

        val preferredTarget = findBestAuthentication1DesTarget { mode -> mode == Mode.Mode0 }
        val targetForErrorMessage = preferredTarget ?: findBestAuthentication1DesTarget()

        if (targetForErrorMessage == null) {
            throw PrerequisiteException(
                "No suitable system found for Authenticate1 DES node-list hierarchy validation (root area with valid DES key is required)."
            )
        }

        if (preferredTarget == null) {
            throw PrerequisiteException(
                "Authenticate1 DES node-list hierarchy validation requires Mode 0 on the selected system (current: ${targetForErrorMessage.systemContext.mode})."
            )
        }

        val nonImmediateNode = findAuthentication1DesNonImmediateNode(preferredTarget)
        if (nonImmediateNode == null) {
            throw PrerequisiteException(
                "No node found under an area under root area; cannot check Authenticate1 DES node-list hierarchy validation."
            )
        }
        ensureCardPresence(target, "authentication1_des_node_list_hierarchy_validation")

        val challenge1A = ByteArray(8) { 0x00.toByte() }
        val areasToAuth = listOf(preferredTarget.rootArea)
        // Area0 may appear in both lists: this is allowed because key updates can target areas.
        val nodesToAuth = listOf<Node>(preferredTarget.rootArea, nonImmediateNode)

        var selectedSystemIdmUsed: ByteArray? = null

        val response =
            try {
                executeWithRetries(
                    target = target,
                    commandLabel = "Authentication1DesCommand",
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
            setSystemMode(preferredTarget.systemContext.systemCode, Mode.Mode1.Des)
        }
        val responseReceived = response != null

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
            scanContext.copy(authentication1DesNodeListHierarchyValidation = validationBehavior)

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
                if (response != null) {
                    appendLine("Challenge1B: ${response.challenge1B.toHexString().uppercase()}")
                    appendLine("Challenge2A: ${response.challenge2A.toHexString().uppercase()}")
                } else {
                    appendLine(
                        "No response after $AUTHENTICATION1_DES_NODE_LIST_HIERARCHY_VALIDATION_ATTEMPTS attempts"
                    )
                }
                appendLine("Validation behavior: $validationBehavior")
                resetStateResult?.let {
                    appendLine()
                    appendLine("$it")
                }
            }
            .trim()
    }

    private suspend fun executeRequestServiceV2DetermineSupported(target: FeliCaTarget): String {
        val systemContext = scanContext.systemScanContexts.firstOrNull()

        val requestServiceV2Response =
            executeWithRetries(
                target,
                RequestServiceV2Command(target.idm, arrayOf(System.code)),
                systemCode = systemContext?.systemCode,
                maxAttempts = ATTEMPTS_DETERMINE_SUPPORTED,
            )
        val aesKeyVersion = requestServiceV2Response.aesKeyVersions.firstOrNull()
        val desKeyVersion = requestServiceV2Response.desKeyVersions.firstOrNull()

        return buildString {
                appendLine("Request Service V2 command is supported (response received)")
                appendLine("System: ${systemCodeLabel(systemContext?.systemCode)}")
                appendLine("Node: ${System.code.toHexString().uppercase()} (System)")
                appendLine("Status: ${formatStatus(requestServiceV2Response)}")
                requestServiceV2Response.encryptionIdentifier?.let { encryptionIdentifier ->
                    appendLine("Encryption Identifier: ${encryptionIdentifier.name}")
                }
                aesKeyVersion?.let { keyVersion ->
                    appendLine(
                        "AES System Key Version: ${
                            if (keyVersion.isMissing) {
                                "Not found"
                            } else {
                                keyVersion.toInt().toString()
                            }
                        }"
                    )
                }
                desKeyVersion?.let { keyVersion ->
                    appendLine(
                        "DES System Key Version: ${
                            if (keyVersion.isMissing) {
                                "Not found"
                            } else {
                                keyVersion.toInt().toString()
                            }
                        }"
                    )
                }
            }
            .trim()
    }

    private suspend fun executeGetNodeKeyVersions(target: FeliCaTarget): Pair<String, String> =
        when {
            scanContext.requestServiceV2Support == CommandSupport.SUPPORTED ->
                executeRequestServiceV2KeyVersions(target)
            scanContext.requestServiceSupport == CommandSupport.SUPPORTED ->
                executeRequestServiceKeyVersions(target)
            else ->
                throw PrerequisiteException(
                    "Get node key versions requires Request Service or Request Service V2 support"
                )
        }

    private suspend fun executeRequestServiceV2KeyVersions(
        target: FeliCaTarget
    ): Pair<String, String> {
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
        ensureCardPresence(target, "get_node_key_versions")

        // Get key versions in batches (max 32 nodes per request)
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
                val requestServiceV2Response =
                    executeWithRetries(target = target, command = requestServiceV2Command)

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

                        aesKeyVersion
                            ?.takeUnless { it.isMissing }
                            ?.let { keyVersion -> nodeAesKeyVersionsMap[node] = keyVersion }
                        desKeyVersion
                            ?.takeUnless { it.isMissing }
                            ?.let { keyVersion -> nodeDesKeyVersionsMap[node] = keyVersion }
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
            val filteredNodes = nodes.filter { node ->
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
            "Got enhanced node key versions for ${areas.size} areas, ${services.size} services across ${updatedSystemContexts.size} system(s)"
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
        ensureCardPresence(target, "force_discover_nodes")

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
                    ScanLog.w("CardScanService", "Force discover batch failed: ${e.message}")
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

    private suspend fun executeGetAreaInformation(target: FeliCaTarget): Pair<String, String> {
        val allAreas = scanContext.systemScanContexts.flatMap { it.nodes.filterIsInstance<Area>() }

        if (allAreas.isEmpty()) {
            throw RuntimeException(
                "No areas discovered. Get Area Information requires discovered areas from Discover Nodes step."
            )
        }
        ensureCardPresence(target, "get_area_information")

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

        return collapsedResult to expandedResult
    }

    private suspend fun executeGetContainerProperty(target: FeliCaTarget): String {
        ensureCardPresence(target, "get_container_property")

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
        ensureCardPresence(target, "set_parameter")

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
        ensureCardPresence(target, "get_container_issue_information")

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
                    printableBytes.joinToString(separator = "") { byte ->
                        byte.toInt().toChar().toString()
                    }
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
        ensureCardPresence(target, "get_platform_information")

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
        ensureCardPresence(target, "reset_mode")

        val resetModeCommand = ResetModeCommand(target.idm)
        val resetModeResponse = target.transceive(resetModeCommand)
        if (resetModeResponse.isStatusSuccessful) {
            setSystemModeByIdm(resetModeResponse.idm, Mode.Mode0)
        }

        return buildString {
                appendLine("Status Flags: ${formatStatus(resetModeResponse, prefix = "")}")

                // appendLine("Note: Reset Mode command resets the card's mode to Mode 0.")
                // appendLine("This command is supported by AES and AES/DES cards.")
            }
            .trim()
    }

    private suspend fun executeGetContainerId(target: FeliCaTarget): String {
        ensureCardPresence(target, "get_container_id")

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
        ensureCardPresence(target, "echo")

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
        ensureCardPresence(target, "internal_authenticate_and_read")

        val systemCodeHex = bestSystemContext.systemCode?.toHexString() ?: "unknown"
        val serviceCodeHex = bestMacService.code.toHexString()

        ScanLog.d(
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

        val response =
            executeWithRetries(
                target = target,
                commandLabel = "ReadWithoutEncryptionCommand",
                systemCode = testTarget.systemContext.systemCode,
            ) { activeTarget, _ ->
                ReadWithoutEncryptionCommand(
                    idm = activeTarget.idm,
                    serviceCodes = arrayOf(testService.code),
                    blockListElements = blocksToRead.toTypedArray(),
                )
            }
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
            ScanLog.w("CardScanService", fallbackMessage)

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
                    ScanLog.d("CardScanService", "Determined FLAG error indication (status1=0xFF)")
                    ErrorLocationIndication.FLAG
                }
                statusFlag1.toInt() and 0xFF == 0x04 -> {
                    ScanLog.d(
                        "CardScanService",
                        "Determined BITMASK error indication (status1=0x03)",
                    )
                    ErrorLocationIndication.BITMASK
                }
                statusFlag1.toInt() and 0xFF == 0x03 -> {
                    ScanLog.d(
                        "CardScanService",
                        "Determined NUMBER error indication (status1=0x01)",
                    )
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

        ScanLog.d("CardScanService", "Determined error indication type: $errorIndicationType")

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

            val response =
                executeWithRetries(
                    target = target,
                    commandLabel = "ReadWithoutEncryptionCommand",
                    systemCode = testTarget.systemContext.systemCode,
                ) { activeTarget, _ ->
                    ReadWithoutEncryptionCommand(
                        idm = activeTarget.idm,
                        serviceCodes = serviceCodes,
                        blockListElements = blockListElements,
                    )
                }
            if (response.isStatusSuccessful) {
                // Command succeeded, we found the maximum
                ScanLog.d(
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
                ScanLog.w(
                    "CardScanService",
                    "ReadWithoutEncryption returned unexpected status while determining max services, falling back to 1 service (${formatStatus(fallbackStatus1, fallbackStatus2)})",
                )
                break
            }

            ScanLog.d(
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

        val response =
            executeWithRetries(
                target = target,
                commandLabel = "ReadWithoutEncryptionCommand",
                systemCode = testTarget.systemContext.systemCode,
            ) { activeTarget, _ ->
                ReadWithoutEncryptionCommand(
                    idm = activeTarget.idm,
                    serviceCodes = serviceCodes,
                    blockListElements = blockListElements,
                )
            }
        val statusFlag1 = response.statusFlag1
        val statusFlag2 = response.statusFlag2

        if (response.isStatusSuccessful) {
            ScanLog.w(
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

        ScanLog.d(
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
            // Create block list elements for blocks 0 through (maxBlocks-1)
            val blockListElements =
                Array(maxBlocks) { blockIndex ->
                    BlockListElement(serviceCodeListOrder = 0, blockNumber = testBlockNumber)
                }

            try {
                val response =
                    executeWithRetries(
                        target = target,
                        commandLabel = "ReadWithoutEncryptionCommand",
                        systemCode = testTarget.systemContext.systemCode,
                    ) { activeTarget, _ ->
                        ReadWithoutEncryptionCommand(
                            idm = activeTarget.idm,
                            serviceCodes = arrayOf(testService.code),
                            blockListElements = blockListElements,
                        )
                    }
                if (response.isStatusSuccessful) {
                    // Command succeeded, we found the maximum
                    ScanLog.d(
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
                    ScanLog.w(
                        "CardScanService",
                        "ReadWithoutEncryption returned unexpected status while determining max blocks, falling back to 1 block (${formatStatus(fallbackStatus1, fallbackStatus2)})",
                    )
                    break
                }
                ScanLog.d(
                    "CardScanService",
                    "ReadWithoutEncryption failed with $maxBlocks blocks, ${formatStatus(response)}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                // Card may not respond if command is too large (e.g., FeliCa Lite)
                // Retry helper checks card availability before continuing with a smaller size.
                ScanLog.d(
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
        ensureCardPresence(target, "write_without_encryption_determine_error_indication")

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

        val response =
            executeWithRetries(
                target = target,
                commandLabel = "WriteWithoutEncryptionCommand",
                systemCode = serviceInfo.systemCode,
            ) { activeTarget, _ ->
                WriteWithoutEncryptionCommand(
                    idm = activeTarget.idm,
                    serviceCodes = arrayOf(writableService.code),
                    blockListElements = blocksToWrite.toTypedArray(),
                    blockData = blockData,
                )
            }
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
                    ScanLog.d("CardScanService", "Determined FLAG error indication (status1=0xFF)")
                    ErrorLocationIndication.FLAG
                }
                statusFlag1.toInt() and 0xFF == 0x04 -> {
                    ScanLog.d(
                        "CardScanService",
                        "Determined BITMASK error indication (status1=0x04)",
                    )
                    ErrorLocationIndication.BITMASK
                }
                statusFlag1.toInt() and 0xFF == 0x03 -> {
                    ScanLog.d("CardScanService", "Determined INDEX error indication (status1=0x03)")
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

        ScanLog.d("CardScanService", "Determined error indication type: $errorIndicationType")

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
        ensureCardPresence(target, "write_without_encryption_determine_max_blocks")

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

            try {
                val response =
                    executeWithRetries(
                        target = target,
                        commandLabel = "WriteWithoutEncryptionCommand",
                        systemCode = serviceInfo.systemCode,
                    ) { activeTarget, _ ->
                        WriteWithoutEncryptionCommand(
                            idm = activeTarget.idm,
                            serviceCodes = arrayOf(writableService.code),
                            blockListElements = blockListElements,
                            blockData = blockData,
                        )
                    }
                if (response.isStatusSuccessful) {
                    // Command succeeded, we found the maximum
                    ScanLog.d(
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
                ScanLog.d(
                    "CardScanService",
                    "WriteWithoutEncryption failed with $maxBlocks blocks, ${formatStatus(response)}",
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: TagUnavailableException) {
                throw e
            } catch (e: Exception) {
                // Card may not respond if command is too large (e.g., FeliCa Lite)
                // Retry helper checks card availability before continuing with a smaller size.
                ScanLog.d(
                    "CardScanService",
                    "WriteWithoutEncryption got no response with $maxBlocks blocks: ${e.message}",
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
        val writableServices = allServices.filter { service ->
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

        ScanLog.d(
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
        ensureCardPresence(target, "read_blocks_without_encryption")

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
                        nodeMetadataProvider.getExtraBlocks(systemCodeHexForLookup, serviceCodeHex)
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
        ensureCardPresence(target, "force_discover_blocks")

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

    private suspend fun executeGetNodePropertyValueLimitedService(
        target: FeliCaTarget
    ): Pair<String, String> {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allDiscoveredNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Get Node Property (Value-Limited Service) requires discovered nodes from Discover Nodes step."
            )
        }
        ensureCardPresence(target, "get_node_property_value_limited_service")

        var errors = 0
        val results = mutableListOf<String>()
        val maxNodesPerRequest = 16 // FeliCa specification limit
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalProperties = 0
        var enabledProperties = 0

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
                            totalProperties++
                            if (property.enabled) {
                                enabledProperties++
                            }
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

        val collapsedResult =
            "Value-limited purse properties: $enabledProperties enabled / $totalProperties returned for ${allDiscoveredNodes.size} node(s)"
        val expandedResult =
            buildString {
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

        return collapsedResult to expandedResult
    }

    private suspend fun executeGetNodePropertyMacCommunication(
        target: FeliCaTarget
    ): Pair<String, String> {
        val allDiscoveredNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allDiscoveredNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Get Node Property (MAC Communication) requires discovered nodes from Discover Nodes step."
            )
        }
        ensureCardPresence(target, "get_node_property_mac_communication")

        var errors = 0
        val results = mutableListOf<String>()
        val maxNodesPerRequest = 16 // FeliCa specification limit
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalProperties = 0
        var enabledProperties = 0

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
                            totalProperties++
                            if (property.enabled) {
                                enabledProperties++
                            }
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

        val collapsedResult =
            "MAC communication properties: $enabledProperties enabled / $totalProperties returned for ${allDiscoveredNodes.size} node(s)"
        val expandedResult =
            buildString {
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

        return collapsedResult to expandedResult
    }

    private suspend fun executeRequestBlockInformation(target: FeliCaTarget): Pair<String, String> {
        val allNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Request Block Information requires discovered nodes from Discover Nodes step."
            )
        }
        ensureCardPresence(target, "request_block_information")

        // Request block information in batches (max 32 services per request as per FeliCa spec)
        val maxServicesPerRequest = 32
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalBlockCountsRetrieved = 0

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
                    totalBlockCountsRetrieved++
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

        val collapsedResult =
            "Loaded block counts for $totalBlockCountsRetrieved/${allNodes.size} node(s) across ${updatedSystemContexts.size} system(s)"
        val expandedResult =
            buildString {
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

        return collapsedResult to expandedResult
    }

    private suspend fun executeRequestBlockInformationEx(
        target: FeliCaTarget
    ): Pair<String, String> {
        val allNodes = scanContext.systemScanContexts.flatMap { it.nodes }

        if (allNodes.isEmpty()) {
            throw RuntimeException(
                "No nodes discovered. Request Block Information Ex requires discovered nodes from Discover Nodes step."
            )
        }
        ensureCardPresence(target, "request_block_information_ex")

        // Request block information in batches (max 32 services per request as per FeliCa spec)
        val maxServicesPerRequest = 16
        val results = mutableListOf<String>()
        val updatedSystemContexts = mutableListOf<SystemScanContext>()
        var totalBlockCountsRetrieved = 0

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
                        totalBlockCountsRetrieved++
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

        val collapsedResult =
            "Loaded extended block counts for $totalBlockCountsRetrieved/${allNodes.size} node(s) across ${updatedSystemContexts.size} system(s)"
        val expandedResult =
            buildString {
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

        return collapsedResult to expandedResult
    }

    private suspend fun executeAuthentication1DesDetermineSupported(target: FeliCaTarget): String {
        val testTarget = findBestAuthentication1DesTarget()
        if (testTarget == null) {
            throw RuntimeException(
                "No suitable system found for DES authentication (root area with valid DES key is required)."
            )
        }
        ensureCardPresence(target, "authentication1_des_determine_supported")

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
            executeWithRetries(
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
        setSystemMode(testTarget.systemContext.systemCode, Mode.Mode1.Des)

        val resetResult =
            resetAuthenticationState(
                target = target,
                authenticatedSystemCode = testTarget.systemContext.systemCode,
                authenticatedSystemIdm = selectedSystemIdmUsed,
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
            val aesCompatibleServices = authServices.filter { service ->
                systemAesKeyVersions.containsKey(service)
            }

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
        ensureCardPresence(target, "authentication1_aes")

        val systemCodeHex = bestSystemContext.systemCode?.toHexString() ?: "unknown"
        ScanLog.d(
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
        val constMs = formatTwoDecimals(constant.inWholeNanoseconds / 1_000_000.0)
        val perUnitMs = formatTwoDecimals(perUnit.inWholeNanoseconds / 1_000_000.0)
        return "${constMs} + (${perUnitMs} * n) ms"
    }

    private fun formatTwoDecimals(value: Double): String {
        val scaled = kotlin.math.round(value * 100).toLong()
        val whole = scaled / 100
        val fraction = (scaled % 100).toString().padStart(2, '0')
        return "$whole.$fraction"
    }
}
