package com.karelisio.bulle.modules.alerts.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.ChargeKind
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.hasPermission
import com.karelisio.bulle.modules.alerts.sources.BluetoothContent
import com.karelisio.bulle.modules.alerts.sources.BtKind
import com.karelisio.bulle.modules.alerts.sources.ChargingContent
import com.karelisio.bulle.modules.alerts.sources.ClipboardContent
import com.karelisio.bulle.modules.alerts.sources.LowBatteryContent
import com.karelisio.bulle.modules.alerts.sources.PackageContent
import com.karelisio.bulle.modules.alerts.sources.ScreenshotSource
import com.karelisio.bulle.modules.alerts.sources.ToggleContent
import com.karelisio.bulle.modules.alerts.sources.ToggleKind
import com.karelisio.bulle.modules.alerts.sources.WifiContent
import com.karelisio.bulle.modules.echo.EchoContent
import com.karelisio.bulle.modules.push.PushModule
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.launch

/** Une ligne de réglage d'alerte. */
private class AlertItem(
    val id: String,
    val icon: ImageVector,
    val tint: Color,
    val titleRes: Int,
    val descRes: Int,
    val get: (AlertSettings) -> Boolean,
    val set: (AlertSettings, Boolean) -> AlertSettings,
    /** Permissions d'exécution demandées à l'activation. */
    val permissions: List<String> = emptyList(),
    /** Si vrai, l'alerte fonctionne aussi sans la permission (en mode réduit). */
    val permissionOptional: Boolean = false,
    /** Explication si la permission est refusée. */
    val deniedRes: Int = R.string.alerts_perm_denied,
    /** Nécessite l'accès aux notifications de Bulle. */
    val needsNotificationAccess: Boolean = false,
    /** Contenu d'aperçu envoyé à l'île (bouton ▶). */
    val preview: ((android.content.Context) -> IslandContent)? = null,
)

private val energy = listOf(
    AlertItem(
        "lowBattery", Icons.Rounded.BatteryAlert, Color(0xFFFF5D5D),
        R.string.alerts_set_low_battery, R.string.alerts_set_low_battery_desc,
        { it.lowBattery }, { s, v -> s.copy(lowBattery = v) },
        preview = { LowBatteryContent(15, alertNow()) },
    ),
    AlertItem(
        "charging", Icons.Rounded.BatteryChargingFull, Color(0xFF5BE38A),
        R.string.alerts_set_charging, R.string.alerts_set_charging_desc,
        { it.charging }, { s, v -> s.copy(charging = v) },
        preview = { ChargingContent(64, ChargeKind.FAST, alertNow()) },
    ),
)

private val connections = listOf(
    AlertItem(
        "bluetooth", Icons.Rounded.Bluetooth, Color(0xFF6AB0FF),
        R.string.alerts_set_bluetooth, R.string.alerts_set_bluetooth_desc,
        { it.bluetooth }, { s, v -> s.copy(bluetooth = v) },
        permissions = listOf(Manifest.permission.BLUETOOTH_CONNECT),
        deniedRes = R.string.alerts_perm_bluetooth_denied,
        preview = { BluetoothContent("00:00", it.getString(R.string.alerts_preview_bt), BtKind.HEADPHONES, 82, alertNow()) },
    ),
    AlertItem(
        "wifi", Icons.Rounded.Wifi, Color(0xFF5ED6E8),
        R.string.alerts_set_wifi, R.string.alerts_set_wifi_desc,
        { it.wifi }, { s, v -> s.copy(wifi = v) },
        permissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        permissionOptional = true,
        deniedRes = R.string.alerts_perm_wifi_denied,
        preview = { WifiContent(it.getString(R.string.alerts_preview_wifi), alertNow()) },
    ),
    AlertItem(
        "airplane", Icons.Rounded.AirplanemodeActive, Color(0xFFFFA24C),
        R.string.alerts_set_airplane, R.string.alerts_set_airplane_desc,
        { it.airplane }, { s, v -> s.copy(airplane = v) },
        preview = { ToggleContent(ToggleKind.AIRPLANE, true, alertNow()) },
    ),
)

