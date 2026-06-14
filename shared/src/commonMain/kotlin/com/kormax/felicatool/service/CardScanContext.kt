package com.kormax.felicatool.service

import com.kormax.felicatool.felica.CommunicationPerformance
import com.kormax.felicatool.felica.ContainerInformation
import com.kormax.felicatool.felica.CountInformation
import com.kormax.felicatool.felica.EncryptionIdentifier
import com.kormax.felicatool.felica.GetContainerPropertyCommand
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
    val commands: CommandCapabilities = CommandCapabilities(),
    val specificationVersion: SpecificationVersion? = null,
    val containerIssueInformation: ContainerInformation? = null,
    val productInformation: RequestProductInformationResponse? = null,
    val containerIdm: ByteArray? = null,
    val containerPropertyValues: Map<GetContainerPropertyCommand.Property, ByteArray> = emptyMap(),
    val communicationLog: List<CommunicationLogEntry> = emptyList(),
) {
    inline fun withCommands(
        transform: CommandCapabilities.() -> CommandCapabilities
    ): CardScanContext = copy(commands = commands.transform())
}

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
