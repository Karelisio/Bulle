package com.karelisio.bulle.modules.alerts.sources

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertRules
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.ChargeKind
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.broadcasts
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.BatteryGlyph
import com.karelisio.bulle.modules.alerts.ui.BoltGlyph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private val ChargeGreen = Color(0xFF5BE38A)
private val LowRed = Color(0xFFFF5D5D)

/**
 * Batterie faible (franchissement d'un seuil vers le bas) et branchement du chargeur.
 * ACTION_BATTERY_CHANGED est « collant » : la première valeur reçue sert seulement de référence.
 */
object BatterySource : AlertSource {
    override val id = "battery"

    override fun isEnabled(settings: AlertSettings) = settings.lowBattery || settings.charging

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = flow<IslandContent?> {
        var prevLevel: Int? = null
        var prevPlugged: Int? = null
        var charge: ChargingContent? = null
        broadcasts(env.context.applicationContext, IntentFilter(Intent.ACTION_BATTERY_CHANGED)).collect { i ->
            val pct = AlertRules.batteryPercent(
                i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                i.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
            ) ?: return@collect
            val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val kind = AlertRules.chargeKind(
                wireless = plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS,
                maxCurrentMicroA = i.getIntExtra("max_charging_current", 0),
                maxVoltageMicroV = i.getIntExtra("max_charging_voltage", 0),
            )
            val s = env.settings.value.alerts
            val now = alertNow()
            val shown = charge
            when {
                AlertRules.justPlugged(prevPlugged, plugged) && s.charging -> {
                    charge = ChargingContent(pct, kind, now).also { emit(it) }
                }
                // Mise à jour pendant l'affichage : le courant max n'est souvent connu qu'après quelques instants.
                shown != null && plugged != 0 && now < (shown.expiresAt ?: 0) -> {
                    val updated = shown.copy(percent = pct, kind = if (shown.kind == ChargeKind.NORMAL) kind else shown.kind)
                    if (updated != shown) { charge = updated; emit(updated) }
                }
                plugged == 0 && shown != null -> { charge = null; emit(null) }
            }
            val crossed = AlertRules.crossedLowThreshold(prevLevel, pct, s.lowBatteryLevels, charging = plugged != 0)
            if (crossed != null && s.lowBattery) emit(LowBatteryContent(pct, now))
            prevLevel = pct
            prevPlugged = plugged
        }
    }.latestAlive()
}

@Composable
private fun percentText(p: Int) = stringResource(R.string.alerts_percent, p)

/** Chargeur branché : batterie qui se remplit, éclair, type de charge. */
data class ChargingContent(
    val percent: Int,
    val kind: ChargeKind,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:charging:$postedAt"
    override val alertId = "charging"
    override val accent = ChargeGreen
    override val compactSlotWidth = 58.dp

    @Composable
    override fun Leading() = BatteryGlyph(percent, ChargeGreen, charging = true, animateFill = true)

    @Composable
    override fun Trailing() {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (kind != ChargeKind.NORMAL) BoltGlyph(ChargeGreen, size = 13.dp)
            AlertLabel(percentText(percent), color = ChargeGreen)
        }
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val title = when (kind) {
            ChargeKind.FAST -> R.string.alerts_charging_fast
            ChargeKind.WIRELESS -> R.string.alerts_charging_wireless
            ChargeKind.NORMAL -> R.string.alerts_charging
        }
        AlertExpanded(
            accent = ChargeGreen,
            title = stringResource(title),
            subtitle = percentText(percent),
            glyph = { BatteryGlyph(percent, ChargeGreen, charging = true, animateFill = true, width = 30.dp) },
        )
    }
}

/** Seuil de batterie faible franchi : batterie rouge qui clignote doucement. */
data class LowBatteryContent(
    val percent: Int,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:lowbattery:$postedAt"
    override val alertId = "lowBattery"
    override val accent = LowRed
    override val compactSlotWidth = 52.dp
    override val baseTimeoutMs: Long? = 5_000L

    @Composable
    override fun Leading() = BatteryGlyph(percent, LowRed, blink = true)

    @Composable
    override fun Trailing() = AlertLabel(percentText(percent), color = LowRed)

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(
            accent = LowRed,
            title = stringResource(R.string.alerts_low_battery),
            subtitle = stringResource(R.string.alerts_low_battery_left, percent),
            glyph = { BatteryGlyph(percent, LowRed, blink = true, width = 30.dp) },
            actions = {
                FilledTonalButton(onClick = {
                    runCatching {
                        host.context.startActivity(
                            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    host.dismiss(key)
                }) { Text(stringResource(R.string.alerts_battery_saver)) }
            },
        )
    }
}
