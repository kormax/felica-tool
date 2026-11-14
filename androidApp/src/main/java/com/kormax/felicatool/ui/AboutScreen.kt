package com.kormax.felicatool.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.AppBarMenu
import com.kormax.felicatool.AppBarMenuGroup
import com.kormax.felicatool.BuildConfig
import com.kormax.felicatool.R
import com.kormax.felicatool.device.OsaifuKeitai
import com.kormax.felicatool.device.OsaifuKeitaiInfo
import com.kormax.felicatool.device.OsaifuKeitaiStatus
import com.kormax.felicatool.util.ExportUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AboutScreen(onBackPressed: () -> Unit) {
    BackHandler(onBack = onBackPressed)
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
                        AppBarMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                            DropdownMenuItem(
                                text = { Text("Share info") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Info, contentDescription = null)
                                },
                                shape = MenuDefaults.standaloneItemShape,
                                enabled = osaifuKeitai != null,
                                onClick = {
                                    dismiss()
                                    osaifuKeitai?.let { ExportUtils.shareDeviceInfo(context, it) }
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

private val androidApiVersion: String
    get() =
        if (Build.VERSION.SDK_INT >= 36) {
            val full = Build.VERSION.SDK_INT_FULL
            "${Build.getMajorSdkVersion(full)}.${Build.getMinorSdkVersion(full)}"
        } else {
            Build.VERSION.SDK_INT.toString()
        }
