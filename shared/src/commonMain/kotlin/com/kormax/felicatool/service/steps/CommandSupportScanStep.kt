package com.kormax.felicatool.service.steps

import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.ScanStep
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.ui.ScanStepIcon

internal abstract class CommandSupportScanStep(
    id: String,
    title: String,
    description: String,
    icon: ScanStepIcon,
) :
    ScanStep(
        id = id,
        title = title,
        description = description,
        icon = icon,
    ) {
    protected abstract fun readSupport(context: CardScanContext): CommandSupport

    protected abstract fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext

    final override fun commandSupport(context: CardScanContext): CommandSupport =
        readSupport(context)

    final override suspend fun run(session: ScanSession): StepOutput {
        val output = session.perform()
        if (readSupport(session.context) != CommandSupport.UNSUPPORTED) {
            session.context = withCommandSupport(session.context, CommandSupport.SUPPORTED)
        }
        return output
    }

    final override fun withCommandSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext {
        if (
            support == CommandSupport.UNSUPPORTED &&
                readSupport(context) == CommandSupport.SUPPORTED
        ) {
            return context
        }

        return writeSupport(context, support)
    }
}
