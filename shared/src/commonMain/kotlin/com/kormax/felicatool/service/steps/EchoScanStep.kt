package com.kormax.felicatool.service.steps

import com.kormax.felicatool.service.CardScanContext
import com.kormax.felicatool.service.CommandSupport
import com.kormax.felicatool.ui.ScanStepIcon

internal abstract class EchoScanStep(
    id: String,
    title: String,
    description: String,
    icon: ScanStepIcon,
) :
    CommandSupportScanStep(
        id = id,
        title = title,
        description = description,
        icon = icon,
    ) {
    final override fun readSupport(context: CardScanContext): CommandSupport = context.echoSupport

    final override fun writeSupport(
        context: CardScanContext,
        support: CommandSupport,
    ): CardScanContext = context.copy(echoSupport = support)
}
