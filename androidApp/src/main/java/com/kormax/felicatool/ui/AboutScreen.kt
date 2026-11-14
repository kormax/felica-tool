package com.kormax.felicatool.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kormax.felicatool.AppBarMenu
import com.kormax.felicatool.AppBarMenuGroup
import com.kormax.felicatool.BuildConfig
import com.kormax.felicatool.R
import com.kormax.felicatool.util.ExportUtils

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AboutScreen(onBackPressed: () -> Unit) {
    BackHandler(onBack = onBackPressed)
    val context = LocalContext.current
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
                                onClick = {
                                    dismiss()
                                    ExportUtils.shareDeviceInfo(context)
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

private val androidApiVersion: String
    get() =
        if (Build.VERSION.SDK_INT >= 36) {
            val full = Build.VERSION.SDK_INT_FULL
            "${Build.getMajorSdkVersion(full)}.${Build.getMinorSdkVersion(full)}"
        } else {
            Build.VERSION.SDK_INT.toString()
        }
