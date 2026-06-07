package com.kormax.felicatool.service

import com.kormax.felicatool.felica.Authentication1DesNodeListHierarchyValidation
import com.kormax.felicatool.felica.CommunicationPerformance
import com.kormax.felicatool.felica.ContainerInformation
import com.kormax.felicatool.felica.CountInformation
import com.kormax.felicatool.felica.EncryptionIdentifier
import com.kormax.felicatool.felica.ErrorLocationIndication
import com.kormax.felicatool.felica.GetContainerPropertyCommand
import com.kormax.felicatool.felica.IllegalNumberErrorPreference
import com.kormax.felicatool.felica.KeyVersion
import com.kormax.felicatool.felica.MacCommunicationProperty
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.Pmm
import com.kormax.felicatool.felica.RequestProductInformationResponse
import com.kormax.felicatool.felica.SpecificationVersion
import com.kormax.felicatool.felica.ValueLimitedPurseServiceProperty
import com.kormax.felicatool.service.logging.CommunicationLogEntry

/** Context class to store discovered card data across multiple scan steps. */
data class CardScanContext(
    val systemScanContexts: List<SystemScanContext> = emptyList(),
    val scanDurationMillis: Long? = null,
    val primaryIdm: ByteArray? = null,
    val pmm: Pmm? = null,
    val primarySystemCode: ByteArray? = null,
    val discoveredSystemCodes: List<ByteArray> = emptyList(),
    val communicationPerformance: CommunicationPerformance? = null,
    val pollingCommandTrailingDataSupported: Boolean? = null,
    val specificationVersion: SpecificationVersion? = null,
    val containerIssueInformation: ContainerInformation? = null,
    val productInformation: RequestProductInformationResponse? = null,
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
    val requestProductInformationSupport: CommandSupport = CommandSupport.UNKNOWN,
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
    val containerPropertyValues: Map<GetContainerPropertyCommand.Property, ByteArray> = emptyMap(),
    val communicationLog: List<CommunicationLogEntry> = emptyList(),
)

data class SystemScanContext(
    val systemCode: ByteArray? = null,
    val nodes: List<Node> = emptyList(),
    /** Set of nodes that were populated from the registry (not discovered via search). */
    val registryPopulatedNodes: Set<Node> = emptySet(),
    val nodeKeyVersions: Map<Node, KeyVersion> = emptyMap(),
    val nodeAesKeyVersions: Map<Node, KeyVersion> = emptyMap(),
    val nodeDesKeyVersions: Map<Node, KeyVersion> = emptyMap(),
    val encryptionIdentifier: EncryptionIdentifier? = null,
    val nodeBlockCounts: Map<Node, CountInformation> = emptyMap(),
    val nodeAssignedBlockCounts: Map<Node, CountInformation> = emptyMap(),
    val nodeFreeBlockCounts: Map<Node, CountInformation> = emptyMap(),
    /** Block data stored as Map<BlockNumber, BlockData> for each node. */
    val serviceBlockData: Map<Node, Map<Int, ByteArray>> = emptyMap(),
    val nodeValueLimitedPurseProperties: Map<Node, ValueLimitedPurseServiceProperty> = emptyMap(),
    val nodeMacCommunicationProperties: Map<Node, MacCommunicationProperty> = emptyMap(),
    val systemStatus: ByteArray? = null,
    val idm: ByteArray? = null,
    /** Set of nodes discovered via force discovery that were not found in regular discovery. */
    val hiddenNodes: Set<Node> = emptySet(),
)
