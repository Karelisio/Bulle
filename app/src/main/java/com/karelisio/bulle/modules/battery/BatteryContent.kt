package com.karelisio.bulle.modules.battery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.modules.alerts.sources.BtKind
import com.karelisio.bulle.modules.alerts.ui.BatteryGlyph
import com.karelisio.bulle.modules.live.ui.CompactDigits
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

/** Priorité « ambiante » : sous la musique, au-dessus de la météo au repos. */
val AmbientBatteryPriority = Priority(65)

val ChargeGreen = Color(0xFF5BE38A)
val LowRed = Color(0xFFFF5D5D)

/** Couleur du niveau : vert en charge, rouge sous 20 %, blanc sinon. */
fun levelColor(level: Int, plugged: Boolean, neutral: Color = Color.White): Color = when {
    plugged -> ChargeGreen
    level <= 20 -> LowRed
    else -> neutral
}

/** Batterie en charge (ou en permanence, en option) : niveau en compact, détails en vue étendue. */
data class BatteryDetailContent(
    val info: BatteryInfo,
    override val compactSubtitle: String?,
    val showAccessories: Boolean,
    override val postedAt: Long,
) : IslandContent() {
    override val key = "battery:detail"
    override val moduleId = BatteryModule.ID
    override val priority = AmbientBatteryPriority
    override val accent: Color = levelColor(info.level, info.plugged)
    override val compactSlotWidth: Dp = 52.dp

    @Composable
    override fun Leading() = BatteryGlyph(info.level, accent, charging = info.plugged, width = 26.dp, followTint = false)

    @Composable
    override fun Trailing() = Text(
        stringResource(R.string.battery_percent, info.level),
        style = MaterialTheme.typography.labelLarge.merge(CompactDigits),
        color = accent,
        maxLines = 1,
        softWrap = false,
    )

    @Composable
    override fun Expanded(host: IslandHost) {
        val context = LocalContext.current
        // Courant et temps de charge système : lus toutes les 2 s, seulement vue ouverte.
        var live by remember { mutableStateOf<LiveBattery?>(null) }
        LaunchedEffect(info.charging) {
            while (true) {
                live = BatteryReader.live(context, info.charging)
                delay(2_000)
            }
        }
        val history by remember(context) { BatteryHistoryStore.flow(context) }.collectAsStateWithLifecycle(emptyList())
        val accessories by remember(context, showAccessories) {
            if (showAccessories) BluetoothBatteries.accessories(context) else flowOf(emptyList())
        }.collectAsStateWithLifecycle(emptyList())
        BatteryDetails(info, live, history, accessories)
    }
}

/** Corps de la vue détaillée (réutilisé par l'aperçu des réglages). */
@Composable
fun BatteryDetails(
    info: BatteryInfo,
    live: LiveBattery?,
    history: List<LevelSample>,
    accessories: List<BtAccessory>,
    modifier: Modifier = Modifier,
    onDark: Boolean = true,
) {
    val context = LocalContext.current
    val primary = if (onDark) Color.White else MaterialTheme.colorScheme.onSurface
    val secondary = primary.copy(alpha = 0.65f)
    val color = levelColor(info.level, info.plugged, primary)
    val now = System.currentTimeMillis()
    val locale = Locale.getDefault()
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BatteryGlyph(info.level, color, charging = info.plugged, width = 44.dp, followTint = false)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.battery_module_title), style = MaterialTheme.typography.labelMedium, color = secondary)
                Text(
                    statusLine(info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                stringResource(R.string.battery_percent, info.level),
                style = MaterialTheme.typography.headlineMedium.merge(CompactDigits),
                color = color,
            )
        }
        Spacer(Modifier.height(12.dp))
        val dash = stringResource(R.string.battery_unknown)
        val timeCell = if (info.plugged) {
            val ms = BatteryMath.chargeRemainingMs(live?.chargeTimeMs, history, info.level, now)
            stringResource(R.string.battery_full_in) to (ms?.let { if (it == 0L) stringResource(R.string.battery_status_full) else formatDuration(context, it) } ?: stringResource(R.string.battery_estimating))
        } else {
            val ms = BatteryMath.dischargeRemainingMs(history, info.level, now)
            stringResource(R.string.battery_time_left) to (ms?.let { formatDuration(context, it) } ?: stringResource(R.string.battery_estimating))
        }
        val power = if (info.plugged) BatteryMath.powerWatts(live?.currentMicroA, info.voltageMv) else null
        val cells = listOf(
            timeCell,
            stringResource(R.string.battery_temperature) to (info.temperatureC?.let { String.format(locale, "%.1f °C", it) } ?: dash),
            stringResource(R.string.battery_voltage) to (info.voltageMv?.let { String.format(locale, "%.2f V", it / 1000.0) } ?: dash),
            stringResource(R.string.battery_health) to stringResource(healthRes(info.health)),
            stringResource(R.string.battery_current) to (live?.currentMicroA?.let { String.format(locale, "%+d mA", it / 1000) } ?: dash),
            stringResource(R.string.battery_power) to (power?.let { String.format(locale, "%.1f W", it) } ?: dash),
        )
        cells.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (label, value) -> Cell(label, value, primary, secondary, Modifier.weight(1f)) }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (accessories.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.battery_accessories), style = MaterialTheme.typography.labelMedium, color = secondary)
            Spacer(Modifier.height(4.dp))
            accessories.forEach { AccessoryRow(it, primary) }
        }
    }
}

