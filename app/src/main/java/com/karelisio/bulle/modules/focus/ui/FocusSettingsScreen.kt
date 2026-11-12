package com.karelisio.bulle.modules.focus.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.focus.Focus
import com.karelisio.bulle.modules.focus.FocusModule
import com.karelisio.bulle.modules.focus.FocusSettings
import kotlinx.coroutines.launch

/** Réglages du minuteur / Pomodoro : activation, durées, enchaînement, vibration et son. */
@Composable
fun FocusSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by remember(context) { Focus.loaded(context) }.collectAsStateWithLifecycle()
    val bulle by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val enabled = FocusModule.isEnabled(bulle)
    val s = data.settings

    fun update(transform: (FocusSettings) -> FocusSettings) = Focus.updateSettings(context, transform)

    ModuleSettingsScaffold(stringResource(R.string.focus_settings_title), onBack) {
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.focus_settings_enable),
                    summary = stringResource(R.string.focus_settings_enable_summary),
                    checked = enabled,
                ) { on ->
                    scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (FocusModule.ID to on)) } }
                }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.focus_settings_pomodoro_header)) }
        item {
            ModuleSettingsCard {
                StepperRow(stringResource(R.string.focus_settings_work), stringResource(R.string.focus_settings_minutes_value, s.workMinutes),
                    onMinus = { update { it.copy(workMinutes = it.workMinutes - 5) } },
                    onPlus = { update { it.copy(workMinutes = it.workMinutes + 5) } })
                StepperRow(stringResource(R.string.focus_settings_short_break), stringResource(R.string.focus_settings_minutes_value, s.shortBreakMinutes),
                    onMinus = { update { it.copy(shortBreakMinutes = it.shortBreakMinutes - 1) } },
                    onPlus = { update { it.copy(shortBreakMinutes = it.shortBreakMinutes + 1) } })
                StepperRow(stringResource(R.string.focus_settings_long_break), stringResource(R.string.focus_settings_minutes_value, s.longBreakMinutes),
                    onMinus = { update { it.copy(longBreakMinutes = it.longBreakMinutes - 5) } },
                    onPlus = { update { it.copy(longBreakMinutes = it.longBreakMinutes + 5) } })
                StepperRow(stringResource(R.string.focus_settings_cycles), s.cycles.toString(),
                    onMinus = { update { it.copy(cycles = it.cycles - 1) } },
                    onPlus = { update { it.copy(cycles = it.cycles + 1) } })
                ModuleSwitchRow(
                    title = stringResource(R.string.focus_settings_auto),
                    summary = stringResource(R.string.focus_settings_auto_summary),
                    checked = s.autoAdvance,
                ) { on -> update { it.copy(autoAdvance = on) } }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.focus_settings_alert_header)) }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.focus_settings_vibrate),
                    summary = stringResource(if (bulle.motion.haptics) R.string.focus_settings_vibrate_summary else R.string.focus_settings_vibrate_off),
                    checked = s.vibrate,
                    enabled = bulle.motion.haptics,
                ) { on -> update { it.copy(vibrate = on) } }
                ModuleSwitchRow(
                    title = stringResource(R.string.focus_settings_sound),
                    summary = stringResource(R.string.focus_settings_sound_summary),
                    checked = s.sound,
                ) { on -> update { it.copy(sound = on) } }
            }
        }
        item {
            Text(
                stringResource(R.string.focus_settings_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
