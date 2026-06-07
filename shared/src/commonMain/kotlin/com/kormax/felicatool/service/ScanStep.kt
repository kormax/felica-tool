package com.kormax.felicatool.service

import com.kormax.felicatool.ui.ScanStepIcon

internal const val ATTEMPTS_DETERMINE_SUPPORTED = 5

internal data class ScanStepDescriptor(
    val id: String,
    val title: String,
    val description: String,
    val icon: ScanStepIcon,
)

internal data class StepOutput(
    val result: String,
    val collapsedResult: String? = null,
)

internal abstract class ScanStep(
    id: String,
    title: String,
    description: String,
    icon: ScanStepIcon,
) {
    val descriptor: ScanStepDescriptor =
        ScanStepDescriptor(
            id = id,
            title = title,
            description = description,
            icon = icon,
        )

    open fun isEnabled(settings: ScanSettings): Boolean = true

    open fun commandSupport(context: CardScanContext): CommandSupport = CommandSupport.UNKNOWN

    open fun withCommandSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context

    open suspend fun run(session: ScanSession): StepOutput = session.perform()

    protected abstract suspend fun ScanSession.perform(): StepOutput
}
