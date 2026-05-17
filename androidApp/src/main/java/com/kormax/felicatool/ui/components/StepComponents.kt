package com.kormax.felicatool.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.ScanStepIcon
import com.kormax.felicatool.ui.StepStatus
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull

private const val ActiveStepPreferredViewportFraction = 0.66f

private data class StepListScrollTarget(val key: String, val index: Int, val scrollOffsetPx: Int)

@Composable
fun StepCard(
    step: CardScanStep,
    modifier: Modifier = Modifier,
    onToggleCollapse: ((String) -> Unit)? = null,
    isCollapseEnabled: Boolean = true,
) {
    val cardColor by
        animateColorAsState(
            targetValue =
                when (step.status) {
                    StepStatus.PENDING -> MaterialTheme.colorScheme.surface
                    StepStatus.IN_PROGRESS -> MaterialTheme.colorScheme.primaryContainer
                    StepStatus.COMPLETED -> MaterialTheme.colorScheme.tertiaryContainer
                    StepStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
                },
            label = "cardColor",
        )

    val contentColor =
        when (step.status) {
            StepStatus.PENDING -> MaterialTheme.colorScheme.onSurface
            StepStatus.IN_PROGRESS -> MaterialTheme.colorScheme.onPrimaryContainer
            StepStatus.COMPLETED -> MaterialTheme.colorScheme.onTertiaryContainer
            StepStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor, contentColor = contentColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Status icon
            val statusIcon =
                when (step.status) {
                    StepStatus.PENDING -> step.icon.imageVector()
                    StepStatus.IN_PROGRESS -> Icons.Default.Refresh
                    StepStatus.COMPLETED -> Icons.Default.CheckCircle
                    StepStatus.ERROR -> Icons.Default.Close
                }

            Icon(
                imageVector = statusIcon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )

            // Content
            Column(modifier = Modifier.weight(1f)) {
                // Title row with duration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    // Display duration if available
                    step.duration?.let { duration ->
                        val durationText =
                            when {
                                duration.inWholeSeconds > 0 ->
                                    "%.1fs".format(duration.inWholeMilliseconds / 1000.0)
                                duration.inWholeMilliseconds > 0 ->
                                    "${duration.inWholeMilliseconds}ms"
                                else -> "<1ms"
                            }

                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }

                Text(
                    text = step.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp),
                )

                // Show result or error
                when (step.status) {
                    StepStatus.COMPLETED -> {
                        // Show collapsed or expanded result
                        val resultToShow =
                            if (step.isCollapsed && step.collapsedResult != null) {
                                step.collapsedResult
                            } else {
                                step.result
                            }

                        resultToShow?.let { result ->
                            Text(
                                text = result,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 8.dp),
                                color = contentColor.copy(alpha = 0.9f),
                            )
                        }
                    }
                    StepStatus.ERROR -> {
                        step.errorMessage?.let { error ->
                            Text(
                                text = "Error: $error",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    else -> {
                        /* No additional content */
                    }
                }
            }

            // Add expand/collapse button for steps that provide expanded details.
            if (
                step.status == StepStatus.COMPLETED &&
                    step.collapsedResult != null &&
                    onToggleCollapse != null
            ) {
                IconButton(
                    onClick = { onToggleCollapse(step.id) },
                    enabled = isCollapseEnabled,
                    modifier = Modifier.size(24.dp),
                ) {
                    Icon(
                        imageVector =
                            if (step.isCollapsed) Icons.Default.KeyboardArrowDown
                            else Icons.Default.KeyboardArrowUp,
                        contentDescription = if (step.isCollapsed) "Expand" else "Collapse",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

private fun ScanStepIcon.imageVector() =
    when (this) {
        ScanStepIcon.BUILD -> Icons.Default.Build
        ScanStepIcon.CHECK -> Icons.Default.CheckCircle
        ScanStepIcon.EDIT -> Icons.Default.Edit
        ScanStepIcon.INFO -> Icons.Default.Info
        ScanStepIcon.LIST -> Icons.AutoMirrored.Filled.List
        ScanStepIcon.LOCK -> Icons.Default.Lock
        ScanStepIcon.PHONE -> Icons.Default.Phone
        ScanStepIcon.REFRESH -> Icons.Default.Refresh
        ScanStepIcon.SEARCH -> Icons.Default.Search
        ScanStepIcon.SETTINGS -> Icons.Default.Settings
    }

@Composable
fun StepsList(
    steps: List<CardScanStep>,
    modifier: Modifier = Modifier,
    onToggleCollapse: ((String) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    isScrollLocked: Boolean = false,
    isScanCompleted: Boolean = false,
) {
    val visibleSteps = steps.filterNot { step -> step.id == "scan_overview" }
    val listState = rememberLazyListState()
    val activeStepIndex = visibleSteps.indexOfFirst { step ->
        step.status == StepStatus.IN_PROGRESS
    }
    val activeStepId = visibleSteps.getOrNull(activeStepIndex)?.id

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val preferredActiveStepOffsetPx =
            with(density) { (maxHeight.toPx() * ActiveStepPreferredViewportFraction).roundToInt() }
        val scrollTarget =
            when {
                activeStepId != null ->
                    StepListScrollTarget(
                        key = "active:$activeStepId",
                        index = activeStepIndex,
                        scrollOffsetPx = -preferredActiveStepOffsetPx,
                    )
                isScanCompleted && visibleSteps.isNotEmpty() ->
                    StepListScrollTarget(
                        key = "completed:${visibleSteps.last().id}",
                        index = visibleSteps.lastIndex,
                        scrollOffsetPx = preferredActiveStepOffsetPx,
                    )
                else -> null
            }
        val currentScrollTarget = rememberUpdatedState(scrollTarget)

        LaunchedEffect(listState) {
            snapshotFlow { currentScrollTarget.value }
                .filterNotNull()
                .distinctUntilChanged()
                .collectLatest { target ->
                    listState.animateScrollToItem(
                        index = target.index,
                        scrollOffset = target.scrollOffsetPx,
                    )
                }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = contentPadding,
            userScrollEnabled = !isScrollLocked,
        ) {
            items(visibleSteps, key = { step -> step.id }) { step ->
                StepCard(
                    step = step,
                    onToggleCollapse = onToggleCollapse,
                    isCollapseEnabled = !isScrollLocked,
                )
            }
        }
    }
}
