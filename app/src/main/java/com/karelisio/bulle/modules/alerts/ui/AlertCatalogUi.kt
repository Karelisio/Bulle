package com.karelisio.bulle.modules.alerts.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded._5g
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.modules.alerts.AlertCategory
import com.karelisio.bulle.modules.alerts.ChargeKind
import com.karelisio.bulle.modules.alerts.RingerKind
import com.karelisio.bulle.modules.alerts.UsbMode
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.sources.BluetoothContent
import com.karelisio.bulle.modules.alerts.sources.BtKind
import com.karelisio.bulle.modules.alerts.sources.ChargingContent
import com.karelisio.bulle.modules.alerts.sources.ClipboardContent
import com.karelisio.bulle.modules.alerts.sources.LowBatteryContent
import com.karelisio.bulle.modules.alerts.sources.PackageContent
import com.karelisio.bulle.modules.alerts.sources.PrivacyContent
import com.karelisio.bulle.modules.alerts.sources.PrivacyKind
import com.karelisio.bulle.modules.alerts.sources.RingerContent
import com.karelisio.bulle.modules.alerts.sources.SignalContent
import com.karelisio.bulle.modules.alerts.sources.SignalKind
import com.karelisio.bulle.modules.alerts.sources.ToggleContent
import com.karelisio.bulle.modules.alerts.sources.ToggleKind
import com.karelisio.bulle.modules.alerts.sources.TorchContent
import com.karelisio.bulle.modules.alerts.sources.UsbContent
import com.karelisio.bulle.modules.alerts.sources.VolumeContent
import com.karelisio.bulle.modules.alerts.sources.WifiContent
import com.karelisio.bulle.modules.echo.EchoContent

/** Présentation d'une alerte dans les réglages (le comportement est dans AlertCatalog). */
class AlertUi(
    val id: String,
    val icon: ImageVector,
    val tint: Color,
    val titleRes: Int,
    val descRes: Int,
    /** Explication si la permission est refusée. */
    val deniedRes: Int = R.string.alerts_perm_denied,
    /** Exemple rendu dans la feuille de personnalisation (null = exemple générique). */
    val sample: ((Context) -> IslandContent)? = null,
    /** L'exemple peut être envoyé à l'île (bouton ▶) : jamais pour un contenu persistant. */
    val pushable: Boolean = sample != null,
)

/** Titre et icône d'une catégorie. */
class CategoryUi(val titleRes: Int, val icon: ImageVector)

fun AlertCategory.ui(): CategoryUi = when (this) {
    AlertCategory.ENERGY -> CategoryUi(R.string.alerts_cat_energy, Icons.Rounded.BatteryChargingFull)
    AlertCategory.NETWORK -> CategoryUi(R.string.alerts_cat_network, Icons.Rounded.SignalCellularAlt)
    AlertCategory.DEVICES -> CategoryUi(R.string.alerts_cat_devices, Icons.Rounded.BluetoothConnected)
    AlertCategory.SOUND -> CategoryUi(R.string.alerts_cat_sound, Icons.AutoMirrored.Rounded.VolumeUp)
    AlertCategory.PRIVACY -> CategoryUi(R.string.alerts_cat_privacy, Icons.Rounded.PrivacyTip)
    AlertCategory.APPS -> CategoryUi(R.string.alerts_cat_apps, Icons.Rounded.Apps)
    AlertCategory.ECHO -> CategoryUi(R.string.alerts_cat_echo, Icons.Rounded.Favorite)
}