private val system = listOf(
    AlertItem(
        "alarm", Icons.Rounded.Alarm, Color(0xFFFFB347),
        R.string.alerts_set_alarm, R.string.alerts_set_alarm_desc,
        { it.alarm }, { s, v -> s.copy(alarm = v) },
        needsNotificationAccess = true,
    ),
    AlertItem(
        "torch", Icons.Rounded.FlashlightOn, Color(0xFFFFE07A),
        R.string.alerts_set_torch, R.string.alerts_set_torch_desc,
        { it.torch }, { s, v -> s.copy(torch = v) },
    ),
    AlertItem(
        "dnd", Icons.Rounded.DoNotDisturbOn, Color(0xFFB39CFF),
        R.string.alerts_set_dnd, R.string.alerts_set_dnd_desc,
        { it.dnd }, { s, v -> s.copy(dnd = v) },
        preview = { ToggleContent(ToggleKind.DND, true, alertNow()) },
    ),
    AlertItem(
        "screenshot", Icons.Rounded.Screenshot, Color(0xFFE8ECF4),
        R.string.alerts_set_screenshot, R.string.alerts_set_screenshot_desc,
        { it.screenshot }, { s, v -> s.copy(screenshot = v) },
        permissions = listOf(ScreenshotSource.permission),
        deniedRes = R.string.alerts_perm_screenshot_denied,
    ),
    AlertItem(
        "packages", Icons.Rounded.InstallMobile, Color(0xFF7BE0A8),
        R.string.alerts_set_packages, R.string.alerts_set_packages_desc,
        { it.packages }, { s, v -> s.copy(packages = v) },
        preview = { PackageContent("com.karelisio.bulle", "Bulle", true, alertNow()) },
    ),
    AlertItem(
        "clipboard", Icons.Rounded.ContentCopy, Color(0xFFC9A7FF),
        R.string.alerts_set_clipboard, R.string.alerts_set_clipboard_desc,
        { it.clipboard }, { s, v -> s.copy(clipboard = v) },
        preview = { ClipboardContent(alertNow()) },
    ),
)

private val echo = listOf(
    AlertItem(
        "echo", Icons.Rounded.Favorite, Color(0xFFFF6FA5),
        R.string.echo_set, R.string.echo_set_desc,
        { it.echoEnabled }, { s, v -> s.copy(echoEnabled = v) },
        needsNotificationAccess = true,
        preview = { EchoContent(it.getString(R.string.echo_preview_title), null, null, alertNow()) },
    ),
)

/** Seuils proposés pour la batterie faible. */
private val thresholdChoices = listOf(5, 10, 15, 20, 30)

