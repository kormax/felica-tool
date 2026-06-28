package com.kormax.felicatool.util

import com.kormax.felicatool.felica.Area
import com.kormax.felicatool.felica.Node
import com.kormax.felicatool.felica.Pmm
import com.kormax.felicatool.felica.Service
import com.kormax.felicatool.felica.System
import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.SystemScanContext

data class CardTypeInference(
    val cardId: String?,
    val media: CardTypeMedia,
    val possibleCardCount: Int? = null,
)

enum class CardTypeMedia {
    PHYSICAL_CARD,
    APPLE_WALLET,
    OSAIFU_KEITAI,
}

object CardTypeInferrer {
    private const val APPLE_WALLET_ROM = 0x01
    private const val APPLE_WALLET_IC = 0x16
    private const val OSAIFU_KEITAI_ROM = 0x01
    private const val OSAIFU_KEITAI_IC = 0x18
    private const val OSAIFU_KEITAI_PROVIDER = "OSAIFU_KEITAI"
    private const val FS_EXACT = 0
    private const val FS_OBSERVED_SYSTEMS = 1
    private const val FS_NONE = 2

    fun infer(
        scanContext: CardScanContext,
        nodeMetadataProvider: NodeMetadataProvider = NodeRegistry,
    ): CardTypeInference? {
        if (!nodeMetadataProvider.isReady()) return null

        val observedNodes = scanContext.observedNodes()
        val observedFilesystem = scanContext.filesystemNodes()
        if (observedNodes.isEmpty() && observedFilesystem.isEmpty()) return null

        val systems = scanContext.systemCodes()
        val media = scanContext.detectMedia()
        val medium = media.cardMedium()
        val scannedDefs = nodeMetadataProvider.definitions(systems, medium)
        val nodeCandidateCards = observedNodes.flatMap { observed ->
            scannedDefs.filter { it.accepts(observed) }.flatMap { it.node.cards }
        }
        val systemCandidateCards = systems.flatMap {
            nodeMetadataProvider.getCardsForSystemCode(it)
        }
        val candidateCards = (nodeCandidateCards + systemCandidateCards).toSortedSet()

        val scores = candidateCards.mapNotNull { card ->
            score(
                card = card,
                systems = systems,
                observedNodes = observedNodes,
                observedFilesystem = observedFilesystem,
                observedData = scanContext.serviceData(),
                medium = medium,
                provider = nodeMetadataProvider,
            )
        }
        val best = scores.minWithOrNull(scoreRankOrder) ?: return null
        val bestScores = scores.filter { scoreRankOrder.compare(it, best) == 0 }
        val inferredCardId =
            when (bestScores.size) {
                1 -> best.card
                else -> nodeMetadataProvider.commonCardAncestor(bestScores.mapToSet { it.card })
            }
        val possibleCardCount = bestScores.size.takeIf { inferredCardId == null && it > 1 }
        val useDeviceLabel = media.shouldUseDeviceLabel(scanContext)

        return CardTypeInference(
            cardId = if (useDeviceLabel) null else inferredCardId,
            media = media,
            possibleCardCount = possibleCardCount.takeUnless { useDeviceLabel },
        )
    }

    fun displayLabel(inference: CardTypeInference): String =
        when (inference.media) {
            CardTypeMedia.PHYSICAL_CARD ->
                inference.cardId?.let(::formatCardId)
                    ?: unknownCardLabel(inference.possibleCardCount)
            CardTypeMedia.APPLE_WALLET,
            CardTypeMedia.OSAIFU_KEITAI -> {
                val media = inference.media.displayLabel()
                when {
                    inference.cardId != null -> "${formatCardId(inference.cardId)} in $media"
                    inference.possibleCardCount != null ->
                        "${unknownCardLabel(inference.possibleCardCount)} in $media"
                    else -> "$media Device"
                }
            }
        }

    fun formatCardId(cardId: String): String =
        cardId
            .trim()
            .uppercase()
            .split("_")
            .filter { it.isNotBlank() }
            .joinToString(" ") { it.lowercase().replaceFirstChar { char -> char.uppercase() } }

    fun unknownCardLabel(possibleCardCount: Int?): String =
        possibleCardCount?.let { "Unknown ($it possible)" } ?: "Unknown"

    private fun score(
        card: String,
        systems: Set<String>,
        observedNodes: Set<NodeKey>,
        observedFilesystem: Set<FilesystemNodeId>,
        observedData: List<ServiceData>,
        medium: CardMedium,
        provider: NodeMetadataProvider,
    ): CandidateScore? {
        val expectedSystems = provider.getSystemCodesForCard(card)
        val expectedDefs =
            provider.definitions(expectedSystems, medium).filter {
                card in it.node.cards
            }
        val expectedNodes = expectedDefs.map { it.key }.toSet()
        if (expectedSystems.isEmpty()) return null

        val (dataMatches, dataMismatches) = expectedDefs.dataScore(observedData)
        val score =
            CandidateScore(
                card = card,
                filesystemRank = provider.filesystemRank(card, observedFilesystem, systems),
                missingSystems = expectedSystems.count { it !in systems },
                missingNodes =
                    expectedNodes.count { expected ->
                        expected.system in systems && observedNodes.none { expected.accepts(it) }
                    },
                dataMatches = dataMatches,
                dataMismatches = dataMismatches,
                hits =
                    observedNodes.count { observed -> expectedNodes.any { it.accepts(observed) } },
                extraNodes =
                    observedNodes.count { observed -> expectedNodes.none { it.accepts(observed) } },
            )
        return score.takeIf { it.isPresent }
    }

