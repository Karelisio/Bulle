package com.karelisio.bulle.modules.volume.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.karelisio.bulle.modules.volume.VolumeModule
import com.karelisio.bulle.modules.volume.VolumePrefs
import com.karelisio.bulle.modules.volume.VolumeStreamChoice
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToLong

/** Réglages du volume sur l'île : activation, flux réglé par les touches, écran verrouillé, durée. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VolumeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    remember(context) { VolumePrefs.ensureLoaded(context) }
    val prefs by VolumePrefs.data.collectAsStateWithLifecycle()
    val bulle by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val enabled = VolumeModule.isEnabled(bulle)

    ModuleSettingsScaffold(stringResource(R.string.volume_settings_title), onBack) {
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.volume_settings_enable),
                    summary = stringResource(R.string.volume_settings_enable_summary),
                    checked = enabled,
                ) { on ->
                    scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (VolumeModule.ID to on)) } }
                }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.volume_settings_stream_header)) }
        item {
            ModuleSettingsCard {
                FlowRow(
                    Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    VolumeStreamChoice.entries.forEach { choice ->
                        FilterChip(
                            selected = prefs.stream == choice,
                            onClick = { VolumePrefs.update(context) { it.copy(stream = choice) } },
                            label = { Text(stringResource(choiceLabel(choice))) },
                            enabled = enabled,
                        )
                    }
                }
                Text(
                    stringResource(R.string.volume_settings_stream_auto_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.volume_settings_behavior_header)) }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.volume_settings_locked),
                    summary = stringResource(R.string.volume_settings_locked_summary),
                    checked = prefs.interceptWhenLocked,
                    enabled = enabled,
                ) { on -> VolumePrefs.update(context) { it.copy(interceptWhenLocked = on) } }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.volume_settings_timeout)) },
                    supportingContent = {
                        Slider(
                            value = prefs.timeoutMs / 1000f,
                            onValueChange = { v -> VolumePrefs.update(context) { it.copy(timeoutMs = (v * 2).roundToLong() * 500) } },
                            valueRange = 1.5f..6f,
                            steps = 8,
                            enabled = enabled,
                        )
                    },
                    trailingContent = { Text(String.format(Locale.getDefault(), "%.1f s", prefs.timeoutMs / 1000f)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
        item {
            Text(
                stringResource(R.string.volume_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun choiceLabel(choice: VolumeStreamChoice): Int = when (choice) {
    VolumeStreamChoice.AUTO -> R.string.volume_stream_auto
    VolumeStreamChoice.MEDIA -> R.string.volume_stream_media
    VolumeStreamChoice.RING -> R.string.volume_stream_ring
    VolumeStreamChoice.NOTIFICATION -> R.string.volume_stream_notification
    VolumeStreamChoice.ALARM -> R.string.volume_stream_alarm
}
