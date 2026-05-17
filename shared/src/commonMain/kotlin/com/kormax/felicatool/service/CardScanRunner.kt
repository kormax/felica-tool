package com.kormax.felicatool.service

import com.kormax.felicatool.felica.FeliCaTarget
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.util.EmptyNodeMetadataProvider
import com.kormax.felicatool.util.NodeMetadataProvider

data class CardScanResult(
    val steps: List<CardScanStep>,
    val completed: Boolean,
    val terminalErrorMessage: String?,
)

class CardScanRunner(nodeMetadataProvider: NodeMetadataProvider = EmptyNodeMetadataProvider) {
    private val cardScanService = CardScanService(nodeMetadataProvider)

    fun getScanContext(): CardScanContext = cardScanService.getScanContext()

    suspend fun isCardPresent(target: FeliCaTarget): Boolean = cardScanService.isCardPresent(target)

    suspend fun scan(
        target: FeliCaTarget,
        scanSettings: ScanSettings = ScanSettings(),
        onStepsChanged: (List<CardScanStep>) -> Unit,
    ): CardScanResult {
        var workingSteps = CardScanStep.createInitialSteps(scanSettings)
        var terminalErrorMessage: String? = null

        onStepsChanged(workingSteps)

        val loggedTarget = cardScanService.wrapTargetForCommunicationLogging(target)
        cardScanService.setScanSettings(scanSettings)

        for (index in workingSteps.indices) {
            val currentStep = workingSteps[index]
            val updatedStep =
                cardScanService.executeStep(
                    step = currentStep,
                    target = loggedTarget,
                    onStepUpdate = { step ->
                        workingSteps =
                            workingSteps.toMutableList().apply {
                                if (index < size) {
                                    set(index, step)
                                }
                            }
                        onStepsChanged(workingSteps)
                    },
                )

            workingSteps = workingSteps.toMutableList().apply { set(index, updatedStep) }
            onStepsChanged(workingSteps)

            if (
                updatedStep.status == StepStatus.ERROR &&
                    updatedStep.errorMessage == CardScanService.CARD_LOST_MESSAGE
            ) {
                terminalErrorMessage = updatedStep.errorMessage
                break
            }
        }

        return CardScanResult(
            steps = workingSteps,
            completed = terminalErrorMessage == null,
            terminalErrorMessage = terminalErrorMessage,
        )
    }
}
