package com.karelisio.bulle.modules.deck

import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.provider.Settings
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.Torch
import com.karelisio.bulle.modules.focusmode.FocusMode

/**
 * État actif / inactif des raccourcis bascules du Deck (Wi-Fi, Bluetooth, rotation, lampe…),
 * lu sans permission particulière. null = pas une bascule, ou état inconnu.
 */
object ShortcutStates {

    /** Types affichés comme bascules (allumé / éteint). */
    val toggles = setOf(
        ActionType.WIFI, ActionType.BLUETOOTH, ActionType.ROTATION, ActionType.TORCH,
        ActionType.BATTERY_SAVER, ActionType.FOCUS_MODE, ActionType.MUTE,
    )

    fun read(context: Context, type: ActionType): Boolean? = runCatching {
        when (type) {
            ActionType.WIFI -> context.getSystemService(WifiManager::class.java)?.isWifiEnabled
            ActionType.BLUETOOTH -> context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
            ActionType.ROTATION -> Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            ActionType.TORCH -> Torch.isOn(context)
            ActionType.BATTERY_SAVER -> context.getSystemService(PowerManager::class.java)?.isPowerSaveMode
            ActionType.FOCUS_MODE -> FocusMode.state.value.active
            ActionType.MUTE -> context.getSystemService(AudioManager::class.java)?.ringerMode?.let { it != AudioManager.RINGER_MODE_NORMAL }
            else -> null
        }
    }.getOrNull()
}
