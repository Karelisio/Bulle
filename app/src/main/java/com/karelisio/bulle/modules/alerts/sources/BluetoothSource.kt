package com.karelisio.bulle.modules.alerts.sources

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.IntentFilter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertRules
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.broadcasts
import com.karelisio.bulle.modules.alerts.hasPermission
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.BatteryGlyph
import com.karelisio.bulle.modules.alerts.ui.CheckGlyph
import com.karelisio.bulle.modules.alerts.ui.PopIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private val BtBlue = Color(0xFF6AB0FF)

/** Type d'appareil, pour l'icône. */
enum class BtKind { HEADPHONES, CAR, WATCH, OTHER }

/**
 * Appareil Bluetooth audio / portable connecté, avec sa batterie si le système la connaît.
 * Nécessite BLUETOOTH_CONNECT (demandée à l'activation de l'alerte).
 */
object BluetoothSource : AlertSource {
    override val id = "bluetooth"

    /** Diffusion système (masquée dans le SDK) annonçant la batterie d'un appareil connecté. */
    const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    override fun isEnabled(settings: AlertSettings) = settings.bluetooth

    override val requiredPermissions: List<String> get() = listOf(Manifest.permission.BLUETOOTH_CONNECT)

    @SuppressLint("MissingPermission") // Permission vérifiée avant tout abonnement.
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = flow<IslandContent?> {
        val ctx = env.context.applicationContext
        if (!ctx.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) return@flow
        val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED).apply { addAction(ACTION_BATTERY_LEVEL_CHANGED) }
        var shown: BluetoothContent? = null
        broadcasts(ctx, filter).collect { i ->
            val device = IntentCompat.getParcelableExtra(i, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                ?: return@collect
            val now = alertNow()
            when (i.action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val kind = kindOf(device) ?: return@collect
                    val c = BluetoothContent(
                        address = device.address,
                        name = deviceName(ctx, device),
                        kind = kind,
                        battery = reflectedBattery(device),
                        postedAt = now,
                    )
                    shown = c
                    emit(c)
                }
                ACTION_BATTERY_LEVEL_CHANGED -> {
                    val level = AlertRules.bluetoothBattery(i.getIntExtra(EXTRA_BATTERY_LEVEL, -1)) ?: return@collect
                    val cur = shown ?: return@collect
                    if (cur.address == device.address && now < (cur.expiresAt ?: 0) && cur.battery != level) {
                        shown = cur.copy(battery = level).also { emit(it) }
                    }
                }
            }
        }
    }.latestAlive()

    /** Garde les appareils « personnels » (casques, enceintes, voiture, montres), ignore claviers, PC… */
    @SuppressLint("MissingPermission")
    private fun kindOf(device: BluetoothDevice): BtKind? {
        val cls = runCatching { device.bluetoothClass }.getOrNull() ?: return BtKind.OTHER
        return when (cls.majorDeviceClass) {
            BluetoothClass.Device.Major.AUDIO_VIDEO ->
                if (cls.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO) BtKind.CAR else BtKind.HEADPHONES
            BluetoothClass.Device.Major.WEARABLE, BluetoothClass.Device.Major.HEALTH -> BtKind.WATCH
            BluetoothClass.Device.Major.UNCATEGORIZED -> BtKind.OTHER
            else -> null
        }
    }

    @SuppressLint("MissingPermission")
    private fun deviceName(ctx: Context, device: BluetoothDevice): String? = runCatching {
        if (!ctx.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) null else device.alias ?: device.name
    }.getOrNull()

    /** Batterie déjà connue du système (méthode masquée, tolérée) ; null si indisponible. */
    private fun reflectedBattery(device: BluetoothDevice): Int? = runCatching {
        val level = BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int
        AlertRules.bluetoothBattery(level)
    }.getOrNull()
}

/** Appareil Bluetooth connecté : icône qui rebondit, batterie à droite. */
data class BluetoothContent(
    val address: String,
    val name: String?,
    val kind: BtKind,
    val battery: Int?,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:bluetooth:$postedAt"
    override val alertId = "bluetooth"
    override val accent = BtBlue
    override val compactSlotWidth = 60.dp

    private val icon: ImageVector
        get() = when (kind) {
            BtKind.HEADPHONES -> Icons.Rounded.Headphones
            BtKind.CAR -> Icons.Rounded.DirectionsCar
            BtKind.WATCH -> Icons.Rounded.Watch
            BtKind.OTHER -> Icons.Rounded.Bluetooth
        }

    @Composable
    override fun Leading() = PopIcon(icon, BtBlue)

    @Composable
    override fun Trailing() {
        val b = battery
        if (b == null) {
            CheckGlyph(BtBlue, size = 18.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AlertLabel(stringResource(R.string.alerts_percent, b))
                BatteryGlyph(b, if (b <= 20) Color(0xFFFF5D5D) else Color.White, width = 20.dp, followTint = false)
            }
        }
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val connected = stringResource(R.string.alerts_bt_connected)
        AlertExpanded(
            accent = BtBlue,
            title = name ?: stringResource(R.string.alerts_bt_device),
            subtitle = battery?.let { "$connected · ${stringResource(R.string.alerts_bt_battery, it)}" } ?: connected,
            glyph = { PopIcon(icon, BtBlue, size = 26.dp) },
        )
    }
}
