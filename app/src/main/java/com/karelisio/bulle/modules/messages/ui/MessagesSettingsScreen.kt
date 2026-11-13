package com.karelisio.bulle.modules.messages.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.apps.AppCatalog
import com.karelisio.bulle.core.apps.AppEntry
import com.karelisio.bulle.core.apps.AppIcon
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import com.karelisio.bulle.modules.messages.MessagesModule
import com.karelisio.bulle.modules.messages.MessagesPrefs
import com.karelisio.bulle.modules.messages.MessagesSettings
import com.karelisio.bulle.ui.settings.AppList
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

/** Réglages du module « Messages » : activation, durée, ouverture auto, contenu, applis exclues. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessagesSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val prefs by remember(context) { MessagesPrefs.loaded(context) }.collectAsStateWithLifecycle()
    val bulle by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val enabled = MessagesModule.isEnabled(bulle)
    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    var picking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { apps = runCatching { AppCatalog.apps(context) }.getOrDefault(emptyList()) }

    ModuleSettingsScaffold(stringResource(R.string.messages_settings_title), onBack) {
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.messages_settings_enable),
                    summary = stringResource(R.string.messages_settings_enable_summary),
                    checked = enabled,
                ) { on ->
                    scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (MessagesModule.ID to on)) } }
                }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.messages_settings_behavior)) }
        item {
            ModuleSettingsCard {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.messages_settings_timeout)) },
                    supportingContent = {
                        Slider(
                            value = prefs.timeoutMs / 1000f,
                            onValueChange = { v ->
                                val ms = (v.roundToLong() * 1000).coerceIn(MessagesSettings.MIN_TIMEOUT_MS, MessagesSettings.MAX_TIMEOUT_MS)
                                if (ms != prefs.timeoutMs) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                MessagesPrefs.update(context) { it.copy(timeoutMs = ms) }
                            },
                            valueRange = MessagesSettings.MIN_TIMEOUT_MS / 1000f..MessagesSettings.MAX_TIMEOUT_MS / 1000f,
                            steps = ((MessagesSettings.MAX_TIMEOUT_MS - MessagesSettings.MIN_TIMEOUT_MS) / 1000 - 1).toInt(),
                            enabled = enabled,
                        )
                    },
                    trailingContent = { Text(String.format(Locale.getDefault(), "%d s", prefs.timeoutMs / 1000)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                ModuleSwitchRow(
                    title = stringResource(R.string.messages_settings_auto_expand),
                    summary = stringResource(R.string.messages_settings_auto_expand_summary),
                    checked = prefs.autoExpand,
                    enabled = enabled,
                ) { on -> MessagesPrefs.update(context) { it.copy(autoExpand = on) } }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.messages_settings_privacy_header)) }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.messages_settings_show_content),
                    summary = stringResource(R.string.messages_settings_show_content_summary),
                    checked = prefs.showContent,
                    enabled = enabled,
                ) { on -> MessagesPrefs.update(context) { it.copy(showContent = on) } }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.messages_settings_excluded)) },
                    supportingContent = { Text(stringResource(R.string.messages_settings_excluded_summary)) },
                    trailingContent = {
                        TextButton(onClick = { picking = true }, enabled = enabled) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text(stringResource(R.string.messages_settings_add))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                prefs.excludedApps.sorted().forEach { pkg ->
                    val app = apps.firstOrNull { it.packageName == pkg }
                    ListItem(
                        headlineContent = { Text(app?.label ?: pkg) },
                        leadingContent = { if (app != null) AppIcon(app, 36.dp) },
                        trailingContent = {
                            IconButton(onClick = { MessagesPrefs.update(context) { it.copy(excludedApps = it.excludedApps - pkg) } }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.messages_settings_remove))
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
        }
        item {
            Text(
                stringResource(R.string.messages_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            AppList { app ->
                MessagesPrefs.update(context) { it.copy(excludedApps = it.excludedApps + app.packageName) }
                picking = false
            }
        }
    }
}
