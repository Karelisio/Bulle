package com.karelisio.bulle.modules.volet.qs

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.karelisio.bulle.R
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.core.actions.Torch
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

// ---------------------------------------------------------------------------------------------
// Outils d'observation : diffusions système et réglages (Settings.*), le temps de la collecte.
// ---------------------------------------------------------------------------------------------

/** État relu à chaque diffusion reçue (l'intent reçu est fourni, null pour la première lecture). */
internal fun broadcastState(
    context: Context,
    actions: List<String>,
    read: (Intent?) -> TileState,
): Flow<TileState> = callbackFlow {
    trySend(runCatching { read(null) }.getOrDefault(TileState(known = false)))
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            trySend(runCatching { read(intent) }.getOrDefault(TileState(known = false)))
        }
    }
    val filter = IntentFilter().apply { actions.forEach(::addAction) }
    // Diffusions système : reçues même sans exportation.
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    awaitClose { runCatching { context.unregisterReceiver(receiver) } }
}.distinctUntilChanged()

/** Valeur relue à chaque changement d'un réglage (Settings.System/Global/Secure). */
internal fun <T> settingsFlow(
    context: Context,
    uris: List<Uri>,
    descendants: Boolean = false,
    read: () -> T,
): Flow<T> = callbackFlow {
    trySend(read())
    val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            trySend(read())
        }
    }
    uris.forEach { context.contentResolver.registerContentObserver(it, descendants, observer) }
    awaitClose { context.contentResolver.unregisterContentObserver(observer) }
}.distinctUntilChanged()

/** État de tuile relu à chaque changement d'un réglage ; erreur de lecture = état inconnu. */
internal fun settingsState(
    context: Context,
    uris: List<Uri>,
    descendants: Boolean = false,
    read: () -> TileState,
): Flow<TileState> = settingsFlow(context, uris, descendants) {
    runCatching(read).getOrDefault(TileState(known = false))
}

private fun granted(context: Context, permission: String) =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Premier intent qu'une activité sait ouvrir (pages non publiques avec repli). */
internal fun firstResolvable(context: Context, vararg intents: Intent): Intent =
    intents.firstOrNull { it.resolveActivity(context.packageManager) != null } ?: intents.last()

private fun writeSettingsIntent(context: Context) =
    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, "package:${context.packageName}".toUri())

/** Bascule d'un entier de Settings.System (exige WRITE_SETTINGS). */
private class SystemIntToggle(private val key: String, private val on: Int, private val off: Int) : DirectToggle {
    override fun permitted(context: Context) = Settings.System.canWrite(context)
    override fun grantIntent(context: Context) = writeSettingsIntent(context)
    override fun set(context: Context, enable: Boolean) {
        Settings.System.putInt(context.contentResolver, key, if (enable) on else off)
    }
}

// ---------------------------------------------------------------------------------------------
// Tuiles
// ---------------------------------------------------------------------------------------------

object WifiTile : QsTile {
    override val id = "wifi"
    override val label = R.string.volet_qs_wifi
    override val icon: ImageVector = Icons.Rounded.Wifi
    override fun shizukuCommand(enable: Boolean) = "svc wifi ${if (enable) "enable" else "disable"}"
    override fun panelIntent() = Intent(Settings.Panel.ACTION_WIFI)
    override fun settingsIntent(context: Context) = Intent(Settings.ACTION_WIFI_SETTINGS)
    override fun isSupported(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)

    override fun observe(context: Context) =
        broadcastState(context, listOf(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
            TileState(active = context.getSystemService(WifiManager::class.java)?.isWifiEnabled == true)
        }
}

object BluetoothTile : QsTile {
    override val id = "bluetooth"
    override val label = R.string.volet_qs_bluetooth
    override val icon: ImageVector = Icons.Rounded.Bluetooth
    override fun shizukuCommand(enable: Boolean) = "svc bluetooth ${if (enable) "enable" else "disable"}"
    override fun settingsIntent(context: Context) = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    override fun isSupported(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)

    override fun observe(context: Context) =
        broadcastState(context, listOf(BluetoothAdapter.ACTION_STATE_CHANGED)) { intent ->
            val fromIntent = intent?.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) ?: -1
            when {
                fromIntent != -1 -> TileState(active = fromIntent == BluetoothAdapter.STATE_ON)
                // Sans BLUETOOTH_CONNECT, l'état n'est pas lisible : inconnu (affiché inactif).
                !granted(context, Manifest.permission.BLUETOOTH_CONNECT) -> TileState(known = false)
                else -> TileState(
                    active = context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true,
                )
            }
        }
}

