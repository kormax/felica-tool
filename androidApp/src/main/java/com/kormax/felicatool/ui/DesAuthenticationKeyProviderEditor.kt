package com.kormax.felicatool.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kormax.felicatool.R
import com.kormax.felicatool.authentication.AuthenticationProviderSettings
import com.kormax.felicatool.authentication.AuthenticationProviderSummary
import com.kormax.felicatool.authentication.AuthenticationProviderType
import com.kormax.felicatool.authentication.CredentialImportResult
import com.kormax.felicatool.authentication.DesAuthenticationKeySummary
import com.kormax.felicatool.authentication.DesKeyComponent
import com.kormax.felicatool.authentication.KeyStorage
import com.kormax.felicatool.authentication.KeyStorageOption
import com.kormax.felicatool.authentication.LocalAuthenticationCredentials
import com.kormax.felicatool.util.NodeDefinitionType
import com.kormax.felicatool.util.NodeRegistry
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun DesAuthenticationKeyProviderEditor(
    settings: AuthenticationProviderSettings,
    localCredentials: LocalAuthenticationCredentials,
    summary: AuthenticationProviderSummary,
    onSummaryChange: (AuthenticationProviderSummary?) -> Unit,
    onBackPressed: () -> Unit,
    onDeleted: () -> Unit,
) {
    val providerId = summary.id
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var keys by remember { mutableStateOf(emptyList<DesAuthenticationKeySummary>()) }
    val mutations = remember { MutationState(scope) }
    var credentialToDelete by remember { mutableStateOf<DesAuthenticationKeySummary?>(null) }
    var credentialToShow by remember { mutableStateOf<DesAuthenticationKeySummary?>(null) }
    var showBulkDelete by remember { mutableStateOf(false) }
    var importState by remember { mutableStateOf<KeyImportState?>(null) }
    var storageChoice by remember { mutableStateOf<KeyStorageChoice?>(null) }
    DisposableEffect(Unit) { onDispose { storageChoice?.data?.fill(0) } }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    suspend fun refresh() {
        onSummaryChange(settings.summary(providerId))
        keys =
            localCredentials.credentials(providerId).filterIsInstance<DesAuthenticationKeySummary>()
    }

    fun importKeys(data: ByteArray, storage: KeyStorage) {
        importState = KeyImportState.Importing()
        scope
            .launch {
                runCatchingCancellable {
                    localCredentials.importCredentials(providerId, data, storage) { imported, total
                        ->
                        importState = KeyImportState.Importing(imported, total)
                    }
                }
                    .onSuccess { result ->
                        importState = KeyImportState.Imported(result, storage)
                        runCatchingCancellable { refresh() }
                            .onFailure {
                                mutations.error =
                                    it.message ?: "Could not load authentication provider"
                            }
                    }
                    .onFailure {
                        importState = KeyImportState.Failed(it.message ?: "Key import failed")
                    }
            }
            .invokeOnCompletion { data.fill(0) }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            importState = KeyImportState.Importing()
            scope.launch {
                var data: ByteArray? = null
                try {
                    runCatchingCancellable {
                        withContext(Dispatchers.IO) {
                            data =
                                context.contentResolver.openInputStream(uri)?.use {
                                    it.readBytes()
                                }
                                    ?: throw IllegalArgumentException(
                                        "Could not open selected document",
                                    )
                        }
                        val prepared =
                            localCredentials.prepareCredentialImport(providerId, checkNotNull(data))
                        try {
                            prepared.storage to
                                KeyStorageChoice(
                                    prepared.data,
                                    if (prepared.storage == null) {
                                        localCredentials.keyStorages(providerId)
                                    } else {
                                        emptyList()
                                    },
                                )
                        } catch (error: Throwable) {
                            prepared.data.fill(0)
                            throw error
                        }
                    }
                        .onSuccess { (required, choice) ->
                            if (required != null) {
                                importKeys(choice.data, required)
                            } else {
                                importState = null
                                storageChoice = choice
                            }
                        }
                        .onFailure {
                            importState = KeyImportState.Failed(it.message ?: "Key import failed")
                        }
                } finally {
                    data?.fill(0)
                }
            }
        }

    LaunchedEffect(localCredentials, providerId) {
        runCatchingCancellable { refresh() }
            .onFailure { mutations.error = it.message ?: "Could not load authentication provider" }
    }

    ProviderEditorScaffold(
        settings = settings,
        summary = summary,
        mutations = mutations,
        refresh = ::refresh,
        onBackPressed = onBackPressed,
        onDeleted = onDeleted,
        deleteWarning = "Its keys will be deleted too.",
        menuItems = { dismiss ->
            DropdownMenuItem(
                text = { Text("Import keys") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_file_open), null) },
                shape = MenuDefaults.leadingItemShape,
                onClick = {
                    dismiss()
                    importLauncher.launch(arrayOf("application/json", "text/json", "text/plain"))
                },
            )
            DropdownMenuItem(
                text = { Text("Delete keys") },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete_sweep), null) },
                enabled = keys.isNotEmpty(),
                shape = MenuDefaults.trailingItemShape,
                onClick = {
                    dismiss()
                    showBulkDelete = true
                },
            )
        },
        bottomBar = {
            if (keys.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier =
                            Modifier.fillMaxWidth()
                                .windowInsetsPadding(
                                    WindowInsets.navigationBars.union(WindowInsets.ime),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        placeholder = { Text("Search by IDm or system") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                    )
                }
            }
        },
    ) { padding ->
        val searchQuery = query.trim()
        val compactQuery = searchQuery.filterNot(Char::isWhitespace)
        val shownKeys =
            if (searchQuery.isEmpty()) keys
            else
                keys.filter {
                    it.systemCode.contains(compactQuery, ignoreCase = true) ||
                        it.idm.contains(compactQuery, ignoreCase = true) ||
                        it.systemName?.contains(searchQuery, ignoreCase = true) == true
                }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).scrollIndicator(listState),
            state = listState,
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom =
                        16.dp +
                            if (keys.isNotEmpty()) 0.dp
                            else
                                WindowInsets.navigationBars
                                    .asPaddingValues()
                                    .calculateBottomPadding(),
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        summary.type.displayName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        keys.size.counted("key"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            mutations.error?.let {
                item {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item { HorizontalDivider() }
            item { Text("Keys", style = MaterialTheme.typography.titleLarge) }
            if (keys.isNotEmpty()) {
                if (shownKeys.isEmpty()) {
                    item {
                        Text(
                            "No keys match \"$query\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (keys.isEmpty()) {
                item {
                    Text(
                        "No keys",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(shownKeys, key = { it.id }) { credential ->
                Card(
                    modifier =
                        Modifier.fillMaxWidth()
                            .clip(CardDefaults.shape)
                            .combinedClickable(
                                onClick = { credentialToShow = credential },
                                onLongClick = { credentialToShow = credential },
                            ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KeyStorageIcon(credential.storage)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${credential.systemCode} / ${credential.idm}",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            credential.systemName?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                "${credential.areaCodes.size.counted("area")}, " +
                                    credential.nodeCodes.size.counted("node"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { credentialToDelete = credential },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete key")
                        }
                    }
                }
            }
        }
    }

    credentialToShow?.let { credential ->
        AlertDialog(
            onDismissRequest = { credentialToShow = null },
            title = { Text(credential.systemName ?: "System ${credential.systemCode}") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        KeyDetailRow("System code", credential.systemCode)
                        KeyDetailRow("IDm", credential.idm)
                        KeyComponentsRow(credential.components)
                        credential.keyVersions["FFFF"]?.let { KeyDetailRow("System key", "v$it") }
                    }
                    NodeCodeSection(
                        "Areas",
                        credential.systemCode,
                        credential.areaCodes,
                        credential.keyVersions,
                        NodeDefinitionType.AREA,
                    )
                    NodeCodeSection(
                        "Nodes",
                        credential.systemCode,
                        credential.nodeCodes,
                        credential.keyVersions,
                        NodeDefinitionType.SERVICE,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { credentialToShow = null }) { Text("Close") }
            },
        )
    }
    credentialToDelete?.let { credential ->
        AlertDialog(
            onDismissRequest = { credentialToDelete = null },
            title = { Text("Delete key?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        credentialToDelete = null
                        mutations.launch(refresh = ::refresh) {
                            localCredentials.deleteCredentials(providerId, setOf(credential.id))
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { credentialToDelete = null }) { Text("Cancel") }
            },
        )
    }

    if (showBulkDelete) {
        BulkDeleteDialog(
            keys = keys,
            onDismiss = { showBulkDelete = false },
            onDelete = { ids ->
                showBulkDelete = false
                mutations.launch("Deleting ${ids.size.counted("key")}", ::refresh) {
                    localCredentials.deleteCredentials(providerId, ids)
                }
            },
        )
    }

    importState?.let { state ->
        KeyImportDialog(state, onDismiss = { importState = null })
    }

    storageChoice?.let { choice ->
        KeyStorageDialog(
            options = choice.options,
            onSelect = { storage ->
                storageChoice = null
                importKeys(choice.data, storage)
            },
            onDismiss = {
                storageChoice = null
                choice.data.fill(0)
            },
        )
    }
}

private enum class KeyGrouping(val label: String, val noun: String) {
    SYSTEM("System", "system"),
    IDM("IDm", "IDm"),
    STORAGE("Storage", "storage type"),
}

@Composable
private fun BulkDeleteDialog(
    keys: List<DesAuthenticationKeySummary>,
    onDismiss: () -> Unit,
    onDelete: (Set<String>) -> Unit,
) {
    var grouping by remember { mutableStateOf(KeyGrouping.SYSTEM) }
    var selected by remember(grouping) { mutableStateOf(emptySet<String>()) }
    val groups =
        remember(keys, grouping) {
            keys
                .groupBy {
                    when (grouping) {
                        KeyGrouping.SYSTEM -> it.systemCode
                        KeyGrouping.IDM -> it.idm
                        KeyGrouping.STORAGE -> it.storage.name
                    }
                }
                .toList()
                .sortedWith(
                    if (grouping == KeyGrouping.STORAGE)
                        compareByDescending { KeyStorage.valueOf(it.first).ordinal }
                    else compareBy { it.first },
                )
        }
    val selectedIds =
        groups.filter { it.first in selected }.flatMap { it.second }.mapTo(mutableSetOf()) { it.id }
    var confirming by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete keys") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    KeyGrouping.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = grouping == entry,
                            onClick = { grouping = entry },
                            shape =
                                SegmentedButtonDefaults.itemShape(index, KeyGrouping.entries.size),
                        ) {
                            Text(entry.label)
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${selected.size.counted(grouping.noun)} selected",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { selected = emptySet() },
                        enabled = selected.isNotEmpty(),
                    ) {
                        Text("Reset")
                    }
                }
                LazyColumn(Modifier.weight(1f, fill = false)) {
                    items(groups, key = { it.first }) { (value, members) ->
                        val first = members.first()
                        Row(
                            Modifier.fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .clickable { selected = selected.toggle(value) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = value in selected,
                                onCheckedChange = { selected = selected.toggle(value) },
                            )
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (grouping) {
                                        KeyGrouping.STORAGE -> first.storage.appearance.label
                                        else -> value
                                    },
                                    fontFamily =
                                        if (grouping == KeyGrouping.STORAGE) null
                                        else FontFamily.Monospace,
                                )
                                if (grouping == KeyGrouping.SYSTEM) {
                                    first.systemName?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                            Text(
                                members.size.counted("key"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { confirming = true },
                enabled = selectedIds.isNotEmpty(),
                colors =
                    ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    if (selectedIds.isEmpty()) "Delete"
                    else "Delete ${selectedIds.size.counted("key")}",
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Delete ${selectedIds.size.counted("key")}?") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(selectedIds) },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Cancel") } },
        )
    }
}

private fun Set<String>.toggle(value: String) = if (value in this) this - value else this + value

@Composable
private fun KeyDetailRow(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeyComponentsRow(components: List<DesKeyComponent>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Keys",
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (component in components) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(component.storage.appearance.icon),
                            contentDescription = component.storage.appearance.label,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            component.name,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NodeCodeSection(
    title: String,
    systemCode: String,
    codes: List<String>,
    versions: Map<String, Int>,
    type: NodeDefinitionType,
) {
    if (codes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (code in codes) {
                val name = NodeRegistry.getNodeName(systemCode, code, type)
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color =
                        if (name != null) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            code,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                        )
                        name?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                        versions[code]?.let {
                            Text(
                                "v$it",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val DesAuthenticationKeySummary.systemName: String?
    get() = NodeRegistry.getNodeName(systemCode, systemCode, NodeDefinitionType.SYSTEM)

private class KeyStorageChoice(val data: ByteArray, val options: List<KeyStorageOption>)

@Composable
private fun KeyStorageDialog(
    options: List<KeyStorageOption>,
    onSelect: (KeyStorage) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(options.firstOrNull { it.isAvailable }?.storage) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose key storage") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                options.forEach { option ->
                    KeyStorageOptionCard(
                        option = option,
                        selected = option.storage == selected,
                        onClick = { selected = option.storage },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { selected?.let(onSelect) }, enabled = selected != null) {
                Text("Import")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun KeyStorageOptionCard(option: KeyStorageOption, selected: Boolean, onClick: () -> Unit) {
    val appearance = option.storage.appearance
    val scheme = MaterialTheme.colorScheme
    SelectableOptionCard(
        icon = appearance.icon,
        color = appearance.color,
        title = appearance.label,
        description = appearance.description,
        selected = selected,
        enabled = option.isAvailable,
        onClick = onClick,
    ) {
        if (!option.isAvailable) {
            Text(
                if (option.isPresent) "Doesn't support DESede keys"
                else "Not available on this device",
                style = MaterialTheme.typography.labelMedium,
                color = scheme.error,
            )
        }
        option.operationTime
            ?.takeIf { option.isAvailable && option.storage != KeyStorage.SOFTWARE }
            ?.let { time ->
                Surface(
                    Modifier.padding(top = 6.dp),
                    shape = AssistChipDefaults.shape,
                    color = scheme.surfaceContainer,
                ) {
                    Text(
                        "~${time.toApproximateMillis()} ms per operation",
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
    }
}

private sealed interface KeyImportState {
    data class Importing(val imported: Int = 0, val total: Int? = null) : KeyImportState

    data class Imported(val result: CredentialImportResult, val storage: KeyStorage) :
        KeyImportState

    data class Failed(val message: String) : KeyImportState
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KeyImportDialog(state: KeyImportState, onDismiss: () -> Unit) {
    if (state is KeyImportState.Importing && state.total == null) {
        BusyDialog("Reading keys")
        return
    }
    val importStart =
        remember(state is KeyImportState.Importing && state.total != null) {
            TimeSource.Monotonic.markNow()
        }
    var canDismiss by remember(state) { mutableStateOf(state is KeyImportState.Imported) }
    LaunchedEffect(state) {
        if (state is KeyImportState.Failed) {
            delay(2.seconds)
            canDismiss = true
        }
    }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title =
            when (state) {
                is KeyImportState.Importing -> null
                is KeyImportState.Imported -> ({ Text("Keys imported") })
                is KeyImportState.Failed -> ({ Text("Key import failed") })
            },
        text = {
            when (state) {
                is KeyImportState.Importing -> {
                    val total = checkNotNull(state.total)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Importing keys: ${state.imported} of $total")
                        if (state.imported > 0 && state.imported < total) {
                            val remaining =
                                importStart.elapsedNow() / state.imported * (total - state.imported)
                            Text(
                                "About ${remaining.toEtaString()} remaining",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearWavyProgressIndicator(
                            progress = {
                                state.imported / total.toFloat()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                is KeyImportState.Imported -> {
                    val (imported, moved, alreadyStored, repeated, refreshed) = state.result
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Imported ${imported.counted("key")}")
                        if (moved > 0) {
                            Text(
                                "Moved ${moved.counted("key")} to " +
                                    state.storage.appearance.label,
                            )
                        }
                        if (refreshed > 0) {
                            Text("Refreshed ${refreshed.counted("key")}")
                        }
                        if (alreadyStored > 0) {
                            Text(
                                "Skipped ${alreadyStored.counted("key")} already in this provider",
                            )
                        }
                        if (repeated > 0) {
                            Text("Skipped ${repeated.counted("key")} repeated in this file")
                        }
                    }
                }
                is KeyImportState.Failed -> Text(state.message)
            }
        },
        confirmButton = {
            if (state !is KeyImportState.Importing) {
                TextButton(onClick = onDismiss, enabled = canDismiss) { Text("OK") }
            }
        },
    )
}

private val KeyStorage.appearance
    get() =
        when (this) {
            KeyStorage.DEDICATED_SECURE_MODULE ->
                OptionAppearance(
                    R.drawable.ic_shield_lock,
                    Color(0xFF188038),
                    "StrongBox",
                    "Dedicated security module. Strongest isolation, slower operations.",
                )
            KeyStorage.TRUSTED_EXECUTION_ENVIRONMENT ->
                OptionAppearance(
                    R.drawable.ic_memory,
                    Color(0xFF81C784),
                    "Trusted execution environment",
                    "Isolated environment on the main processor, faster operations.",
                )
            KeyStorage.SOFTWARE ->
                OptionAppearance(
                    R.drawable.ic_smartphone,
                    Color(0xFFF9A825),
                    "Android software keystore",
                    "Keys stay inside Android, without hardware isolation.",
                )
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KeyStorageIcon(storage: KeyStorage) {
    val appearance = storage.appearance
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(appearance.label) } },
        state = rememberTooltipState(),
    ) {
        Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.CenterStart) {
            Icon(
                painterResource(appearance.icon),
                contentDescription = appearance.label,
                modifier = Modifier.size(20.dp),
                tint = appearance.color,
            )
        }
    }
}

private fun Duration.toApproximateMillis(): String {
    val millis = inWholeMicroseconds / 1000.0
    val step =
        when {
            millis < 25 -> 2.5
            millis < 50 -> 5.0
            millis < 100 -> 10.0
            millis < 250 -> 25.0
            millis < 500 -> 50.0
            else -> 100.0
        }
    val rounded = ceil(millis / step).coerceAtLeast(1.0) * step
    return if (rounded % 1.0 == 0.0) rounded.toLong().toString() else rounded.toString()
}

private fun Duration.toEtaString(): String {
    val seconds = inWholeSeconds.coerceAtLeast(1)
    return if (seconds < 60) "$seconds s" else "${seconds / 60} min ${seconds % 60} s"
}

internal val desAuthenticationKeyProviderAppearance
    @Composable
    get() =
        OptionAppearance(
            R.drawable.ic_key,
            MaterialTheme.colorScheme.primary,
            AuthenticationProviderType.DES_AUTHENTICATION.displayName,
            "Authentication keys scoped to nodes contained within particular system on a particular card",
        )

internal suspend fun desAuthenticationKeyProviderDetails(
    localCredentials: LocalAuthenticationCredentials,
    providerId: String,
) = localCredentials.credentials(providerId).size.counted("key")
