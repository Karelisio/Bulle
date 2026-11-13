package com.karelisio.bulle.modules.screentime.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import com.karelisio.bulle.modules.screentime.AppIcon
import com.karelisio.bulle.modules.screentime.ScreenTimeDetails
import com.karelisio.bulle.modules.screentime.ScreenTimeModule
import com.karelisio.bulle.modules.screentime.ScreenTimePrefs
import com.karelisio.bulle.modules.screentime.ScreenTimeReport
import com.karelisio.bulle.modules.screentime.ScreenTimeSettings
import com.karelisio.bulle.modules.screentime.UsageAccess
import com.karelisio.bulle.modules.screentime.formatScreenDuration
import com.karelisio.bulle.modules.screentime.rememberAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Réglages du module « Temps d'écran » : accès aux données d'utilisation (état + bouton vers les
 * paramètres, revérifié au retour), aperçu du jour, rappel de pause, limites par appli.
 */
@Composable
fun ScreenTimeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val screenTime by remember(context) { ScreenTimePrefs.store.loaded(context) }.collectAsStateWithLifecycle()
    val enabled = ScreenTimeModule.isEnabled(settings)
    var access by remember { mutableStateOf(UsageAccess.hasAccess(context)) }
    var report by remember { mutableStateOf<ScreenTimeReport?>(null) }
    var refresh by remember { mutableStateOf(0) }

    // Retour des paramètres système : l'accès a pu changer.
    LifecycleResumeEffect(Unit) {
        access = UsageAccess.hasAccess(context)
        refresh++
        onPauseOrDispose { }
    }
    LaunchedEffect(access, refresh) {
        report = if (access) withContext(Dispatchers.IO) { UsageAccess.report(context) } else null
    }

    fun update(transform: (ScreenTimeSettings) -> ScreenTimeSettings) {
        ScreenTimePrefs.store.update(context, transform)
    }

    ModuleSettingsScaffold(title = stringResource(R.string.screentime_settings_title), onBack = onBack) {
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.screentime_settings_enable),
                    summary = stringResource(R.string.screentime_settings_enable_summary),
                    checked = enabled,
                ) { on -> scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (ScreenTimeModule.ID to on)) } } }
            }
        }
        item {
            ModuleSettingsCard {
                ListItem(
                    leadingContent = {
                        Icon(
                            if (access) Icons.Rounded.CheckCircle else Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (access) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    headlineContent = { Text(stringResource(R.string.screentime_settings_access)) },
                    supportingContent = {
                        Text(stringResource(if (access) R.string.screentime_settings_access_on else R.string.screentime_settings_access_off))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                if (!access) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                        FilledTonalButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            UsageAccess.openSettings(context)
                        }) { Text(stringResource(R.string.screentime_settings_grant)) }
                    }
                }
            }
        }
        if (access) {
            item {
                ModuleSettingsCard { ScreenTimeDetails(report, hasAccess = true, modifier = Modifier.padding(16.dp), onDark = false) }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.screentime_settings_break_header)) }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.screentime_settings_break),
                    summary = stringResource(R.string.screentime_settings_break_summary),
                    checked = screenTime.breakReminder,
                    enabled = enabled,
                ) { on -> update { it.copy(breakReminder = on) } }
                val choices = ScreenTimeSettings.BREAK_CHOICES
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    choices.forEachIndexed { i, minutes ->
                        SegmentedButton(
                            selected = screenTime.breakMinutes == minutes,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                update { it.copy(breakMinutes = minutes) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                            enabled = enabled && screenTime.breakReminder,
                        ) { Text(stringResource(R.string.screentime_duration_m, minutes), maxLines = 1, softWrap = false) }
                    }
                }
                ModuleSwitchRow(
                    title = stringResource(R.string.screentime_settings_breathing),
                    summary = stringResource(R.string.screentime_settings_breathing_summary),
                    checked = screenTime.breakBreathing,
                    enabled = enabled && screenTime.breakReminder,
                ) { on -> update { it.copy(breakBreathing = on) } }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.screentime_settings_display_header)) }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.screentime_settings_ambient),
                    summary = stringResource(R.string.screentime_settings_ambient_summary),
                    checked = screenTime.showAmbient,
                    enabled = enabled && access,
                ) { on -> update { it.copy(showAmbient = on) } }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.screentime_settings_limits_header)) }
        item {
            ModuleSettingsCard {
                Text(
                    stringResource(if (access) R.string.screentime_settings_limits_summary else R.string.screentime_no_access),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                if (access) {
                    // Applis du jour, puis celles qui ont déjà une limite.
                    val today = report?.today
                    val packages = (today?.top(8)?.map { it.first }.orEmpty() + screenTime.appLimits.keys).distinct()
                    packages.forEach { pkg ->
                        LimitRow(
                            pkg = pkg,
                            usedMs = today?.perApp?.get(pkg) ?: 0L,
                            limit = screenTime.appLimits[pkg] ?: 0,
                            enabled = enabled,
                        ) { minutes ->
                            update { s -> s.copy(appLimits = if (minutes <= 0) s.appLimits - pkg else s.appLimits + (pkg to minutes)) }
                        }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.screentime_settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun LimitRow(pkg: String, usedMs: Long, limit: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val info = rememberAppInfo(pkg)
    var open by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    ListItem(
        leadingContent = { AppIcon(pkg, 32.dp) },
        headlineContent = { Text(info.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(stringResource(R.string.screentime_settings_used_today, formatScreenDuration(usedMs))) },
        trailingContent = {
            Box {
                TextButton(onClick = { open = true }, enabled = enabled) {
                    Text(if (limit > 0) formatScreenDuration(limit * 60_000L) else stringResource(R.string.screentime_settings_no_limit), maxLines = 1)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    ScreenTimeSettings.LIMIT_CHOICES.forEach { minutes ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (minutes > 0) formatScreenDuration(minutes * 60_000L) else stringResource(R.string.screentime_settings_no_limit))
                                    if (minutes == limit) {
                                        Spacer(Modifier.width(8.dp))
                                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onChange(minutes)
                                open = false
                            },
                        )
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