object MobileDataTile : QsTile {
    override val id = "data"
    override val label = R.string.volet_qs_data
    override val icon: ImageVector = Icons.Rounded.SignalCellularAlt
    override fun shizukuCommand(enable: Boolean) = "svc data ${if (enable) "enable" else "disable"}"
    override fun panelIntent() = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    override fun settingsIntent(context: Context) = Intent(Settings.ACTION_DATA_USAGE_SETTINGS)
    override fun isSupported(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TELEPHONY)

    // « mobile_data » existe aussi suffixé par carte SIM : on observe toute la table Global.
    // Permission vérifiée juste avant (ACCESS_NETWORK_STATE, accordée à l'installation) ;
    // une SecurityException éventuelle est absorbée par settingsState (état inconnu).
    @SuppressLint("MissingPermission")
    override fun observe(context: Context) =
        settingsState(context, listOf(Settings.Global.CONTENT_URI), descendants = true) {
            val tm = context.getSystemService(TelephonyManager::class.java)
            // Sans droit de lecture, la tuile n'est jamais affichée active.
            if (tm == null || !granted(context, Manifest.permission.ACCESS_NETWORK_STATE)) {
                TileState(known = false)
            } else {
                TileState(active = tm.isDataEnabled)
            }
        }
}

object NfcTile : QsTile {
    override val id = "nfc"
    override val label = R.string.volet_qs_nfc
    override val icon: ImageVector = Icons.Rounded.Nfc
    override fun shizukuCommand(enable: Boolean) = "svc nfc ${if (enable) "enable" else "disable"}"
    override fun panelIntent() = Intent(Settings.Panel.ACTION_NFC)
    override fun settingsIntent(context: Context) = Intent(Settings.ACTION_NFC_SETTINGS)
    override fun isSupported(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC)

    override fun observe(context: Context) =
        broadcastState(context, listOf(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED)) {
            TileState(active = NfcAdapter.getDefaultAdapter(context)?.isEnabled == true)
        }
}

object HotspotTile : QsTile {
    override val id = "hotspot"
    override val label = R.string.volet_qs_hotspot
    override val icon: ImageVector = Icons.Rounded.WifiTethering

    // Pas de bascule publique ni de commande fiable : page « Point d'accès ».
    override fun settingsIntent(context: Context) = firstResolvable(
        context,
        Intent("android.settings.TETHER_SETTINGS"),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )

    override fun isSupported(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)

    private const val ACTION_AP_STATE = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val AP_STATE_ENABLED = 13

    override fun observe(context: Context) = broadcastState(context, listOf(ACTION_AP_STATE)) { intent ->
        if (intent != null) {
            TileState(active = intent.getIntExtra("wifi_state", 0) == AP_STATE_ENABLED)
        } else {
            // Lecture initiale par l'API cachée quand elle est accessible, sinon inconnu.
            val wm = context.getSystemService(WifiManager::class.java)
            val on = runCatching { wm.javaClass.getMethod("isWifiApEnabled").invoke(wm) as Boolean }.getOrNull()
            if (on == null) TileState(known = false) else TileState(active = on)
        }
    }
}

object TorchTile : QsTile {
    override val id = "torch"
    override val label = R.string.volet_qs_torch
    override val icon: ImageVector = Icons.Rounded.FlashlightOn
    override fun settingsIntent(context: Context): Intent? = null
    override fun isSupported(context: Context) =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    override val direct: DirectToggle = object : DirectToggle {
        override fun permitted(context: Context) = true
        override fun grantIntent(context: Context) = Intent(Settings.ACTION_SETTINGS)
        override fun set(context: Context, enable: Boolean) {
            val cm = context.getSystemService(CameraManager::class.java) ?: return
            val id = runCatching { Torch.cameraWithFlash(cm) }.getOrNull() ?: return
            runCatching { cm.setTorchMode(id, enable) }
        }
    }

    override fun observe(context: Context): Flow<TileState> = callbackFlow {
        val cm = context.getSystemService(CameraManager::class.java)
        val flashId = cm?.let { runCatching { Torch.cameraWithFlash(it) }.getOrNull() }
        trySend(TileState(active = false))
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (flashId == null || cameraId == flashId) trySend(TileState(active = enabled))
            }
        }
        cm?.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { cm?.unregisterTorchCallback(callback) }
    }.distinctUntilChanged()
}

