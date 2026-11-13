package com.karelisio.bulle.modules.battery.ui

import android.Manifest
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.alerts.broadcasts
import com.karelisio.bulle.modules.battery.AccessoryRow
import com.karelisio.bulle.modules.battery.BatteryDetails
import com.karelisio.bulle.modules.battery.BatteryHistoryStore
import com.karelisio.bulle.modules.battery.BatteryModule
import com.karelisio.bulle.modules.battery.BatteryReader
import com.karelisio.bulle.modules.battery.BatterySettings
import com.karelisio.bulle.modules.battery.BatterySettingsBinding
import com.karelisio.bulle.modules.battery.BluetoothBatteries
import com.karelisio.bulle.modules.battery.BtAccessory
import com.karelisio.bulle.modules.battery.LiveBattery
import com.karelisio.bulle.modules.focus.ui.ModuleSectionHeader
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsCard
import com.karelisio.bulle.modules.focus.ui.ModuleSettingsScaffold
import com.karelisio.bulle.modules.focus.ui.ModuleSwitchRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch

/**
 * Réglages du module « Batterie détaillée », avec aperçu en direct. L'accès Bluetooth
 * (batteries des accessoires) n'est demandé que par le bouton explicite de cet écran.
 */
@Composable
fun BatterySettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val settings by remember(context) { SettingsRepository.flow(context) }.collectAsStateWithLifecycle(BulleSettings())
    val battery = BatterySettingsBinding.read(settings)
    val enabled = BatteryModule.isEnabled(settings)

    val info by remember(context) {
        broadcasts(context.applicationContext, IntentFilter(Intent.ACTION_BATTERY_CHANGED)).mapNotNull(BatteryReader::parse)
    }.collectAsStateWithLifecycle(remember(context) { BatteryReader.current(context) })
    val history by remember(context) { BatteryHistoryStore.flow(context) }.collectAsStateWithLifecycle(emptyList())
    var live by remember { mutableStateOf<LiveBattery?>(null) }
    val charging = info?.charging == true
    // Courant lu toutes les 2 s tant que l'écran est affiché.
    LaunchedEffect(charging) {
        while (true) {
            live = BatteryReader.live(context, charging)
            delay(2_000)
        }
    }

    var granted by remember { mutableStateOf(BluetoothBatteries.granted(context)) }
    LifecycleResumeEffect(Unit) {
        granted = BluetoothBatteries.granted(context)
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val accessories by remember(context, granted) {
        if (granted) BluetoothBatteries.accessories(context) else flowOf(emptyList<BtAccessory>())
    }.collectAsStateWithLifecycle(emptyList())

    fun setEnabled(on: Boolean) {
        scope.launch { SettingsRepository.update(context) { it.copy(modules = it.modules + (BatteryModule.ID to on)) } }
    }

    fun update(transform: (BatterySettings) -> BatterySettings) {
        scope.launch {
            SettingsRepository.update(context) { BatterySettingsBinding.write(it, transform(BatterySettingsBinding.read(it))) }
        }
    }

    ModuleSettingsScaffold(title = stringResource(R.string.battery_settings_title), onBack = onBack) {
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.battery_settings_enable),
                    summary = stringResource(R.string.battery_settings_enable_summary),
                    checked = enabled,
                ) { setEnabled(it) }
            }
        }
        info?.let { current ->
            item {
                ModuleSettingsCard {
                    BatteryDetails(current, live, history, emptyList(), Modifier.padding(16.dp), onDark = false)
                }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.battery_settings_show_header)) }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.battery_settings_charging),
                    summary = stringResource(R.string.battery_settings_charging_summary),
                    checked = battery.showWhileCharging,
                    enabled = enabled,
                ) { on -> update { it.copy(showWhileCharging = on) } }
                ModuleSwitchRow(
                    title = stringResource(R.string.battery_settings_always),
                    summary = stringResource(R.string.battery_settings_always_summary),
                    checked = battery.showAlways,
                    enabled = enabled,
                ) { on -> update { it.copy(showAlways = on) } }
            }
        }
        item { ModuleSectionHeader(stringResource(R.string.battery_settings_bt_header)) }
        item {
            ModuleSettingsCard {
                ModuleSwitchRow(
                    title = stringResource(R.string.battery_settings_accessories),
                    summary = stringResource(R.string.battery_settings_accessories_summary),
                    checked = battery.accessories,
                    enabled = enabled,
                ) { on -> update { it.copy(accessories = on) } }
                if (!granted) {
                    ListItem(
                        leadingContent = { Icon(Icons.Rounded.Bluetooth, contentDescription = null) },
                        headlineContent = { Text(stringResource(R.string.battery_settings_bt_missing)) },
                        supportingContent = { Text(stringResource(R.string.battery_settings_bt_explain)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    FilledTonalButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    ) { Text(stringResource(R.string.battery_settings_bt_grant)) }
                } else {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                        if (accessories.isEmpty()) {
                            Text(
                                stringResource(R.string.battery_settings_bt_none),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        accessories.forEach { AccessoryRow(it, MaterialTheme.colorScheme.onSurface) }
                    }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.battery_settings_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}