    private fun NodeMetadataProvider.definitions(
        systems: Set<String>,
        medium: CardMedium,
    ): List<DefinitionRef> = systems.flatMap { system ->
        getNodesForSystemCode(system)
            .filter { it.type != NodeDefinitionType.AREA || it.code.uppercase() != "0000FEFF" }
            .filter { it.mediums.isEmpty() || medium in it.mediums }
            .map { DefinitionRef(system, it) }
    }

    private fun NodeMetadataProvider.filesystemRank(
        card: String,
        observed: Set<FilesystemNodeId>,
        observedSystems: Set<String>,
    ): Int =
        if (observed.isEmpty()) {
            FS_NONE
        } else {
            getFilesystemSnapshotsForCard(card).minOfOrNull { snapshot ->
                val observedSystemsSnapshot =
                    snapshot.filter { it.systemCode in observedSystems }.toSet()
                when {
                    snapshot == observed -> FS_EXACT
                    observedSystemsSnapshot.isNotEmpty() && observedSystemsSnapshot == observed ->
                        FS_OBSERVED_SYSTEMS
                    else -> FS_NONE
                }
            } ?: FS_NONE
        }

    private fun List<DefinitionRef>.dataScore(observedData: List<ServiceData>): Pair<Int, Int> {
        var matches = 0
        var mismatches = 0
        for (definition in this) {
            val patterns = definition.node.blockDataPatterns
            if (patterns.isEmpty()) continue
            val observed = observedData.firstOrNull { definition.accepts(it) } ?: continue
            if (patterns.all { it.matches(observed.blocks) }) matches++ else mismatches++
        }
        return matches to mismatches
    }

    private fun CardTypeMedia.shouldUseDeviceLabel(scanContext: CardScanContext): Boolean =
        this == CardTypeMedia.OSAIFU_KEITAI &&
            ServicePresenceAnalyzer.detectProviders(scanContext)
                .providers
                .map { it.provider.trim().uppercase() }
                .count { it != OSAIFU_KEITAI_PROVIDER } > 1

    private fun CardScanContext.detectMedia(): CardTypeMedia =
        when {
            pmm.hasCombo(APPLE_WALLET_ROM, APPLE_WALLET_IC) -> CardTypeMedia.APPLE_WALLET
            pmm.hasCombo(OSAIFU_KEITAI_ROM, OSAIFU_KEITAI_IC) ||
                supportsMobileContainerCommands() -> CardTypeMedia.OSAIFU_KEITAI
            else -> CardTypeMedia.PHYSICAL_CARD
        }

    private fun CardScanContext.supportsMobileContainerCommands(): Boolean =
        containerIssueInformation != null ||
            containerIdm != null ||
            commands.getContainerIssueInformation.supported == CommandSupport.SUPPORTED ||
            commands.getContainerId.supported == CommandSupport.SUPPORTED

    private fun CardTypeMedia.cardMedium(): CardMedium =
        when (this) {
            CardTypeMedia.PHYSICAL_CARD -> CardMedium.CARD
            CardTypeMedia.APPLE_WALLET,
            CardTypeMedia.OSAIFU_KEITAI -> CardMedium.MOBILE
        }

    fun CardTypeMedia.displayLabel(): String =
        when (this) {
            CardTypeMedia.PHYSICAL_CARD -> "Physical Card"
            CardTypeMedia.APPLE_WALLET -> "Apple Wallet"
            CardTypeMedia.OSAIFU_KEITAI -> "Osaifu Keitai"
        }

    private fun CardScanContext.systemCodes(): Set<String> =
        systemScanContexts.mapNotNullTo(mutableSetOf()) { it.systemCodeHex() }

    private fun CardScanContext.observedNodes(): Set<NodeKey> =
        systemScanContexts
            .flatMap { context ->
                val system = context.systemCodeHex() ?: return@flatMap emptyList()
                context.nodes
                    .asSequence()
                    .filter { !it.isStructural() }
                    .filter { it !in context.registryPopulatedNodes || it.isPresent(context) }
                    .mapNotNull { it.toNodeKey(system, context) }
                    .toList()
            }
            .toSet()

    private fun CardScanContext.filesystemNodes(): Set<FilesystemNodeId> =
        systemScanContexts
            .flatMap { context ->
                val system = context.systemCodeHex() ?: return@flatMap emptyList()
                context.nodes
                    .asSequence()
                    .filter { it !is System }
                    .filter { it !in context.registryPopulatedNodes || it.isPresent(context) }
                    .mapNotNull { it.toFilesystemNodeId(system, context) }
                    .toList()
            }
            .toSet()

