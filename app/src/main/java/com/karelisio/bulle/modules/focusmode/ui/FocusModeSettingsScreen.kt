package com.karelisio.bulle.modules.focusmode.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.ModuleRegistry
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import com.karelisio.bulle.modules.focusmode.FocusMode
import com.karelisio.bulle.modules.focusmode.FocusModeSettings
import com.karelisio.bulle.modules.focusmode.FocusModePrefs
import com.karelisio.bulle.modules.focusmode.FocusModuleMode
import com.karelisio.bulle.modules.focusmode.FocusSlot
import com.karelisio.bulle.modules.focusmode.FocusSource
import com.karelisio.bulle.modules.focusmode.FocusState
import com.karelisio.bulle.modules.rules.ui.dayShort
import com.karelisio.bulle.ui.settings.AppList

/** Écran de réglages du mode focus. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusModeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val settings by remember { FocusModePrefs.loaded(context) }.collectAsStateWithLifecycle()
    val state by FocusMode.state.collectAsStateWithLifecycle()
    var pickingApp by remember { mutableStateOf(false) }
    var timeEdit by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }

    fun update(transform: (FocusModeSettings) -> FocusModeSettings) = FocusModePrefs.update(context, transform)
    fun updateSlot(i: Int, transform: (FocusSlot) -> FocusSlot) =
        update { s -> s.copy(slots = s.slots.mapIndexed { j, slot -> if (j == i) transform(slot) else slot }) }

    ModuleSettingsScaffold(title = stringResource(R.string.focusmode_title), onBack = onBack) {
        item(key = "status") {
            StatusCard(state) {
                haptic.performHapticFeedback(HapticFeedbackType.ToggleOn)
                FocusMode.toggle()
            }
        }

        item(key = "essential_h") { ModuleSectionHeader(stringResource(R.string.focusmode_essential)) }
        item(key = "essential") {
            ModuleSettingsCard {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.focusmode_threshold_hint), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FocusModeSettings.THRESHOLDS.forEach { t ->
                            FilterChip(
                                selected = settings.minPriority == t,
                                onClick = { update { it.copy(minPriority = t) } },
                                label = { Text(stringResource(thresholdLabel(t))) },
                            )
                        }
                    }
                }
            }
        }

        item(key = "modules_h") { ModuleSectionHeader(stringResource(R.string.focusmode_modules)) }
        item(key = "modules") {
            ModuleSettingsCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    ModuleRegistry.modules.forEach { m ->
                        val mode = settings.modeOf(m.id)
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(stringResource(m.titleRes), style = MaterialTheme.typography.bodyLarge)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FocusModuleMode.entries.forEach { opt ->
                                    FilterChip(
                                        selected = mode == opt,
                                        onClick = { update { s -> s.copy(modules = s.modules + (m.id to opt)) } },
                                        label = { Text(stringResource(opt.labelRes())) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "slots_h") { ModuleSectionHeader(stringResource(R.string.focusmode_schedule)) }
        if (settings.slots.isEmpty()) {
            item(key = "slots_empty") {
                Text(
                    stringResource(R.string.focusmode_schedule_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        settings.slots.forEachIndexed { i, slot ->
            item(key = "slot_$i") {
                ModuleSettingsCard {
                    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { timeEdit = i to true }) { Text(hhmm(slot.startMinute)) }
                                OutlinedButton(onClick = { timeEdit = i to false }) { Text(hhmm(slot.endMinute)) }
                            }
                            Switch(checked = slot.enabled, onCheckedChange = { on -> updateSlot(i) { it.copy(enabled = on) } })
                            IconButton(onClick = { update { s -> s.copy(slots = s.slots.filterIndexed { j, _ -> j != i }) } }) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.focusmode_remove))
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..7).forEach { d ->
                                FilterChip(
                                    selected = d in slot.days,
                                    onClick = { updateSlot(i) { it.copy(days = if (d in it.days) it.days - d else it.days + d) } },
                                    label = { Text(dayShort(d)) },
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "slot_add") {
            FilledTonalButton(onClick = { update { it.copy(slots = it.slots + FocusSlot()) } }) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.focusmode_add_slot))
            }
        }

        item(key = "apps_h") { ModuleSectionHeader(stringResource(R.string.focusmode_apps)) }
        item(key = "apps") {
            ModuleSettingsCard {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.focusmode_apps_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    settings.apps.sorted().forEach { pkg ->
                        ListItem(
                            headlineContent = { Text(appLabel(context, pkg)) },
                            trailingContent = {
                                IconButton(onClick = { update { it.copy(apps = it.apps - pkg) } }) {
                                    Icon(Icons.Rounded.Close, stringResource(R.string.focusmode_remove))
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                    TextButton(onClick = { pickingApp = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.focusmode_add_app))
                    }
                }
            }
        }

        item(key = "indicator") {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.focusmode_indicator),
                    summary = stringResource(R.string.focusmode_indicator_summary),
                    checked = settings.showIndicator,
                    onChange = { on -> update { it.copy(showIndicator = on) } },
                )
            }
        }
    }

    if (pickingApp) {
        ModalBottomSheet(onDismissRequest = { pickingApp = false }) {
            AppList { app ->
                update { it.copy(apps = it.apps + app.packageName) }
                pickingApp = false
            }
        }
    }
    timeEdit?.let { (i, start) ->
        val slot = settings.slots.getOrNull(i) ?: return@let
        val initial = if (start) slot.startMinute else slot.endMinute
        val picker = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { timeEdit = null },
            confirmButton = {
                TextButton(onClick = {
                    val m = picker.hour * 60 + picker.minute
                    updateSlot(i) { if (start) it.copy(startMinute = m) else it.copy(endMinute = m) }
                    timeEdit = null
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { timeEdit = null }) { Text(stringResource(R.string.cancel)) } },
            text = { TimePicker(state = picker) },
        )
    }
}

@Composable
private fun StatusCard(state: FocusState, onToggle: () -> Unit) {
    val container by animateColorAsState(
        if (state.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "focusContainer",
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Bedtime, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(if (state.active) R.string.focusmode_on else R.string.focusmode_off),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            Spacer(Modifier.height(6.dp))
            val reason = when {
                state.active -> state.source?.labelRes()
                state.automatic != null -> R.string.focusmode_paused
                else -> null
            }
            Text(
                stringResource(reason ?: R.string.focusmode_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onToggle) {
                Text(stringResource(if (state.active) R.string.focusmode_turn_off else R.string.focusmode_turn_on))
            }
        }
    }
}

private fun FocusSource.labelRes(): Int = when (this) {
    FocusSource.MANUAL -> R.string.focusmode_by_manual
    FocusSource.RULE -> R.string.focusmode_by_rule
    FocusSource.APP -> R.string.focusmode_by_app
    FocusSource.SCHEDULE -> R.string.focusmode_by_schedule
}

private fun FocusModuleMode.labelRes(): Int = when (this) {
    FocusModuleMode.AUTO -> R.string.focusmode_module_auto
    FocusModuleMode.ALWAYS -> R.string.focusmode_module_always
    FocusModuleMode.NEVER -> R.string.focusmode_module_never
}

private fun thresholdLabel(value: Int): Int = when {
    value >= Priority.CALL.value -> R.string.focusmode_threshold_call
    value >= Priority.ALARM.value -> R.string.focusmode_threshold_alarm
    value >= Priority.ALERT.value -> R.string.focusmode_threshold_alert
    else -> R.string.focusmode_threshold_live
}

private fun appLabel(context: Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)

private fun hhmm(m: Int) = "%d:%02d".format(m / 60, m % 60)
