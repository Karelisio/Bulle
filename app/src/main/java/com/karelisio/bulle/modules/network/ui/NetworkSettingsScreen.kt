package com.karelisio.bulle.modules.network.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import com.karelisio.bulle.modules.network.NetworkDetails
import com.karelisio.bulle.modules.network.NetworkModule
import com.karelisio.bulle.modules.network.NetworkMonitor
import com.karelisio.bulle.modules.network.NetworkSettings
import com.karelisio.bulle.modules.network.NetworkSettingsBinding
import com.karelisio.bulle.modules.network.NetworkSnapshot
import com.karelisio.bulle.modules.network.NetworkUsageStore
import com.karelisio.bulle.modules.network.RateFormat
import com.karelisio.bulle.modules.network.RateUnit
import kotlinx.coroutines.launch

/**
 * Réglages du module « Réseau ». L'aperçu en direct n'échantillonne le débit que tant que
 * l'écran est visible (collecte liée au cycle de vie).
 */
@Composable
fun NetworkSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val network = NetworkSettingsBinding.read(settings)
    val enabled = NetworkModule.isEnabled(settings)
    val snapshot by remember(context) { NetworkMonitor.snapshots(context) }.collectAsStateWithLifecycle(NetworkSnapshot())
    val usage by remember(context) { NetworkUsageStore.flow(context) }.collectAsStateWithLifecycle(null)

    fun setEnabled(on: Boolean) {
        scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (NetworkModule.ID to on)) } }
    }

    fun update(transform: (NetworkSettings) -> NetworkSettings) {
        scope.launch {
            SettingsRepository.update(context) { NetworkSettingsBinding.write(it, transform(NetworkSettingsBinding.read(it))) }
        }
    }

    ModuleSettingsScaffold(title = stringResource(R.string.network_settings_title), onBack = onBack) {
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.network_settings_enable),
                    summary = stringResource(R.string.network_settings_enable_summary),
                    checked = enabled,
                ) { setEnabled(it) }
            }
        }
        item {
            ModuleSettingsCard {
                NetworkDetails(snapshot, network.unit, usage, Modifier.padding(16.dp), onDark = false)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        scope.launch { NetworkUsageStore.reset(context) }
                    }) {
                        Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.network_settings_reset))
                    }
                }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.network_settings_show_header)) }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.network_settings_compact),
                    summary = stringResource(R.string.network_settings_compact_summary),
                    checked = network.showCompact,
                    enabled = enabled,
                ) { on -> update { it.copy(showCompact = on) } }
                val units = stringArrayResource(R.array.network_units_bytes).toList()
                ListItem(
                    headlineContent = { Text(stringResource(R.string.network_settings_threshold)) },
                    supportingContent = { Text(stringResource(R.string.network_settings_threshold_summary)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                val choices = NetworkSettings.THRESHOLD_CHOICES
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                    choices.forEachIndexed { i, kb ->
                        SegmentedButton(
                            selected = network.thresholdKBps == kb,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                update { it.copy(thresholdKBps = kb) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                            enabled = enabled && network.showCompact,
                        ) { Text(RateFormat.rate(kb * 1000.0, RateUnit.BYTES, units), maxLines = 1, softWrap = false) }
                    }
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.network_settings_unit)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                val unitChoices = listOf(RateUnit.BYTES to R.string.network_settings_unit_bytes, RateUnit.BITS to R.string.network_settings_unit_bits)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    unitChoices.forEachIndexed { i, (unit, label) ->
                        SegmentedButton(
                            selected = network.unit == unit,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                update { it.copy(unit = unit) }
                            },
                            shape = SegmentedButtonDefaults.itemShape(i, unitChoices.size),
                        ) { Text(stringResource(label), maxLines = 1) }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.network_settings_privacy),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
