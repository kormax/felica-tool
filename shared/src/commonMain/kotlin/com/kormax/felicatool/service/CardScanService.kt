package com.kormax.felicatool.service

import com.kormax.felicatool.felica.FeliCaTarget
import com.kormax.felicatool.nfc.NfcTargetUnavailableException
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.util.EmptyNodeMetadataProvider
import com.kormax.felicatool.util.NodeMetadataProvider
import kotlin.time.Duration
import kotlin.time.TimeSource

data class CardScanResult(
    val steps: List<CardScanStep>,
    val completed: Boolean,
    val terminalErrorMessage: String?,
    val duration: Duration,
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
        val scanStartTime = TimeSource.Monotonic.markNow()

        onStepsChanged(workingSteps)

        val session =
            ScanSession(
                initialTarget = target,
                settings = scanSettings,
                nodeMetadataProvider = nodeMetadataProvider,
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
                scanContext = session.contextSnapshot()
                break
            }
            scanContext = session.contextSnapshot()
        }
        val scanDuration = scanStartTime.elapsedNow()
        session.context =
            session.context.copy(scanDurationMillis = scanDuration.inWholeMilliseconds)
        scanContext = session.contextSnapshot()

        return CardScanResult(
            steps = workingSteps,
            completed = terminalErrorMessage == null,
            terminalErrorMessage = terminalErrorMessage,
            duration = scanDuration,
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

        if (scanStep.commandSupport(session.context) == CommandSupport.UNSUPPORTED) {
            return step.copy(
                status = StepStatus.SKIPPED,
                result = "Command not supported by this card",
                duration = kotlin.time.Duration.ZERO,
            )
        }

        val inProgressStep = step.copy(status = StepStatus.IN_PROGRESS)
        onStepUpdate(inProgressStep)

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
        } catch (e: StepSkipped) {
            ScanLog.i("CardScanService", "Skipping step ${step.id}: ${e.message}")

            return step.copy(
                status = StepStatus.SKIPPED,
                result = e.message ?: "Step skipped",
                duration = startTime.elapsedNow(),
            )
        } catch (e: NfcTargetUnavailableException) {
            ScanLog.e("CardScanService", "Card unavailable for step ${step.id}", e)

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = CARD_LOST_MESSAGE,
                duration = startTime.elapsedNow(),
            )
        } catch (e: TransceiveTimeoutException) {
            ScanLog.w("CardScanService", "No response for step ${step.id}: ${e.message}", e)

            session.context =
                scanStep.withCommandSupport(session.context, CommandSupport.UNSUPPORTED)

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "No response from target",
                duration = startTime.elapsedNow(),
            )
        } catch (e: Exception) {
            ScanLog.e("CardScanService", "Error executing step ${step.id}", e)

            session.context =
                scanStep.withCommandSupport(session.context, CommandSupport.UNSUPPORTED)

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
