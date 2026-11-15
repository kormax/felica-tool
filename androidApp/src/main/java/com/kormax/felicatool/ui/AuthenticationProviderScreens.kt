package com.kormax.felicatool.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.AppBarMenu
import com.kormax.felicatool.AppBarMenuGroup
import com.kormax.felicatool.R
import com.kormax.felicatool.authentication.AuthenticationProviderSettings
import com.kormax.felicatool.authentication.AuthenticationProviderSummary
import com.kormax.felicatool.authentication.AuthenticationProviderType
import com.kormax.felicatool.authentication.LocalAuthenticationCredentials
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AuthenticationProvidersScreen(
    settings: AuthenticationProviderSettings,
    localCredentials: LocalAuthenticationCredentials,
    onBackPressed: () -> Unit,
    onOpenProvider: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(emptyList<AuthenticationProviderSummary>()) }
    var details by remember { mutableStateOf(emptyMap<String, String>()) }
    var pendingProviderOrder by remember {
        mutableStateOf<List<AuthenticationProviderSummary>?>(null)
    }
    val mutations = remember { MutationState(scope) }
    var showAddDialog by remember { mutableStateOf(false) }
    var isSavingOrder by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var unreadableCount by remember { mutableIntStateOf(0) }
    var showDeleteAllConfirmation by remember { mutableStateOf(false) }
    var showDeleteUnreadableConfirmation by remember { mutableStateOf(false) }

    suspend fun refresh() {
        providers = settings.summaries()
        unreadableCount = settings.unreadableProviderCount()
        loadError = null
        details = providers.associate { it.id to it.details(localCredentials) }
    }

    fun cancelReordering() {
        if (isSavingOrder) return
        pendingProviderOrder = null
        mutations.error = null
    }

    fun moveProvider(providerId: String, offset: Int) {
        val order = pendingProviderOrder ?: return
        val index = order.indexOfFirst { it.id == providerId }
        if (index < 0) return
        val target = (index + offset).coerceIn(order.indices)
        if (target == index) return
        pendingProviderOrder = order.toMutableList().apply { add(target, removeAt(index)) }
    }

    fun saveProviderOrder() {
        val providerIds = pendingProviderOrder?.map { it.id } ?: return
        scope.launch {
            isSavingOrder = true
            try {
                runCatchingCancellable {
                    settings.reorderProviders(providerIds)
                    refresh()
                }
                    .onSuccess {
                        pendingProviderOrder = null
                        mutations.error = null
                    }
                    .onFailure {
                        mutations.error = it.message ?: "Could not save provider priorities"
                    }
            } finally {
                isSavingOrder = false
            }
        }
    }

    BackHandler {
        if (pendingProviderOrder != null) cancelReordering() else onBackPressed()
    }

    LaunchedEffect(settings) {
        runCatchingCancellable { refresh() }.onFailure { loadError = it.message ?: "Unknown error" }
        isLoading = false
    }

    val displayedProviders = pendingProviderOrder ?: providers
    val isReordering = pendingProviderOrder != null

    Scaffold(
        topBar = {
            SettingsTopBar(
                title = if (isReordering) "Rearrange priorities" else "Authentication Providers",
                onBackPressed = { if (isReordering) cancelReordering() else onBackPressed() },
                actions = {
                    if (isReordering) {
                        IconButton(onClick = ::saveProviderOrder, enabled = !isSavingOrder) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = "Save provider priorities",
                            )
                        }
                    } else {
                        AppBarMenu(icon = Icons.Filled.MoreVert, label = "Provider actions") {
                            dismiss ->
                            AppBarMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                                DropdownMenuItem(
                                    text = { Text("Rearrange priorities") },
                                    leadingIcon = {
                                        Icon(painterResource(R.drawable.ic_swap_vert), null)
                                    },
                                    shape = MenuDefaults.standaloneItemShape,
                                    enabled = providers.size > 1,
                                    onClick = {
                                        dismiss()
                                        pendingProviderOrder = providers.toList()
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!isReordering) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Add provider")
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.padding(top = 2.dp))
            loadError?.let { error ->
                NoticeBlock(
                    title = "Saved providers can't be read",
                    text = error,
                    action = "Delete all",
                    onAction = { showDeleteAllConfirmation = true },
                )
            }
            if (unreadableCount > 0) {
                NoticeBlock(
                    title =
                        if (unreadableCount == 1) "1 provider can't be read"
                        else "$unreadableCount providers can't be read",
                    action = "Delete",
                    onAction = { showDeleteUnreadableConfirmation = true },
                )
            }
            mutations.error?.let {
                NoticeBlock(
                    title = "Couldn't change providers",
                    text = it,
                    action = "Dismiss",
                    onAction = { mutations.error = null },
                )
            }
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            } else if (loadError == null && displayedProviders.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            "No authentication providers",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
            displayedProviders.forEachIndexed { index, provider ->
                ProviderCard(
                    provider = provider,
                    details = details[provider.id],
                    isReordering = isReordering,
                    canMoveUp = index > 0,
                    canMoveDown = index < displayedProviders.lastIndex,
                    onOpen = { onOpenProvider(provider.id) },
                    onEnabledChange = { enabled ->
                        mutations.launch(refresh = ::refresh) {
                            settings.setProviderEnabled(provider.id, enabled)
                        }
                    },
                    onMove = { offset -> moveProvider(provider.id, offset) },
                )
            }
            Spacer(Modifier.padding(bottom = 44.dp))
        }
    }

    mutations.busy?.let { BusyDialog(it) }

    if (showDeleteAllConfirmation) {
        DeleteConfirmationDialog(
            title = "Delete all providers?",
            text = "All saved providers and their keys will be deleted.",
            onDismiss = { showDeleteAllConfirmation = false },
            onConfirm = {
                showDeleteAllConfirmation = false
                mutations.launch("Deleting providers", ::refresh) {
                    settings.deleteAllProviders()
                }
            },
        )
    }

    if (showDeleteUnreadableConfirmation) {
        DeleteConfirmationDialog(
            title =
                if (unreadableCount == 1) "Delete unreadable provider?"
                else "Delete $unreadableCount unreadable providers?",
            text = "Their keys will be deleted too.",
            onDismiss = { showDeleteUnreadableConfirmation = false },
            onConfirm = {
                showDeleteUnreadableConfirmation = false
                mutations.launch("Deleting providers", ::refresh) {
                    settings.deleteUnreadableProviders()
                }
            },
        )
    }

    if (showAddDialog) {
        AddProviderDialog(
            initialName = if (providers.isEmpty()) "Authentication" else "",
            onDismiss = { showAddDialog = false },
            onAdd = { type, name ->
                showAddDialog = false
                mutations.launch(refresh = ::refresh) {
                    val id = settings.createProvider(type, name)
                    onOpenProvider(id)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AuthenticationProviderEditorScreen(
    settings: AuthenticationProviderSettings,
    localCredentials: LocalAuthenticationCredentials,
    providerId: String,
    onBackPressed: () -> Unit,
    onDeleted: () -> Unit,
) {
    BackHandler(onBack = onBackPressed)
    var summary by remember { mutableStateOf<AuthenticationProviderSummary?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(settings, providerId) {
        runCatchingCancellable { summary = settings.summary(providerId) }
            .onFailure { loadError = it.message ?: "Could not load authentication provider" }
        isLoaded = true
    }

    val current = summary
    if (current == null) {
        Scaffold(topBar = { SettingsTopBar(title = "Provider", onBackPressed = onBackPressed) }) {
            padding ->
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                val error =
                    loadError ?: "Authentication provider does not exist".takeIf { isLoaded }
                if (error != null) {
                    Text(error, modifier = Modifier.fillMaxWidth())
                } else {
                    LoadingIndicator(modifier = Modifier.padding(vertical = 32.dp))
                }
            }
        }
        return
    }
    when (current.type) {
        AuthenticationProviderType.DES_AUTHENTICATION ->
            DesAuthenticationKeyProviderEditor(
                settings = settings,
                localCredentials = localCredentials,
                summary = current,
                onSummaryChange = { summary = it },
                onBackPressed = onBackPressed,
                onDeleted = onDeleted,
            )
    }
}

@Composable
private fun ProviderCard(
    provider: AuthenticationProviderSummary,
    details: String?,
    isReordering: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
) {
    Card(
        modifier =
            Modifier.fillMaxWidth()
                .clip(CardDefaults.shape)
                .clickable(enabled = !isReordering, onClick = onOpen),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (provider.isEnabled) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    provider.type.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                details?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isReordering) {
                Row {
                    IconButton(onClick = { onMove(-1) }, enabled = canMoveUp) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { onMove(1) }, enabled = canMoveDown) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                    }
                }
            } else {
                Switch(
                    checked = provider.isEnabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }
    }
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    initialName: String,
    onAdd: (AuthenticationProviderType, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var type by remember { mutableStateOf(AuthenticationProviderType.entries.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add authentication provider") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Provider name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AuthenticationProviderType.entries.forEach { candidate ->
                    val appearance = candidate.appearance
                    SelectableOptionCard(
                        icon = appearance.icon,
                        color = appearance.color,
                        title = appearance.label,
                        description = appearance.description,
                        selected = type == candidate,
                        enabled = true,
                        onClick = { type = candidate },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(type, name) }, enabled = name.isNotBlank()) {
                Text("Add")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val AuthenticationProviderType.appearance
    @Composable
    get() =
        when (this) {
            AuthenticationProviderType.DES_AUTHENTICATION -> desAuthenticationKeyProviderAppearance
        }

private suspend fun AuthenticationProviderSummary.details(
    localCredentials: LocalAuthenticationCredentials,
): String =
    when (type) {
        AuthenticationProviderType.DES_AUTHENTICATION ->
            desAuthenticationKeyProviderDetails(localCredentials, id)
    }

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text("Delete")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
