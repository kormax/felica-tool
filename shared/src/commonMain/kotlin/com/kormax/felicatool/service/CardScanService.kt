package com.kormax.felicatool.service

import com.kormax.felicatool.authentication.AuthenticationProviderRegistry
import com.kormax.felicatool.felica.FeliCaTarget
import com.kormax.felicatool.logging.ScanLog
import com.kormax.felicatool.nfc.NfcTargetUnavailableException
import com.kormax.felicatool.nfc.TransceiveTimeoutException
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.StepStatus
import com.kormax.felicatool.util.EmptyNodeMetadataProvider
import com.kormax.felicatool.util.NodeMetadataProvider
import kotlin.time.Duration
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class CardScanResult(
    val steps: List<CardScanStep>,
    val completed: Boolean,
    val terminalErrorMessage: String?,
    val duration: Duration,
)

class CardScanService(
    private val nodeMetadataProvider: NodeMetadataProvider = EmptyNodeMetadataProvider,
    private val authenticationProviderRegistry: AuthenticationProviderRegistry? = null,
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
                authenticationProviderRegistry = authenticationProviderRegistry,
            )

        try {
            for (index in workingSteps.indices) {
                currentCoroutineContext().ensureActive()
                val currentStep = workingSteps[index]

                fun withCurrentStep(step: CardScanStep) =
                    workingSteps.toMutableList().apply { set(index, step) }

                val updatedStep =
                    runStep(currentStep, session) { step -> onStepsChanged(withCurrentStep(step)) }

                workingSteps = withCurrentStep(updatedStep)
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
        } finally {
            session.closeAuthenticationProviderSessions()
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

        return try {
            val result = executeStep(scanStep, step, session)
            currentCoroutineContext().ensureActive()
            result.copy(duration = startTime.elapsedNow())
        } catch (e: CancellationException) {
            onStepUpdate(
                step.copy(
                    status = StepStatus.ERROR,
                    errorMessage = "Cancelled",
                    duration = startTime.elapsedNow(),
                ),
            )
            throw e
        }
    }

    private suspend fun executeStep(
        scanStep: ScanStep,
        step: CardScanStep,
        session: ScanSession,
    ): CardScanStep {
        try {
            return step.completedWith(scanStep.run(session))
        } catch (e: CancellationException) {
            throw e
        } catch (e: StepBehaviorUnexpected) {
            ScanLog.w("CardScanService", "Probe fallback used for step ${step.id}: ${e.message}")

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Probe fallback applied",
            )
        } catch (e: StepPreconditionNotMet) {
            ScanLog.w("CardScanService", "Prerequisite not met for step ${step.id}: ${e.message}")

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Prerequisite not met",
            )
        } catch (e: StepSkipped) {
            ScanLog.i("CardScanService", "Skipping step ${step.id}: ${e.message}")

            return step.copy(
                status = StepStatus.SKIPPED,
                result = e.message ?: "Step skipped",
            )
        } catch (e: NfcTargetUnavailableException) {
            ScanLog.e("CardScanService", "Card unavailable for step ${step.id}", e)

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = CARD_LOST_MESSAGE,
            )
        } catch (e: TransceiveTimeoutException) {
            ScanLog.w("CardScanService", "No response for step ${step.id}: ${e.message}", e)

            session.context =
                scanStep.withCommandSupport(session.context, CommandSupport.UNSUPPORTED)

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "No response from target",
            )
        } catch (e: Exception) {
            ScanLog.e("CardScanService", "Error executing step ${step.id}", e)

            session.context =
                scanStep.withCommandSupport(session.context, CommandSupport.UNSUPPORTED)

            return step.copy(
                status = StepStatus.ERROR,
                errorMessage = e.message ?: "Unknown error",
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