@Composable
private fun Cell(label: String, value: String, primary: Color, secondary: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = secondary, maxLines = 1)
        Text(value, style = MaterialTheme.typography.titleSmall.merge(CompactDigits), color = primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AccessoryRow(device: BtAccessory, primary: Color) {
    Row(Modifier.fillMaxWidth().height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(accessoryIcon(device.kind), contentDescription = null, tint = primary.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            device.name ?: stringResource(R.string.battery_accessory_unnamed),
            style = MaterialTheme.typography.bodyMedium,
            color = primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val level = device.level
        if (level != null) {
            Text(stringResource(R.string.battery_percent, level), style = MaterialTheme.typography.labelLarge.merge(CompactDigits), color = primary)
            Spacer(Modifier.width(6.dp))
            BatteryGlyph(level, levelColor(level, plugged = false, neutral = primary), width = 22.dp, followTint = false)
        } else {
            Text(stringResource(R.string.battery_unknown), style = MaterialTheme.typography.labelLarge, color = primary.copy(alpha = 0.6f))
        }
    }
}

fun accessoryIcon(kind: BtKind): ImageVector = when (kind) {
    BtKind.HEADPHONES -> Icons.Rounded.Headphones
    BtKind.CAR -> Icons.Rounded.DirectionsCar
    BtKind.WATCH -> Icons.Rounded.Watch
    BtKind.OTHER -> Icons.Rounded.Bluetooth
}

/** « En charge · secteur », « Sur batterie »… */
@Composable
fun statusLine(info: BatteryInfo): String {
    val status = stringResource(
        when (info.status) {
            ChargeStatus.CHARGING -> R.string.battery_status_charging
            ChargeStatus.FULL -> R.string.battery_status_full
            ChargeStatus.NOT_CHARGING -> if (info.plugged) R.string.battery_status_paused else R.string.battery_status_discharging
            ChargeStatus.DISCHARGING -> R.string.battery_status_discharging
        },
    )
    val source = when (info.source) {
        PowerSource.NONE -> null
        PowerSource.AC -> R.string.battery_source_ac
        PowerSource.USB -> R.string.battery_source_usb
        PowerSource.WIRELESS -> R.string.battery_source_wireless
        PowerSource.DOCK -> R.string.battery_source_dock
    }
    return if (source == null) status else "$status · ${stringResource(source)}"
}

fun healthRes(health: BatteryHealth): Int = when (health) {
    BatteryHealth.GOOD -> R.string.battery_health_good
    BatteryHealth.OVERHEAT -> R.string.battery_health_overheat
    BatteryHealth.DEAD -> R.string.battery_health_dead
    BatteryHealth.OVER_VOLTAGE -> R.string.battery_health_over_voltage
    BatteryHealth.COLD -> R.string.battery_health_cold
    BatteryHealth.FAILURE -> R.string.battery_health_failure
    BatteryHealth.UNKNOWN -> R.string.battery_health_unknown
}