    private fun CardScanContext.serviceData(): List<ServiceData> =
        systemScanContexts.flatMap { context ->
            val system = context.systemCodeHex() ?: return@flatMap emptyList()
            context.serviceBlockData.mapNotNull { (node, blocks) ->
                val service = node as? Service ?: return@mapNotNull null
                ServiceData(system, service.code.hex(), service.parentCode(context), blocks)
            }
        }

    private fun Node.toNodeKey(system: String, context: SystemScanContext): NodeKey? =
        when (this) {
            is Area -> NodeKey(system, NodeDefinitionType.AREA, fullCode.hex(), parentCode(context))
            is Service ->
                NodeKey(system, NodeDefinitionType.SERVICE, code.hex(), parentCode(context))
            else -> null
        }

    private fun Node.toFilesystemNodeId(
        system: String,
        context: SystemScanContext,
    ): FilesystemNodeId? =
        when (this) {
            is Area -> FilesystemNodeId(system, parentCode(context), fullCode.hex())
            is Service -> FilesystemNodeId(system, parentCode(context), code.hex())
            else -> null
        }

    private fun Area.parentCode(context: SystemScanContext): String? =
        context.nodes
            .filterIsInstance<Area>()
            .filter { it != this && belongsTo(it) && !hasSameRangeButCannotNestIn(it) }
            .minWithOrNull(areaOrder)
            ?.fullCode
            ?.hex()

    private fun Service.parentCode(context: SystemScanContext): String? =
        context.nodes
            .filterIsInstance<Area>()
            .filter { belongsTo(it) }
            .minWithOrNull(areaOrder)
            ?.fullCode
            ?.hex()

    private fun Area.hasSameRangeButCannotNestIn(candidate: Area): Boolean =
        number == candidate.number &&
            endNumber == candidate.endNumber &&
            attribute.canCreateSubArea &&
            !candidate.attribute.canCreateSubArea

    private fun Node.isPresent(context: SystemScanContext): Boolean =
        context.nodeKeyVersions.containsKey(this) ||
            context.nodeAesKeyVersions.containsKey(this) ||
            context.nodeDesKeyVersions.containsKey(this) ||
            context.nodeBlockCounts.containsKey(this) ||
            context.nodeAssignedBlockCounts.containsKey(this) ||
            context.nodeFreeBlockCounts.containsKey(this) ||
            context.serviceBlockData.containsKey(this) ||
            context.nodeValueLimitedPurseProperties.containsKey(this) ||
            context.nodeMacCommunicationProperties.containsKey(this)

    private fun Node.isStructural(): Boolean = this is System || (this is Area && isRoot)

    private fun SystemScanContext.systemCodeHex(): String? = systemCode?.hex()

    private fun ByteArray.hex(): String = toHexString().uppercase()

    private fun Pmm?.hasCombo(rom: Int, ic: Int): Boolean =
        this != null && romType.u8() == rom && icType.u8() == ic

    private fun Byte.u8(): Int = toInt() and 0xFF

    private data class DefinitionRef(val system: String, val node: NodeDefinition) {
        val key = NodeKey(system, node.type, node.code.uppercase(), node.parent?.uppercase())

        fun accepts(observed: NodeKey): Boolean = key.accepts(observed)

        fun accepts(data: ServiceData): Boolean =
            system == data.system &&
                node.type == NodeDefinitionType.SERVICE &&
                node.code.uppercase() == data.code &&
                (node.parent == null || node.parent.uppercase() == data.parent)
    }

    private data class NodeKey(
        val system: String,
        val type: NodeDefinitionType,
        val code: String,
        val parent: String?,
    ) {
        fun accepts(observed: NodeKey): Boolean =
            system == observed.system &&
                type == observed.type &&
                code == observed.code &&
                (parent == null || parent == observed.parent)
    }

    private data class ServiceData(
        val system: String,
        val code: String,
        val parent: String?,
        val blocks: Map<Int, ByteArray>,
    )

    private data class CandidateScore(
        val card: String,
        val filesystemRank: Int,
        val missingSystems: Int,
        val missingNodes: Int,
        val dataMatches: Int,
        val dataMismatches: Int,
        val hits: Int,
        val extraNodes: Int,
    ) {
        val isPresent: Boolean
            get() = filesystemRank < FS_NONE || hits > 0 || dataMatches > 0
    }

    private val scoreRankOrder =
        compareBy<CandidateScore> { it.filesystemRank }
            .thenBy { it.missingSystems }
            .thenBy { it.missingNodes }
            .thenByDescending { it.dataMatches }
            .thenBy { it.dataMismatches }
            .thenByDescending { it.hits }
            .thenBy { it.extraNodes }

    private val areaOrder =
        compareBy<Area> { it.endNumber - it.number }
            .thenByDescending { it.number }
            .thenBy { it.attribute.canCreateSubArea }
            .thenBy { it.fullCode.hex() }

    private inline fun <T, R> Iterable<T>.mapToSet(transform: (T) -> R): Set<R> =
        mapTo(linkedSetOf(), transform)
}
