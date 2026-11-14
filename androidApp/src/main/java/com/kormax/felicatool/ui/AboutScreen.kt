package com.kormax.felicatool.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.AppBarMenu
import com.kormax.felicatool.AppBarMenuGroup
import com.kormax.felicatool.BuildConfig
import com.kormax.felicatool.R
import com.kormax.felicatool.device.*
import com.kormax.felicatool.util.ExportUtils
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AboutScreen(onBackPressed: () -> Unit) {
    BackHandler(onBack = onBackPressed)
    val state by KeystoreCapabilities.measurements.collectAsState()
    val context = LocalContext.current
    val osaifuKeitai by produceState<OsaifuKeitaiInfo?>(null) { value = OsaifuKeitai.detect() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    AppBarMenu(icon = Icons.Filled.Share, label = "Share") { dismiss ->
                        val profile = (state as? MeasurementState.Ready)?.profile
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                            DropdownMenuItem(
                                text = { Text("Share info") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Info, contentDescription = null)
                                },
                                shape = MenuDefaults.standaloneItemShape,
                                enabled = osaifuKeitai != null && profile != null,
                                onClick = {
                                    dismiss()
                                    val osaifu = osaifuKeitai
                                    if (osaifu != null && profile != null)
                                        ExportUtils.shareDeviceInfo(context, osaifu, profile)
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                IdentityCard(
                    "FeliCa Tool",
                    "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    R.drawable.ic_launcher_foreground,
                    app = true,
                )
            }
            item {
                IdentityCard(
                    Build.MODEL,
                    "Android ${Build.VERSION.RELEASE} (API $androidApiVersion)",
                    R.drawable.ic_smartphone,
                    detail =
                        Build.VERSION.SECURITY_PATCH.takeIf { it.isNotBlank() }
                            ?.let { "Security patch $it" },
                )
            }
            item { OsaifuKeitaiCard(osaifuKeitai) }
            when (val result = state) {
                is MeasurementState.Measuring ->
                    item {
                        ListItem(leadingContent = { LoadingIndicator(Modifier.size(32.dp)) }) {
                            Text(result.detail)
                        }
                    }
                MeasurementState.Failed ->
                    item {
                        ListItem(
                            trailingContent = {
                                TextButton(onClick = { KeystoreCapabilities.start(context) }) {
                                    Text("Retry")
                                }
                            },
                        ) {
                            Text("Keystore measurements unavailable")
                        }
                    }
                is MeasurementState.Ready -> {
                    for (backend in result.profile.backends) {
                        item(backend.backend.name) {
                            BackendCard(backend, result) {
                                KeystoreCapabilities.refresh(context, backend.backend)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdentityCard(
    title: String,
    subtitle: String,
    icon: Int,
    app: Boolean = false,
    detail: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors =
            CardDefaults.cardColors(
                containerColor = if (app) scheme.primaryContainer else scheme.surfaceContainer,
            ),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = if (app) 14.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = if (app) scheme.primary else scheme.secondaryContainer,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, Modifier.size(if (app) 48.dp else 28.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style =
                        if (app) MaterialTheme.typography.headlineSmall
                        else MaterialTheme.typography.titleLarge,
                    fontWeight = if (app) FontWeight.SemiBold else FontWeight.Medium,
                )
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
                detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OsaifuKeitaiCard(info: OsaifuKeitaiInfo?) {
    val scheme = MaterialTheme.colorScheme
    val status = info?.status
    var showConfiguration by rememberSaveable { mutableStateOf(false) }
    val hasConfiguration = !info?.configurationFiles.isNullOrEmpty()
    val pixel = "${Build.MANUFACTURER} ${Build.MODEL} model code ${OsaifuKeitai.modelCode}"
    val description =
        when (status) {
            null -> "Checking FeliCa configuration"
            OsaifuKeitaiStatus.NO_FELICA_CONFIGURATION ->
                "FeliCa configuration is missing from device firmware."
            OsaifuKeitaiStatus.BLOCKED_MODEL_CODE -> "$pixel is not allowed to use Osaifu-Keitai."
            OsaifuKeitaiStatus.SUPPORTED_IN_JAPAN -> "Supported only while in Japan."
            OsaifuKeitaiStatus.SUPPORTED -> null
            OsaifuKeitaiStatus.UNKNOWN -> "Support could not be determined."
        }
    Card(
        Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .combinedClickable(
                enabled = hasConfiguration,
                onClick = { showConfiguration = true },
                onLongClick = { showConfiguration = true },
            ),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = scheme.secondaryContainer,
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (status == null) LoadingIndicator(Modifier.size(32.dp))
                    else
                        Icon(
                            painterResource(R.drawable.ic_osaifu_keitai),
                            null,
                            Modifier.size(28.dp),
                        )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Osaifu-Keitai",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
                val chip =
                    when (status) {
                        null -> null
                        OsaifuKeitaiStatus.NO_FELICA_CONFIGURATION ->
                            scheme.errorContainer to scheme.onErrorContainer
                        OsaifuKeitaiStatus.BLOCKED_MODEL_CODE -> Color(0xFFE64A19) to Color.White
                        OsaifuKeitaiStatus.SUPPORTED_IN_JAPAN ->
                            Color(0xFF81C784) to Color(0xFF0D3B16)
                        OsaifuKeitaiStatus.SUPPORTED -> Color(0xFF188038) to Color.White
                        OsaifuKeitaiStatus.UNKNOWN ->
                            scheme.surfaceContainerHighest to scheme.onSurfaceVariant
                    }
                if (status != null && chip != null) {
                    Surface(
                        shape = AssistChipDefaults.shape,
                        color = chip.first,
                        contentColor = chip.second,
                    ) {
                        Text(
                            status.label,
                            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (hasConfiguration) {
                IconButton(onClick = { showConfiguration = true }) {
                    Icon(Icons.Filled.Info, "Show FeliCa configuration")
                }
            }
        }
    }
    if (showConfiguration && info != null) {
        AlertDialog(
            onDismissRequest = { showConfiguration = false },
            title = { Text("FeliCa configuration") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    for ((path, bytes) in info.configurationFiles) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = scheme.surfaceContainerHigh,
                        ) {
                            Column {
                                Surface(color = scheme.secondaryContainer) {
                                    Text(
                                        path,
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                                Text(
                                    bytes?.decodeToString() ?: "Not readable",
                                    Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showConfiguration = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun BackendCard(
    report: BackendMeasurement,
    state: MeasurementState.Ready,
    onRefresh: () -> Unit,
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val tee = report.backend == KeystoreBackend.TEE
    val shape = MaterialTheme.shapes.extraLarge
    Card(
        Modifier.fillMaxWidth()
            .clip(shape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Show ${report.backend.label} measurements",
                onLongClickLabel = "Show detailed measurements",
                onLongClick = { showDetails = true },
                onClick = { showDetails = true },
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = scheme.secondaryContainer),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = MaterialTheme.shapes.large, color = scheme.secondary) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        ImageVector.vectorResource(
                            if (tee) R.drawable.ic_memory else R.drawable.ic_shield_lock,
                        ),
                        null,
                        Modifier.size(28.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    report.backend.label,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
                val identity =
                    listOfNotNull(report.implementation, report.attestedVersionLabel)
                        .joinToString(" · ")
                if (identity.isNotEmpty())
                    Text(identity, style = MaterialTheme.typography.bodySmall)
                FlowRow(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CapabilityChip(
                        if (report.attestedIds.isNotEmpty()) "Attestation · ID" else "Attestation",
                        report.attestation,
                    )
                    for ((algorithm, result) in report.algorithms) {
                        if (
                            result.generation == Support.YES ||
                                result.rawImport.support == Support.YES ||
                                result.wrappedImport.support == Support.YES
                        ) {
                            CapabilityChip(algorithm.label, Support.YES)
                        } else {
                            CapabilityChip(algorithm.label, Support.NO)
                        }
                    }
                }
            }
            IconButton(onClick = { showDetails = true }) {
                Icon(Icons.Filled.Info, "Show ${report.backend.label} measurements")
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            modifier = Modifier.semantics { paneTitle = report.backend.label },
            title = { Text(report.backend.label) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.refreshFailed == report.backend) {
                        Text("Couldn’t refresh measurements", color = scheme.error)
                    }
                    BackendDetails(report)
                    AlgorithmTable(report)
                }
            },
            confirmButton = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onRefresh, enabled = state.refreshing == null) {
                        Text(if (state.refreshing == report.backend) "Refreshing…" else "Refresh")
                    }
                    TextButton(onClick = { showDetails = false }) { Text("Close") }
                }
            },
        )
    }
}

@Composable
private fun BackendDetails(report: BackendMeasurement) {
    val details =
        buildList<Pair<String, Any>> {
            if (
                report.backend == KeystoreBackend.STRONGBOX &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN
            ) {
                add("Manufacturer" to Build.STRONGBOX_MANUFACTURER)
                add("Model" to Build.STRONGBOX_MODEL)
            }
            if (report.backend == KeystoreBackend.TEE)
                add("Implementation" to (report.implementation ?: "Unknown"))
            report.attestedVersionLabel?.let { add("Version" to it) }
            add("Key attestation" to report.attestation)
            add(
                "Device ID attestation" to
                    if (report.attestedIds.isNotEmpty()) Support.YES else Support.NO,
            )
            add(
                "Maximum simultaneous occupancy" to
                    (report.concurrentOccupancy?.toString() ?: "Unknown"),
            )
        }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((label, value) in details) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (value is Support) SupportIcon(value)
                else Text(value.toString(), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AlgorithmTable(report: BackendMeasurement) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Algorithm",
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
            )
            for (header in listOf("Gen", "Raw", "Wrap", "Enc", "Dec")) {
                Text(
                    header,
                    Modifier.width(44.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        HorizontalDivider()
        for ((algorithm, measurement) in report.algorithms) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    algorithm.label,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false,
                )
                MeasurementCell(measurement.generation, measurement.generationNanos)
                MeasurementCell(measurement.rawImport.result)
                MeasurementCell(measurement.wrappedImport.result)
                MeasurementCell(Support.YES, measurement.encryptionNanos)
                MeasurementCell(Support.YES, measurement.decryptionNanos)
            }
        }
    }
}

private val ImportMeasurement.result: Support
    get() = if (support == Support.YES && keyInfoError != null) Support.INCONCLUSIVE else support

@Composable
private fun MeasurementCell(support: Support, samples: List<Long>? = null) {
    Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
        when {
            support == Support.YES && samples == null -> SupportIcon(support)
            support == Support.YES && samples!!.isNotEmpty() ->
                Text(
                    millis(samples),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            support == Support.YES -> SupportIcon(Support.NO)
            else -> SupportIcon(support)
        }
    }
}

@Composable
private fun SupportIcon(support: Support) {
    val scheme = MaterialTheme.colorScheme
    when (support) {
        Support.YES ->
            Icon(Icons.Filled.Check, support.label, Modifier.size(18.dp), tint = scheme.primary)
        Support.NO ->
            Icon(Icons.Filled.Close, support.label, Modifier.size(18.dp), tint = scheme.error)
        Support.INCONCLUSIVE ->
            Box(
                Modifier.size(18.dp).semantics { contentDescription = support.label },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(12.dp).border(2.dp, scheme.tertiary, CircleShape))
            }
    }
}

private fun millis(samples: List<Long>): String {
    val value = samples.average() / 1_000_000.0
    return if (value < 10) "%.1f".format(value) else value.roundToLong().toString()
}

private val Support.label: String
    get() =
        when (this) {
            Support.YES -> "Yes"
            Support.NO -> "No"
            Support.INCONCLUSIVE -> "Inconclusive"
        }

@Composable
private fun CapabilityChip(label: String, support: Support) {
    val icon =
        when (support) {
            Support.YES -> Icons.Filled.Check
            Support.NO -> Icons.Filled.Close
            Support.INCONCLUSIVE -> Icons.Filled.Warning
        }
    val content = LocalContentColor.current
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = AssistChipDefaults.shape,
        color = if (support == Support.NO) scheme.errorContainer else content.copy(alpha = 0.08f),
        contentColor = if (support == Support.NO) scheme.onErrorContainer else content,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, support.label, Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
