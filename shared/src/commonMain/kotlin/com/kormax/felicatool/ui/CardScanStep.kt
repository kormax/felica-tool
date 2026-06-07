package com.kormax.felicatool.ui

import com.kormax.felicatool.service.ScanSettings
import com.kormax.felicatool.service.ScanStepRegistry
import kotlin.time.Duration

enum class StepStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    ERROR,
}

enum class ScanStepIcon {
    BUILD,
    CHECK,
    EDIT,
    INFO,
    LIST,
    LOCK,
    PHONE,
    REFRESH,
    SEARCH,
    SETTINGS,
}

data class CardScanStep(
    val id: String,
    val title: String,
    val description: String,
    val icon: ScanStepIcon,
    val status: StepStatus = StepStatus.PENDING,
    val result: String? = null,
    val collapsedResult: String? = null,
    val isCollapsed: Boolean = true,
    val errorMessage: String? = null,
    val duration: Duration? = null,
) {
    val durationMilliseconds: Long?
        get() = duration?.inWholeMilliseconds

    companion object {
        fun createInitialSteps(scanSettings: ScanSettings = ScanSettings()): List<CardScanStep> =
            ScanStepRegistry.descriptors(scanSettings).map { descriptor ->
                CardScanStep(
                    id = descriptor.id,
                    title = descriptor.title,
                    description = descriptor.description,
                    icon = descriptor.icon,
                )
            }
    }
}
