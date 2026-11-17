package com.kormax.felicatool.ui

import android.content.ClipData
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.AppBarMenu
import com.kormax.felicatool.AppBarMenuGroup
import com.kormax.felicatool.R
import com.kormax.felicatool.authentication.AndroidKeyImportIdentities
import com.kormax.felicatool.authentication.KeyImportIdentity
import com.kormax.felicatool.device.KeystoreBackend
import com.kormax.felicatool.util.ExportUtils
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun KeyImportIdentitiesScreen(
    identities: AndroidKeyImportIdentities,
    onBackPressed: () -> Unit,
) {
    BackHandler(onBack = onBackPressed)
    val scope = rememberCoroutineScope()
    var backends by remember { mutableStateOf<List<KeystoreBackend>?>(null) }
    var current by remember { mutableStateOf(emptyMap<KeystoreBackend, KeyImportIdentity>()) }
    var generating by remember { mutableStateOf<KeystoreBackend?>(null) }
    var pendingGeneration by remember { mutableStateOf<PendingGeneration?>(null) }
    var pendingDeletion by remember { mutableStateOf<KeystoreBackend?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var softwareOnly by remember { mutableStateOf(false) }

    LaunchedEffect(identities) {
        try {
            val available = identities.backends()
            softwareOnly = !identities.hasHardwareKeystore()
            current =
                available
                    .mapNotNull { backend -> identities.identity(backend)?.let { backend to it } }
                    .toMap()
            backends = available
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            backends = emptyList()
            error = failure.message ?: "Import identities can't be read"
        }
    }

    fun generate(backend: KeystoreBackend, challenge: ByteArray?) {
        scope.launch {
            generating = backend
            error = null
            try {
                current = current + (backend to identities.generate(backend, challenge))
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                error = failure.message ?: "Import identity could not be generated"
            } finally {
                generating = null
            }
        }
    }

    Scaffold(
        topBar = { SettingsTopBar(title = "Import identities", onBackPressed = onBackPressed) },
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
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                Row(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Import identities enable secure key import bound to this " +
                                "device's hardware.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "To import authentication keys from someone else, generate an " +
                                "import identity and share it with them.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val available = backends
            if (softwareOnly) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            "Only a software keystore is available",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "This device has no secure hardware to hold an import identity. " +
                                "Keys can only be imported from plain key files.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else if (available != null && available.isEmpty() && error == null) {
                Text(
                    "This device's secure hardware can't import wrapped keys.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            available?.forEach { backend ->
                IdentityCard(
                    backend = backend,
                    identity = current[backend],
                    enabled = generating == null,
                    onGenerate = {
                        scope.launch {
                            pendingGeneration =
                                PendingGeneration(
                                    backend,
                                    identities.generationForgetsIdentity(backend),
                                )
                        }
                    },
                    onDelete = { pendingDeletion = backend },
                )
            }
            if (!available.isNullOrEmpty()) {
                Text(
                    "Import identities include information about this device's hardware and " +
                        "software state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.padding(bottom = 16.dp))
        }
    }

    generating?.let { BusyDialog("Generating ${it.label} identity") }

    pendingDeletion?.let { backend ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Delete ${backend.label} identities?") },
            text = {
                Text(
                    "All ${backend.label} identities will be deleted. Key files prepared for " +
                        "them that haven't been imported yet will no longer import. Keys you've " +
                        "already imported aren't affected.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeletion = null
                        scope.launch {
                            try {
                                identities.delete(backend)
                                current = current - backend
                            } catch (failure: CancellationException) {
                                throw failure
                            } catch (failure: Exception) {
                                error = failure.message ?: "Import identities could not be deleted"
                            }
                        }
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("Cancel") } },
        )
    }

    pendingGeneration?.let { pending ->
        GenerateIdentityDialog(
            pending = pending,
            onDismiss = { pendingGeneration = null },
            onGenerate = { challenge ->
                pendingGeneration = null
                generate(pending.backend, challenge)
            },
        )
    }
}

@Composable
private fun IdentityCard(
    backend: KeystoreBackend,
    identity: KeyImportIdentity?,
    enabled: Boolean,
    onGenerate: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painterResource(
                                if (backend == KeystoreBackend.TEE) R.drawable.ic_memory
                                else R.drawable.ic_shield_lock,
                            ),
                            null,
                            Modifier.size(28.dp),
                        )
                    }
                }
                Text(
                    backend.label,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (identity == null) {
                    Button(onClick = onGenerate, enabled = enabled) { Text("Generate") }
                } else {
                    AppBarMenu(
                        icon = Icons.Filled.MoreVert,
                        label = "${backend.label} identity actions",
                    ) { dismiss ->
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                            DropdownMenuItem(
                                text = { Text("Generate new identity") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                shape = MenuDefaults.leadingItemShape,
                                enabled = enabled,
                                onClick = {
                                    dismiss()
                                    onGenerate()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete identities") },
                                leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                shape = MenuDefaults.trailingItemShape,
                                enabled = enabled,
                                onClick = {
                                    dismiss()
                                    onDelete()
                                },
                            )
                        }
                    }
                }
            }
            if (identity != null) {
                Text(
                    "Created " +
                        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                            .withZone(ZoneId.systemDefault())
                            .format(identity.created),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    identity.id.uppercase().chunked(4).joinToString(" "),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(
                                        ClipData.newPlainText(
                                            "${backend.label} import identity",
                                            identity.request,
                                        ),
                                    ),
                                )
                            }
                        },
                    ) {
                        Text("Copy")
                    }
                    OutlinedButton(
                        onClick = {
                            ExportUtils.shareFile(
                                context,
                                "felica-tool-import-identity-${backend.name.lowercase()}-" +
                                    "${identity.id.take(8)}.txt",
                                identity.request,
                                "text/plain",
                            )
                        },
                    ) {
                        Text("Share")
                    }
                }
            }
        }
    }
}

private class PendingGeneration(val backend: KeystoreBackend, val forgetsOldest: Boolean)

@Composable
private fun GenerateIdentityDialog(
    pending: PendingGeneration,
    onDismiss: () -> Unit,
    onGenerate: (ByteArray?) -> Unit,
) {
    var challenge by remember { mutableStateOf("") }
    val bytes = challenge.takeIf { it.isNotEmpty() }?.encodeToByteArray()
    val tooLong = (bytes?.size ?: 0) > AndroidKeyImportIdentities.MAX_CHALLENGE_BYTES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate ${pending.backend.label} identity") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (pending.forgetsOldest) {
                    Text(
                        "The oldest ${pending.backend.label} identity will be forgotten. Key " +
                            "files prepared for it that haven't been imported yet will no longer " +
                            "import.",
                    )
                }
                OutlinedTextField(
                    value = challenge,
                    onValueChange = { challenge = it },
                    label = { Text("Challenge") },
                    singleLine = true,
                    isError = tooLong,
                    supportingText = {
                        Text(
                            if (tooLong) {
                                "At most ${AndroidKeyImportIdentities.MAX_CHALLENGE_BYTES} bytes"
                            } else {
                                "If you were given a challenge, enter it here. Otherwise a random one is used."
                            },
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGenerate(bytes) }, enabled = !tooLong) { Text("Generate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
