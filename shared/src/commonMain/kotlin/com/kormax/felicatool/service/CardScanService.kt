package com.kormax.felicatool.service

import com.kormax.felicatool.felica.FeliCaTarget
import com.kormax.felicatool.nfc.TagUnavailableException
import com.kormax.felicatool.service.logging.CommunicationLoggedFeliCaTarget
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.util.EmptyNodeMetadataProvider
import com.kormax.felicatool.util.NodeMetadataProvider
import kotlin.time.TimeSource

data class CardScanResult(
    val steps: List<CardScanStep>,
    val completed: Boolean,
    val terminalErrorMessage: String?,
)

class CardScanService(
    private val nodeMetadataProvider: NodeMetadataProvider = EmptyNodeMetadataProvider
) {
    companion object {
        const val CARD_LOST_MESSAGE = "Card lost during scan - scan terminated"
    }

    private var scanContext = CardScanContext()

    fun getScanContext(): CardScanContext = scanContext

    suspend fun scan(
        target: FeliCaTarget,
        scanSettings: ScanSettings = ScanSettings(),
        onStepsChanged: (List<CardScanStep>) -> Unit,
    ): CardScanResult {
        scanContext = CardScanContext()
        var workingSteps = CardScanStep.createInitialSteps(scanSettings)
        var terminalErrorMessage: String? = null

        onStepsChanged(workingSteps)

        val loggedTarget = CommunicationLoggedFeliCaTarget(target)
        val session =
            ScanSession(
                target = loggedTarget,
                settings = scanSettings,
                nodeMetadataProvider = nodeMetadataProvider,
                readContext = { scanContext },
                writeContext = { updatedContext -> scanContext = updatedContext },
            )

        for (index in workingSteps.indices) {
            val currentStep = workingSteps[index]
            val updatedStep =
                runStep(
                    step = currentStep,
                    session = session,
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
                    updatedStep.errorMessage == CARD_LOST_MESSAGE
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

    private suspend fun runStep(
        step: CardScanStep,
        session: ScanSession,
        onStepUpdate: (CardScanStep) -> Unit,
    ): CardScanStep {
        nodeMetadataProvider.ensureReady()
        val scanStep = ScanStepRegistry.find(step.id)
        if (scanStep == null || !scanStep.isEnabled(session.settings)) {
            return step.copy(
                status = StepStatus.COMPLETED,
                result = "Step is not enabled for this scan",
                duration = kotlin.time.Duration.ZERO,
            )
        }

        if (scanStep.commandSupport(scanContext) == CommandSupport.UNSUPPORTED) {
            return step.copy(
                status = StepStatus.COMPLETED,
                result = "Command not supported by this card",
                duration = kotlin.time.Duration.ZERO,
            )
        }

        val inProgressStep = step.copy(status = StepStatus.IN_PROGRESS)
        onStepUpdate(inProgressStep)

        session.beginStep(scanStep)
        val startTime = TimeSource.Monotonic.markNow()

        try {
            val result = scanStep.run(session)

            return step.completedWith(result).copy(duration = startTime.elapsedNow())
        } catch (e: StepBehaviorUnexpected) {
            ScanLog.w("CardScanService", "Probe fallback used for step ${step.id}: ${e.message}")

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Probe fallback applied",
                duration = startTime.elapsedNow(),
            )
        } catch (e: StepPreconditionNotMet) {
            ScanLog.w("CardScanService", "Prerequisite not met for step ${step.id}: ${e.message}")

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Prerequisite not met",
                duration = startTime.elapsedNow(),
            )
        } catch (e: TagUnavailableException) {
            ScanLog.e("CardScanService", "Card unavailable for step ${step.id}", e)

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = CARD_LOST_MESSAGE,
                duration = startTime.elapsedNow(),
            )
        } catch (e: Exception) {
            ScanLog.e("CardScanService", "Error executing step ${step.id}", e)

            scanContext = scanStep.withCommandSupport(scanContext, CommandSupport.UNSUPPORTED)

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Unknown error",
                duration = startTime.elapsedNow(),
            )
        }
    }

    private fun CardScanStep.completedWith(result: StepOutput): CardScanStep =
        when (result.collapsedResult) {
            null -> copy(status = StepStatus.COMPLETED, result = result.result)
            else ->
                copy(
                    status = StepStatus.COMPLETED,
                    result = result.result,
                    collapsedResult = result.collapsedResult,
                    isCollapsed = true,
                )
        }
}