object AlertUis {
    val all: List<AlertUi> = listOf(
        AlertUi(
            "lowBattery", Icons.Rounded.BatteryAlert, Color(0xFFFF5D5D),
            R.string.alerts_set_low_battery, R.string.alerts_set_low_battery_desc,
            sample = { LowBatteryContent(15, alertNow()) },
        ),
        AlertUi(
            "charging", Icons.Rounded.BatteryChargingFull, Color(0xFF5BE38A),
            R.string.alerts_set_charging, R.string.alerts_set_charging_desc,
            sample = { ChargingContent(64, ChargeKind.FAST, alertNow()) },
        ),
        AlertUi(
            "bluetooth", Icons.Rounded.Bluetooth, Color(0xFF6AB0FF),
            R.string.alerts_set_bluetooth, R.string.alerts_set_bluetooth_desc,
            deniedRes = R.string.alerts_perm_bluetooth_denied,
            sample = { BluetoothContent("00:00", it.getString(R.string.alerts_preview_bt), BtKind.HEADPHONES, 82, alertNow()) },
        ),
        AlertUi(
            "wifi", Icons.Rounded.Wifi, Color(0xFF5ED6E8),
            R.string.alerts_set_wifi, R.string.alerts_set_wifi_desc,
            deniedRes = R.string.alerts_perm_wifi_denied,
            sample = { WifiContent(it.getString(R.string.alerts_preview_wifi), alertNow()) },
        ),
        AlertUi(
            "cellular", Icons.Rounded.SignalCellularAlt, Color(0xFF5BE38A),
            R.string.alerts_set_cellular, R.string.alerts_set_cellular_desc,
            sample = { SignalContent(SignalKind.CELL_BACK, alertNow()) },
        ),
        AlertUi(
            "fiveG", Icons.Rounded._5g, Color(0xFF6AB0FF),
            R.string.alerts_set_five_g, R.string.alerts_set_five_g_desc,
            deniedRes = R.string.alerts_perm_five_g_denied,
            sample = { SignalContent(SignalKind.FIVE_G, alertNow()) },
        ),
        AlertUi(
            "vpn", Icons.Rounded.VpnLock, Color(0xFF7BE0A8),
            R.string.alerts_set_vpn, R.string.alerts_set_vpn_desc,
            sample = { SignalContent(SignalKind.VPN_ON, alertNow()) },
        ),
        AlertUi(
            "hotspot", Icons.Rounded.WifiTethering, Color(0xFFFFA24C),
            R.string.alerts_set_hotspot, R.string.alerts_set_hotspot_desc,
            sample = { SignalContent(SignalKind.HOTSPOT_ON, alertNow()) },
        ),
        AlertUi(
            "airplane", Icons.Rounded.AirplanemodeActive, Color(0xFFFFA24C),
            R.string.alerts_set_airplane, R.string.alerts_set_airplane_desc,
            sample = { ToggleContent(ToggleKind.AIRPLANE, true, alertNow()) },
        ),
        AlertUi(
            "usb", Icons.Rounded.Usb, Color(0xFF5ED6E8),
            R.string.alerts_set_usb, R.string.alerts_set_usb_desc,
            sample = { UsbContent(UsbContent.Event.CABLE_ON, UsbMode.FILES, null, alertNow()) },
        ),
        AlertUi(
            "ringer", Icons.Rounded.NotificationsActive, Color(0xFFB39CFF),
            R.string.alerts_set_ringer, R.string.alerts_set_ringer_desc,
            sample = { RingerContent(RingerKind.VIBRATE, alertNow()) },
        ),
        AlertUi(
            "mediaVolume", Icons.AutoMirrored.Rounded.VolumeUp, Color(0xFF6AB0FF),
            R.string.alerts_set_volume, R.string.alerts_set_volume_desc,
            sample = { alertNow().let { t -> VolumeContent(9, 15, t, t) } },
        ),
        AlertUi(
            "dnd", Icons.Rounded.DoNotDisturbOn, Color(0xFFB39CFF),
            R.string.alerts_set_dnd, R.string.alerts_set_dnd_desc,
            sample = { ToggleContent(ToggleKind.DND, true, alertNow()) },
        ),
        AlertUi(
            "alarm", Icons.Rounded.Alarm, Color(0xFFFFB347),
            R.string.alerts_set_alarm, R.string.alerts_set_alarm_desc,
        ),
        AlertUi(
            "torch", Icons.Rounded.FlashlightOn, Color(0xFFFFE07A),
            R.string.alerts_set_torch, R.string.alerts_set_torch_desc,
            sample = { TorchContent("0", 1, 1) }, pushable = false,
        ),
        AlertUi(
            "cameraInUse", Icons.Rounded.Videocam, Color(0xFF3DDC84),
            R.string.alerts_set_camera, R.string.alerts_set_camera_desc,
            sample = { PrivacyContent(PrivacyKind.CAMERA) }, pushable = false,
        ),
        AlertUi(
            "micInUse", Icons.Rounded.Mic, Color(0xFFFF9F43),
            R.string.alerts_set_mic, R.string.alerts_set_mic_desc,
            sample = { PrivacyContent(PrivacyKind.MIC) }, pushable = false,
        ),
        AlertUi(
            "screenshot", Icons.Rounded.Screenshot, Color(0xFFE8ECF4),
            R.string.alerts_set_screenshot, R.string.alerts_set_screenshot_desc,
            deniedRes = R.string.alerts_perm_screenshot_denied,
        ),
        AlertUi(
            "packages", Icons.Rounded.InstallMobile, Color(0xFF7BE0A8),
            R.string.alerts_set_packages, R.string.alerts_set_packages_desc,
            sample = { PackageContent("com.karelisio.bulle", "Bulle", true, alertNow()) },
        ),
        AlertUi(
            "clipboard", Icons.Rounded.ContentCopy, Color(0xFFC9A7FF),
            R.string.alerts_set_clipboard, R.string.alerts_set_clipboard_desc,
            sample = { ClipboardContent(alertNow()) },
        ),
        AlertUi(
            "echo", Icons.Rounded.Favorite, Color(0xFFFF6FA5),
            R.string.echo_set, R.string.echo_set_desc,
            sample = { EchoContent(it.getString(R.string.echo_preview_title), null, null, alertNow()) },
        ),
    )

    private val byId = all.associateBy { it.id }

    fun of(id: String): AlertUi = byId.getValue(id)
}
