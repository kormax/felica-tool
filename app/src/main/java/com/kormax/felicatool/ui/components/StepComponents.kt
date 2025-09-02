package com.kormax.felicatool.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.ui.CardScanStep
import com.kormax.felicatool.ui.StepStatus

@Composable
fun StepCard(
    step: CardScanStep,
    modifier: Modifier = Modifier,
    onToggleCollapse: ((String) -> Unit)? = null,
) {
    // Skip rendering the scan_overview step as it's handled by the prominent button
    if (step.id == "scan_overview") {
        return
    }

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
                    StepStatus.PENDING -> step.icon
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

            // Add expand/collapse button for steps that support it (excluding comprehensive data
            // view)
            if (
                step.status == StepStatus.COMPLETED &&
                    step.collapsedResult != null &&
                    step.id in
                        listOf(
                            "search_services",
                            "request_service_key_versions",
                            "request_service_v2_key_versions",
                        ) &&
                    onToggleCollapse != null
            ) {
                IconButton(
                    onClick = { onToggleCollapse(step.id) },
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

@Composable
fun StepsList(
    steps: List<CardScanStep>,
    modifier: Modifier = Modifier,
    onToggleCollapse: ((String) -> Unit)? = null,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
    ) {
        items(steps) { step -> StepCard(step = step, onToggleCollapse = onToggleCollapse) }
    }
}
