package com.karelisio.bulle.modules.alerts.sources

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
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
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_USB_STATE -> {
                        val now = intent.getBooleanExtra("connected", false)
                        val mode = SignalRules.usbMode { intent.getBooleanExtra(it, false) }
                        // L'état rejoué à l'inscription n'est qu'une référence.
                        val event = if (isInitialStickyBroadcast) null else SignalRules.usbTransition(connected ?: false, now)
                        connected = now
                        val shown = current
                        when {
                            event == true -> current = UsbContent(UsbContent.Event.CABLE_ON, mode, null, alertNow()).also { trySend(it) }
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
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose { runCatching { ctx.unregisterReceiver(receiver) } }
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
) : AlertContent() {
    enum class Event { CABLE_ON, CABLE_OFF, DEVICE_ON, DEVICE_OFF }

    private val on get() = event == Event.CABLE_ON || event == Event.DEVICE_ON

    override val key = "alerts:usb:$postedAt"
    override val alertId = "usb"
    override val compactSlotWidth = 84.dp
    override val accent: Color = if (on) UsbTeal else Color(0xFFB8C0CC)

    @Composable
    private fun Glyph(size: Int) = PopIcon(if (on) Icons.Rounded.Usb else Icons.Rounded.UsbOff, accent, size = size.dp)

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
    override fun Trailing() = AlertLabel(stringResource(if (on) R.string.alerts_usb_on_short else R.string.alerts_usb_off_short), color = accent)

    @Composable
    override fun Expanded(host: IslandHost) {
        val title = when (event) {
            Event.CABLE_ON -> R.string.alerts_usb_cable_on
            Event.CABLE_OFF -> R.string.alerts_usb_cable_off
            Event.DEVICE_ON -> R.string.alerts_usb_device_on
            Event.DEVICE_OFF -> R.string.alerts_usb_device_off
        }
        AlertExpanded(accent = accent, title = stringResource(title), subtitle = deviceName ?: modeLabel(), glyph = { Glyph(26) })
    }
}
