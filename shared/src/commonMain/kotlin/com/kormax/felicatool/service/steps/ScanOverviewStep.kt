package com.kormax.felicatool.service.steps

import com.kormax.felicatool.service.ScanSession
import com.kormax.felicatool.service.ScanStep
import com.kormax.felicatool.service.StepOutput
import com.kormax.felicatool.ui.ScanStepIcon

internal object ScanOverviewStep :
    ScanStep(
        id = "scan_overview",
        title = "View Comprehensive Data",
        description = "Display all collected card information in a detailed view",
        icon = ScanStepIcon.LIST,
    ) {
    override suspend fun ScanSession.perform(): StepOutput {
        return StepOutput("Click to view comprehensive overview of all discovered card data")
    }
}
