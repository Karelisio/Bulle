package com.karelisio.bulle.modules.alerts.sources

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.SystemClock
import androidx.compose.runtime.Composable
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
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.WifiGlyph
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private val WifiCyan = Color(0xFF5ED6E8)

/**
 * Connexion à un réseau Wi-Fi. Le nom du réseau n'est fourni par le système qu'avec
 * la permission de localisation précise (demandée à l'activation) ; sinon « Wi-Fi connecté ».
 */
object WifiSource : AlertSource {
    override val id = "wifi"

    /** Les réseaux annoncés juste après l'abonnement sont l'état initial : pas d'alerte. */
    private const val INITIAL_GRACE_MS = 1_500L

    override fun isEnabled(settings: AlertSettings) = settings.wifi

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = callbackFlow<IslandContent?> {
        val cm = env.context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val registeredAt = SystemClock.elapsedRealtime()
        val seen = HashSet<Network>()
        val announced = HashMap<Network, WifiContent>()
        val callback = object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val ssid = AlertRules.cleanSsid((caps.transportInfo as? WifiInfo)?.ssid)
                synchronized(seen) {
                    val previous = announced[network]
                    if (previous != null) {
                        // Le nom arrive parfois après la première annonce.
                        if (previous.ssid == null && ssid != null && alertNow() < (previous.expiresAt ?: 0)) {
                            announced[network] = previous.copy(ssid = ssid).also { trySend(it) }
                        }
                        return
                    }
                    if (!seen.add(network)) return
                    if (SystemClock.elapsedRealtime() - registeredAt < INITIAL_GRACE_MS) return
                    announced[network] = WifiContent(ssid, alertNow()).also { trySend(it) }
                }
            }

            override fun onLost(network: Network) {
                synchronized(seen) { seen.remove(network); announced.remove(network) }
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        cm.registerNetworkCallback(request, callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.latestAlive()
}

/** Wi-Fi connecté : ondes qui s'allument une à une + nom du réseau. */
data class WifiContent(
    val ssid: String?,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:wifi:$postedAt"
    override val alertId = "wifi"
    override val accent = WifiCyan
    override val compactSlotWidth = 92.dp

    @Composable
    override fun Leading() = WifiGlyph(WifiCyan)

    @Composable
    override fun Trailing() = AlertLabel(ssid ?: stringResource(R.string.alerts_wifi_short))

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(
            accent = WifiCyan,
            title = stringResource(R.string.alerts_wifi_connected),
            subtitle = ssid,
            glyph = { WifiGlyph(WifiCyan, size = 26.dp) },
        )
    }
}
