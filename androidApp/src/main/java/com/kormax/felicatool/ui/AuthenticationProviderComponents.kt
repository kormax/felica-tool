package com.kormax.felicatool.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kormax.felicatool.AppBarMenu
import com.kormax.felicatool.AppBarMenuGroup
import com.kormax.felicatool.authentication.AuthenticationProviderSettings
import com.kormax.felicatool.authentication.AuthenticationProviderSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTopBar(
    title: String,
    onBackPressed: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBackPressed) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        },
        actions = { actions() },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    )
}

internal class MutationState(private val scope: CoroutineScope) {
    var busy by mutableStateOf<String?>(null)
        private set

    var error by mutableStateOf<String?>(null)

    fun launch(
        busyLabel: String? = null,
        refresh: suspend () -> Unit,
        block: suspend () -> Unit,
    ) {
        scope.launch {
            busy = busyLabel
            try {
                runCatchingCancellable {
                    block()
                    refresh()
                }
                    .onSuccess { error = null }
                    .onFailure { error = it.message ?: "Provider change failed" }
            } finally {
                busy = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BusyDialog(label: String) {
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LoadingIndicator()
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
internal fun NoticeBlock(
    title: String,
    text: String? = null,
    action: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                text?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            TextButton(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
internal fun Modifier.scrollIndicator(state: LazyListState): Modifier {
    val alpha by
        animateFloatAsState(
            if (state.isScrollInProgress) 1f else 0f,
            animationSpec = tween(if (state.isScrollInProgress) 150 else 600),
        )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val total = info.totalItemsCount
        val visible = info.visibleItemsInfo
        if (alpha == 0f || total == 0 || visible.isEmpty() || visible.size >= total)
            return@drawWithContent
        val first = visible.first()
        val firstFraction = -first.offset.toFloat() / first.size.coerceAtLeast(1)
        val thumbHeight = (size.height * visible.size / total).coerceAtLeast(32.dp.toPx())
        val progress = (first.index + firstFraction) / (total - visible.size).coerceAtLeast(1)
        val top = (size.height - thumbHeight) * progress.coerceIn(0f, 1f)
        drawRoundRect(
            color = color.copy(alpha = 0.5f * alpha),
            topLeft = Offset(size.width - 6.dp.toPx(), top),
            size = Size(3.dp.toPx(), thumbHeight),
            cornerRadius = CornerRadius(1.5.dp.toPx()),
        )
    }
}

internal fun Int.counted(noun: String) = "$this $noun${if (this == 1) "" else "s"}"

internal data class OptionAppearance(
    val icon: Int,
    val color: Color,
    val label: String,
    val description: String,
)

@Composable
internal fun SelectableOptionCard(
    icon: Int,
    color: Color,
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.large,
        color = if (selected) scheme.secondaryContainer else scheme.surfaceContainerHighest,
        border = if (selected) BorderStroke(2.dp, scheme.secondary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).alpha(if (enabled) 1f else 0.38f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.2f)) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = color,
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
                content()
            }
            Box(Modifier.size(24.dp)) {
                if (selected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = scheme.secondary,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProviderEditorScaffold(
    settings: AuthenticationProviderSettings,
    summary: AuthenticationProviderSummary,
    mutations: MutationState,
    refresh: suspend () -> Unit,
    onBackPressed: () -> Unit,
    onDeleted: () -> Unit,
    deleteWarning: String,
    menuItems: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
    bottomBar: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameName by remember { mutableStateOf("") }
    var showDeleteProviderConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets =
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            SettingsTopBar(
                title = summary.displayName,
                onBackPressed = onBackPressed,
                actions = {
                    AppBarMenu(icon = Icons.Filled.MoreVert, label = "Provider actions") { dismiss
                        ->
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                            menuItems(dismiss)
                        }
                        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Filled.Edit, null) },
                                shape = MenuDefaults.leadingItemShape,
                                onClick = {
                                    dismiss()
                                    renameName = summary.displayName
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                shape = MenuDefaults.trailingItemShape,
                                onClick = {
                                    dismiss()
                                    showDeleteProviderConfirmation = true
                                },
                            )
                        }
                    }
                },
            )
        },
        bottomBar = bottomBar,
        content = content,
    )

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename provider") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    label = { Text("Provider name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        mutations.launch(refresh = refresh) {
                            settings.renameProvider(summary.id, renameName)
                        }
                    },
                    enabled = renameName.isNotBlank() && renameName.trim() != summary.displayName,
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
        )
    }

    mutations.busy?.let { BusyDialog(it) }

    if (showDeleteProviderConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteProviderConfirmation = false },
            title = { Text("Delete provider?") },
            text = { Text(deleteWarning) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteProviderConfirmation = false
                        mutations.launch("Deleting provider", refresh = {}) {
                            settings.deleteProvider(summary.id)
                            onDeleted()
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteProviderConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

internal inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