object DndTile : QsTile {
    override val id = "dnd"
    override val label = R.string.volet_qs_dnd
    override val icon: ImageVector = Icons.Rounded.DoNotDisturbOn
    override fun shizukuCommand(enable: Boolean) = "cmd notification set_dnd ${if (enable) "priority" else "off"}"
    override fun settingsIntent(context: Context) = firstResolvable(
        context,
        Intent("android.settings.ZEN_MODE_SETTINGS"),
        Intent(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS),
    )

    override val direct: DirectToggle = object : DirectToggle {
        override fun permitted(context: Context) =
            context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

        override fun grantIntent(context: Context) = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

        override fun set(context: Context, enable: Boolean) {
            context.getSystemService(NotificationManager::class.java)?.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
            )
        }
    }

    override fun observe(context: Context) =
        broadcastState(context, listOf(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)) {
            val f = context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
                ?: NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            TileState(
                active = f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            )
        }
}

object RotationTile : QsTile {
    override val id = "rotation"
    override val label = R.string.volet_qs_rotation
    override val icon: ImageVector = Icons.Rounded.ScreenRotation
    override val direct: DirectToggle = SystemIntToggle(Settings.System.ACCELEROMETER_ROTATION, 1, 0)
    override fun shizukuCommand(enable: Boolean) =
        "settings put system ${Settings.System.ACCELEROMETER_ROTATION} ${if (enable) 1 else 0}"

    override fun settingsIntent(context: Context) = Intent(Settings.ACTION_DISPLAY_SETTINGS)

    override fun observe(context: Context) =
        settingsState(context, listOf(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION))) {
            TileState(active = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1)
        }
}

object AutoBrightnessTile : QsTile {
    override val id = "auto_brightness"
    override val label = R.string.volet_qs_auto_brightness
    override val icon: ImageVector = Icons.Rounded.BrightnessAuto
    override val direct: DirectToggle = SystemIntToggle(
        Settings.System.SCREEN_BRIGHTNESS_MODE,
        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
    )

    override fun shizukuCommand(enable: Boolean) =
        "settings put system ${Settings.System.SCREEN_BRIGHTNESS_MODE} ${if (enable) 1 else 0}"

    override fun settingsIntent(context: Context) = Intent(Settings.ACTION_DISPLAY_SETTINGS)

    override fun observe(context: Context) =
        settingsState(context, listOf(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE))) {
            TileState(
                active = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            )
        }
}

object AirplaneTile : QsTile {
    override val id = "airplane"
    override val label = R.string.volet_qs_airplane
    override val icon: ImageVector = Icons.Rounded.AirplanemodeActive
    override fun shizukuCommand(enable: Boolean) =
        "cmd connectivity airplane-mode ${if (enable) "enable" else "disable"}"

    override fun settingsIntent(context: Context) = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)

    override fun observe(context: Context) =
        broadcastState(context, listOf(Intent.ACTION_AIRPLANE_MODE_CHANGED)) {
            TileState(active = Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1)
        }
}

object LocationTile : QsTile {
    override val id = "location"
    override val label = R.string.volet_qs_location
    override val icon: ImageVector = Icons.Rounded.LocationOn

    // Android 12+ : « cmd location » ; repli sur l'ancien réglage location_mode (3 = haute précision).
    override fun shizukuCommand(enable: Boolean) =
        "cmd location set-location-enabled $enable || settings put secure location_mode ${if (enable) 3 else 0}"

    override fun settingsIntent(context: Context) = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)

    override fun observe(context: Context) =
        broadcastState(context, listOf(LocationManager.MODE_CHANGED_ACTION, LocationManager.PROVIDERS_CHANGED_ACTION)) {
            TileState(active = context.getSystemService(LocationManager::class.java)?.isLocationEnabled == true)
        }
}

/** Tuile d'action ponctuelle : pas d'état, exécute une action de geste. */
private abstract class ActionTile(type: ActionType) : QsTile {
    override val action = GestureAction(type)
    override fun observe(context: Context): Flow<TileState> = kotlinx.coroutines.flow.flowOf(TileState())
}

object ScreenshotTile : QsTile by object : ActionTile(ActionType.SCREENSHOT) {
    override val id = "screenshot"
    override val label = R.string.volet_qs_screenshot
    override val icon: ImageVector = Icons.Rounded.Screenshot
    override fun settingsIntent(context: Context): Intent? = null
}

object LockTile : QsTile by object : ActionTile(ActionType.LOCK_SCREEN) {
    override val id = "lock"
    override val label = R.string.volet_qs_lock
    override val icon: ImageVector = Icons.Rounded.Lock
    override fun settingsIntent(context: Context) = Intent(Settings.ACTION_SECURITY_SETTINGS)
}
