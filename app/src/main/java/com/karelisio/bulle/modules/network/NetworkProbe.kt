package com.karelisio.bulle.modules.network

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.PowerManager
import android.provider.Settings
import com.karelisio.bulle.modules.alerts.broadcasts
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Accès Android du module : compteurs TrafficStats, connexion, écran. Aucune permission spéciale. */
internal object NetworkProbe {

    private fun sum(a: Long, b: Long): Long =
        if (a == TrafficStats.UNSUPPORTED.toLong() || b == TrafficStats.UNSUPPORTED.toLong()) -1L else a + b

    fun rxBytes(): Long = TrafficStats.getTotalRxBytes()
    fun txBytes(): Long = TrafficStats.getTotalTxBytes()

    fun reading(context: Context, kind: NetKind): TrafficReading = TrafficReading(
        totalBytes = sum(TrafficStats.getTotalRxBytes(), TrafficStats.getTotalTxBytes()),
        mobileBytes = sum(TrafficStats.getMobileRxBytes(), TrafficStats.getMobileTxBytes()),
        bootCount = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1) }.getOrDefault(-1),
        atMillis = System.currentTimeMillis(),
        kind = kind,
    )

    fun connection(context: Context): ConnectionInfo {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return ConnectionInfo()
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return ConnectionInfo()
        return info(caps)
    }

    private fun info(caps: NetworkCapabilities) = ConnectionInfo.fromTransports(
        wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI),
        cellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR),
        ethernet = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET),
        vpn = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
    )

    /** Connexion par défaut, mise à jour par rappel système (aucune scrutation). */
    fun connections(context: Context): Flow<ConnectionInfo> = callbackFlow {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm == null) {
            trySend(ConnectionInfo())
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                trySend(info(caps))
            }

            override fun onLost(network: Network) {
                trySend(ConnectionInfo())
            }
        }
        trySend(connection(context))
        runCatching { cm.registerDefaultNetworkCallback(callback) }
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    /** Écran allumé ? (diffusions SCREEN_ON / SCREEN_OFF, valeur initiale de PowerManager). */
    fun screenOn(context: Context): Flow<Boolean> {
        val filter = IntentFilter(Intent.ACTION_SCREEN_ON).apply { addAction(Intent.ACTION_SCREEN_OFF) }
        val initial = context.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        return broadcasts(context, filter)
            .map { it.action == Intent.ACTION_SCREEN_ON }
            .onStart { emit(initial) }
            .distinctUntilChanged()
    }
}
