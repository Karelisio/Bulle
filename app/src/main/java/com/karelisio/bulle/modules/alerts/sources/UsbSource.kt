package com.karelisio.bulle.modules.alerts.sources

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.UsbOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.alerts.AlertContent
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.alerts.AlertSource
import com.karelisio.bulle.modules.alerts.SignalRules
import com.karelisio.bulle.modules.alerts.AlertRules
import com.karelisio.bulle.modules.alerts.UsbChargeLink
import com.karelisio.bulle.modules.alerts.ui.BatteryGlyph
import com.karelisio.bulle.modules.alerts.UsbMode
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.PopIcon
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private val UsbTeal = Color(0xFF5ED6E8)

/**
 * USB : câble de DONNÉES vers un ordinateur (diffusion collante « android.hardware.usb.action.USB_STATE »,
 * extra « connected », faux avec un simple chargeur : la charge seule reste l'alerte « Branchement »)
 * et périphériques USB branchés en OTG (UsbManager.ACTION_USB_DEVICE_ATTACHED / DETACHED).
 * Aucune permission nécessaire.
 */
object UsbSource : AlertSource {
    override val id = "usb"
    private const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"

    override fun isEnabled(settings: AlertSettings) = settings.usb

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = callbackFlow<IslandContent?> {
        val ctx = env.context.applicationContext
        var connected: Boolean? = null
        var current: UsbContent? = null
        // Dernier état batterie connu (diffusion collante) : % affiché si l'ordinateur charge le téléphone.
        var plugged = 0
        var percent: Int? = null
        fun chargePercent(): Int? =
            if (env.settings.value.alerts.charging) SignalRules.usbChargePercent(plugged, percent) else null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_BATTERY_CHANGED -> {
                        plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                        percent = AlertRules.batteryPercent(
                            intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
                            intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1),
                        )
                        // L'alerte combinée est déjà affichée : on y ajoute / met à jour le pourcentage.
                        val shown = current
                        val pct = chargePercent()
                        if (shown != null && shown.event == UsbContent.Event.CABLE_ON && shown.chargePercent != pct &&
                            alertNow() < (shown.expiresAt ?: 0)
                        ) {
                            current = shown.copy(chargePercent = pct).also { trySend(it) }
                        }
                    }
                    ACTION_USB_STATE -> {
                        val now = intent.getBooleanExtra("connected", false)
                        UsbChargeLink.dataConnected = now
                        val mode = SignalRules.usbMode { intent.getBooleanExtra(it, false) }
                        // L'état rejoué à l'inscription n'est qu'une référence.
                        val event = if (isInitialStickyBroadcast) null else SignalRules.usbTransition(connected ?: false, now)
                        connected = now
                        val shown = current
                        when {
                            event == true -> current = UsbContent(UsbContent.Event.CABLE_ON, mode, null, alertNow(), chargePercent()).also { trySend(it) }
                            event == false -> current = UsbContent(UsbContent.Event.CABLE_OFF, null, null, alertNow()).also { trySend(it) }
                            // Le mode (fichiers, photos…) se règle souvent juste après le branchement : même alerte, mise à jour.
                            now && shown != null && shown.event == UsbContent.Event.CABLE_ON && shown.mode != mode &&
                                alertNow() < (shown.expiresAt ?: 0) -> current = shown.copy(mode = mode).also { trySend(it) }
                        }
                    }
                    UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                        val device = deviceOf(intent)
                        val name = device?.let { d -> runCatching { d.productName }.getOrNull()?.takeIf { it.isNotBlank() } }
                        val attached = intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED
                        current = UsbContent(
                            if (attached) UsbContent.Event.DEVICE_ON else UsbContent.Event.DEVICE_OFF, null, name, alertNow(),
                        ).also { trySend(it) }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_STATE)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose {
            UsbChargeLink.dataConnected = false
            runCatching { ctx.unregisterReceiver(receiver) }
        }
    }.latestAlive()

    private fun deviceOf(intent: Intent): UsbDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
        }
}

/** USB branché / débranché : prise qui rebondit, usage du câble ou nom du périphérique. */
data class UsbContent(
    val event: Event,
    val mode: UsbMode?,
    val deviceName: String?,
    override val postedAt: Long,
    /** Pourcentage de batterie quand l'ordinateur charge le téléphone (alerte combinée USB + charge). */
    val chargePercent: Int? = null,
) : AlertContent() {
    enum class Event { CABLE_ON, CABLE_OFF, DEVICE_ON, DEVICE_OFF }

    private val on get() = event == Event.CABLE_ON || event == Event.DEVICE_ON

    override val key = "alerts:usb:$postedAt"
    override val alertId = "usb"
    override val compactSlotWidth = 84.dp
    override val accent: Color = if (on) UsbTeal else Color(0xFFB8C0CC)

    @Composable
    private fun Glyph(size: Int) = PopIcon(if (on) Icons.Rounded.Usb else Icons.Rounded.UsbOff, accent, size = size.dp)

    /** Batterie qui se remplit (animation de charge) de l'alerte combinée. */
    @Composable
    private fun ChargeGlyph(percent: Int, width: Int) =
        BatteryGlyph(percent, accent, charging = true, animateFill = true, width = width.dp)

    @Composable
    private fun modeLabel(): String? = when (mode) {
        UsbMode.FILES -> stringResource(R.string.alerts_usb_files)
        UsbMode.PHOTOS -> stringResource(R.string.alerts_usb_photos)
        UsbMode.TETHERING -> stringResource(R.string.alerts_usb_tethering)
        UsbMode.MIDI -> stringResource(R.string.alerts_usb_midi)
        UsbMode.CHARGE_ONLY -> stringResource(R.string.alerts_usb_charge_only)
        null -> null
    }

    @Composable
    override fun Leading() = Glyph(20)

    @Composable
    override fun Trailing() {
        val pct = chargePercent
        if (pct != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ChargeGlyph(pct, 18)
                AlertLabel(stringResource(R.string.alerts_percent, pct), color = accent)
            }
        } else {
            AlertLabel(stringResource(if (on) R.string.alerts_usb_on_short else R.string.alerts_usb_off_short), color = accent)
        }
    }

    @Composable
    override fun Expanded(host: IslandHost) {
        val title = when (event) {
            Event.CABLE_ON -> R.string.alerts_usb_cable_on
            Event.CABLE_OFF -> R.string.alerts_usb_cable_off
            Event.DEVICE_ON -> R.string.alerts_usb_device_on
            Event.DEVICE_OFF -> R.string.alerts_usb_device_off
        }
        val pct = chargePercent
        val detail = deviceName ?: modeLabel()
        val subtitle = if (pct != null) {
            listOfNotNull(stringResource(R.string.alerts_percent, pct), detail).joinToString(" · ")
        } else {
            detail
        }
        AlertExpanded(
            accent = accent,
            title = stringResource(title),
            subtitle = subtitle,
            glyph = {
                if (pct != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Glyph(22)
                        ChargeGlyph(pct, 26)
                    }
                } else {
                    Glyph(26)
                }
            },
        )
    }
}
