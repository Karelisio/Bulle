package com.karelisio.bulle.modules.rules

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.BatteryManager
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.island.ForegroundApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Moteur de règles : observe l'appli au premier plan, le casque, la charge, l'heure et le Wi-Fi,
 * puis publie les effets des règles actives et lance les actions au moment où une règle s'active.
 */
class RuleEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val rules: StateFlow<List<Rule>>,
    private val onEffects: (RuleEffects) -> Unit,
    private val onRun: (GestureAction) -> Unit,
) {
    private val world = MutableStateFlow(RuleWorld())
    private var job: Job? = null
    private var activeIds = emptySet<String>()
    private val cleanups = mutableListOf<() -> Unit>()

    fun start() {
        if (job != null) return
        registerSources()
        job = scope.launch {
            combine(rules, world, ForegroundApp.packageName) { r, w, fg -> r to w.copy(foregroundPackage = fg) }
                .collect { (r, w) ->
                    onEffects(RuleLogic.effects(r, w))
                    // Front montant : lancer les actions des règles qui viennent de s'activer.
                    val nowActive = r.filter { it.enabled && RuleLogic.isActive(it.trigger, w) }.map { it.id }.toSet()
                    r.filter { it.id in nowActive && it.id !in activeIds }
                        .mapNotNull { (it.action as? RuleAction.Run)?.action }
                        .forEach(onRun)
                    activeIds = nowActive
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        cleanups.forEach { runCatching { it() } }
        cleanups.clear()
    }

    private fun registerSources() {
        // Heure : mise à jour à chaque minute pile.
        scope.launch {
            while (isActive) {
                val c = Calendar.getInstance()
                world.update { it.copy(minuteOfDay = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)) }
                delay(60_000L - (c.get(Calendar.SECOND) * 1000L + c.get(Calendar.MILLISECOND)) + 50)
            }
        }.also { j -> cleanups += { j.cancel() } }

        // Charge.
        val battery = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                world.update { it.copy(charging = plugged) }
            }
        }
        context.registerReceiver(battery, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { battery.onReceive(context, it) }
        cleanups += { context.unregisterReceiver(battery) }

        // Casque (filaire, USB, Bluetooth).
        val audio = context.getSystemService(AudioManager::class.java)
        val headsetTypes = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
        )
        fun refreshHeadset() {
            val on = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headsetTypes }
            world.update { it.copy(headset = on) }
        }
        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refreshHeadset()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refreshHeadset()
        }
        audio.registerAudioDeviceCallback(deviceCallback, null)
        refreshHeadset()
        cleanups += { audio.unregisterAudioDeviceCallback(deviceCallback) }

        // Wi-Fi (le nom du réseau nécessite la localisation ; sinon seul « connecté » est connu).
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val wifiCallback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val ssid = (caps.transportInfo as? WifiInfo)?.ssid?.trim('"')?.takeUnless { it == "<unknown ssid>" }
                world.update { it.copy(wifiConnected = true, wifiSsid = ssid) }
            }

            override fun onLost(network: Network) {
                world.update { it.copy(wifiConnected = false, wifiSsid = null) }
            }
        }
        runCatching {
            cm.registerNetworkCallback(NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(), wifiCallback)
            cleanups += { cm.unregisterNetworkCallback(wifiCallback) }
        }
    }
}
