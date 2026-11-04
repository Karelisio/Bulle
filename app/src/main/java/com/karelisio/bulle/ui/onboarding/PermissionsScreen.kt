package com.karelisio.bulle.ui.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.system.OemSettings
import com.karelisio.bulle.ui.components.cascade
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Copie la commande adb et le confirme par un Snackbar (et une petite vibration). */
internal fun copyAdbCommand(context: Context, snackbar: SnackbarHostState, scope: CoroutineScope, message: String) {
    context.getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("adb", ADB_GRANT_COMMAND))
    scope.launch { snackbar.showSnackbar(message) }
}

/** Bloc monospace copiable contenant la commande adb. */
@Composable
internal fun AdbCommandBlock(onCopy: () -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                ADB_GRANT_COMMAND,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                softWrap = false,
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 14.dp),
            )
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                onCopy()
            }) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.rel_copy))
            }
        }
    }
}

/** Liste de toutes les autorisations, avec leur état en direct et un accès à l'écran système. */
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val permissions = rememberPermissionController()
    val family = remember { OemSettings.family }
    val voletEnabled by remember { SettingsRepository.flow(context).map { it.volet.enabled } }
        .collectAsStateWithLifecycle(false)
    val essential = remember(voletEnabled) { PermissionLogic.onboardingSteps(Build.VERSION.SDK_INT, family, voletEnabled) }
    val onDemand = remember(essential) { PermissionLogic.onDemand - essential.toSet() }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val copied = stringResource(R.string.rel_copied)

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.permissions_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            var index = 0
            item { SectionHeader(stringResource(R.string.perm_section_essential), null) }
            essential.forEach { key ->
                val i = index++
                item(key.name) { PermissionRow(key, permissions, family, optional = false, index = i) }
            }
            item {
                SectionHeader(
                    stringResource(R.string.perm_section_on_demand),
                    stringResource(R.string.perm_section_on_demand_desc),
                )
            }
            onDemand.forEach { key ->
                val i = index++
                item(key.name) { PermissionRow(key, permissions, family, optional = true, index = i) }
            }
            item { SectionHeader(stringResource(R.string.perm_section_advanced), null) }
            item(PermissionKey.SECURE_SETTINGS.name) {
                PermissionRow(PermissionKey.SECURE_SETTINGS, permissions, family, optional = true, index = index) {
                    Spacer(Modifier.height(12.dp))
                    AdbCommandBlock(onCopy = { copyAdbCommand(context, snackbar, scope, copied) })
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?) {
    Column(Modifier.padding(start = 8.dp, top = 16.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionRow(
    key: PermissionKey,
    permissions: PermissionController,
    family: OemSettings.Family,
    optional: Boolean,
    index: Int,
    extra: @Composable () -> Unit = {},
) {
    val granted = permissions[key]
    val texts = key.texts(family)
    val manual = PermissionLogic.isManual(key)
    val state = when {
        granted -> PillState.GRANTED
        manual -> PillState.PENDING
        optional -> PillState.OPTIONAL
        else -> PillState.DENIED
    }
    ConfirmHaptic(granted)
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .cascade(index),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PermissionGlyph(texts.icon, granted, size = 44.dp, animated = false)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(texts.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(texts.desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            extra()
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(state)
                Spacer(Modifier.weight(1f))
                if (manual) {
                    Checkbox(checked = granted || permissions.isConfirmed(key), onCheckedChange = { permissions.setConfirmed(key, it) })
                    Text(stringResource(R.string.onb_done_check), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                }
                if (key != PermissionKey.SECURE_SETTINGS) {
                    if (granted && !manual) {
                        TextButton(onClick = { permissions.request(key) }) { Text(stringResource(R.string.perm_manage)) }
                    } else {
                        FilledTonalButton(onClick = { permissions.request(key) }) { Text(stringResource(R.string.perm_open)) }
                    }
                }
            }
        }
    }
}