/** Écran des réglages des alertes système et d'Écho. */
@Composable
fun AlertsSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by SettingsRepository.flow(context).collectAsStateWithLifecycle(initialValue = BulleSettings())
    val alerts = settings.alerts

    fun update(transform: (AlertSettings) -> AlertSettings) {
        scope.launch { SettingsRepository.update(context) { it.copy(alerts = transform(it.alerts)) } }
    }

    // Les permissions et l'accès aux notifications peuvent changer hors de l'appli : relus au retour.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val notifAccess = remember(refresh) { NotificationRepository.isAccessGranted(context) }

    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var denied by rememberSaveable { mutableStateOf<String?>(null) }
    val all = remember { energy + connections + system + echo }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val item = all.firstOrNull { it.id == pending } ?: return@rememberLauncherForActivityResult
        pending = null
        val granted = context.hasPermission(item.permissions.first())
        when {
            granted -> { denied = null; update { item.set(it, true) } }
            item.permissionOptional -> { denied = item.id; update { item.set(it, true) } }
            else -> { denied = item.id; update { item.set(it, false) } }
        }
        refresh++
    }

    fun toggle(item: AlertItem, on: Boolean) {
        if (!on) {
            if (denied == item.id) denied = null
            update { item.set(it, false) }
            return
        }
        val missing = item.permissions.filterNot { context.hasPermission(it) }
        if (missing.isEmpty()) {
            update { item.set(it, true) }
        } else {
            pending = item.id
            launcher.launch(item.permissions.toTypedArray())
        }
    }

    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.alerts_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.alerts_back))
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = { Text(stringResource(R.string.alerts_master), fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(stringResource(R.string.alerts_master_desc)) },
                        trailingContent = {
                            Switch(checked = alerts.enabled, onCheckedChange = { v -> update { it.copy(enabled = v) } })
                        },
                    )
                }
            }
            if (!notifAccess) {
                item {
                    NoticeCard(stringResource(R.string.alerts_notif_access_needed), stringResource(R.string.alerts_grant)) {
                        runCatching {
                            context.startActivity(
                                Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }
                    }
                }
            }
            val groups = listOf(
                R.string.alerts_group_energy to energy,
                R.string.alerts_group_connections to connections,
                R.string.alerts_group_system to system,
                R.string.echo_group to echo,
            )
            groups.forEach { (titleRes, items) ->
                item(key = "h$titleRes") {
                    Text(
                        stringResource(titleRes),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(items, key = { it.id }) { item ->
                    val checked = item.get(alerts)
                    val permMissing = remember(refresh, checked) {
                        checked && item.permissions.isNotEmpty() && !context.hasPermission(item.permissions.first())
                    }
                    AlertRow(
                        item = item,
                        checked = checked,
                        enabled = alerts.enabled,
                        note = when {
                            denied == item.id -> stringResource(item.deniedRes)
                            permMissing -> stringResource(R.string.alerts_perm_missing)
                            checked && item.needsNotificationAccess && !notifAccess -> stringResource(R.string.alerts_needs_notif_access)
                            else -> null
                        },
                        onNoteClick = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        }.takeIf { denied == item.id || permMissing },
                        onToggle = { toggle(item, it) },
                    ) {
                        if (item.id == "lowBattery" && checked) {
                            ThresholdChips(alerts.lowBatteryLevels) { levels -> update { it.copy(lowBatteryLevels = levels) } }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.alerts_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp, 16.dp),
                )
            }
        }
    }
}

@Composable
private fun AlertRow(
    item: AlertItem,
    checked: Boolean,
    enabled: Boolean,
    note: String?,
    onNoteClick: (() -> Unit)?,
    onToggle: (Boolean) -> Unit,
    below: @Composable () -> Unit,
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = MaterialTheme.shapes.large,
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(item.tint.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(item.icon, null, tint = MaterialTheme.colorScheme.onSurface) }
            },
            headlineContent = { Text(stringResource(item.titleRes)) },
            supportingContent = { Text(stringResource(item.descRes)) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val preview = item.preview
                    if (preview != null && checked && enabled) {
                        IconButton(onClick = { PushModule.push(preview(context)) }) {
                            Icon(Icons.Rounded.PlayArrow, stringResource(R.string.alerts_preview))
                        }
                    }
                    Switch(checked = checked, enabled = enabled, onCheckedChange = onToggle)
                }
            },
        )
        below()
        AnimatedVisibility(note != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    note.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                if (onNoteClick != null) {
                    TextButton(onClick = onNoteClick) { Text(stringResource(R.string.alerts_open_settings)) }
                }
            }
        }
    }
}

@Composable
private fun ThresholdChips(levels: List<Int>, onChange: (List<Int>) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
        Text(stringResource(R.string.alerts_set_thresholds), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            thresholdChoices.forEach { t ->
                val on = t in levels
                FilterChip(
                    selected = on,
                    onClick = { onChange(if (on) levels - t else (levels + t).sortedDescending()) },
                    label = { Text(stringResource(R.string.alerts_percent, t)) },
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(text: String, action: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}
