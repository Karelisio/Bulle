package com.karelisio.bulle.modules.alerts.sources

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded._5g
import androidx.compose.material.icons.rounded.VpnKeyOff
import androidx.compose.material.icons.rounded.VpnLock
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.WifiTetheringOff
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
import com.karelisio.bulle.modules.alerts.CellEvent
import com.karelisio.bulle.modules.alerts.CellState
import com.karelisio.bulle.modules.alerts.SignalRules
import com.karelisio.bulle.modules.alerts.VpnTracker
import com.karelisio.bulle.modules.alerts.alertNow
import com.karelisio.bulle.modules.alerts.hasPermission
import com.karelisio.bulle.modules.alerts.latestAlive
import com.karelisio.bulle.modules.alerts.ui.AlertExpanded
import com.karelisio.bulle.modules.alerts.ui.AlertLabel
import com.karelisio.bulle.modules.alerts.ui.PopIcon
import com.karelisio.bulle.modules.alerts.ui.SignalGlyph
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest

/** Ce que signale une alerte réseau. */
enum class SignalKind(val alertId: String) {
    CELL_LOST("cellular"),
    CELL_BACK("cellular"),
    FIVE_G("fiveG"),
    VPN_ON("vpn"),
    VPN_OFF("vpn"),
    HOTSPOT_ON("hotspot"),
    HOTSPOT_OFF("hotspot"),
}

/** Abonnement TelephonyCallback commun ; un refus (permission, pas de téléphonie) ferme proprement. */
private fun <T> telephony(context: Context, build: ((T) -> Unit) -> TelephonyCallback): Flow<T> = callbackFlow {
    val tm = context.getSystemService(TelephonyManager::class.java)
    val callback = build { trySend(it) }
    val registered = tm != null && runCatching {
        tm.registerTelephonyCallback(ContextCompat.getMainExecutor(context), callback)
    }.isSuccess
    awaitClose { if (registered) runCatching { tm.unregisterTelephonyCallback(callback) } }
}

/**
 * Perte et retour du réseau mobile (ServiceStateListener, sans permission).
 * Une perte n'est annoncée qu'après [SignalRules.CELL_LOSS_CONFIRM_MS] sans réseau.
 */
object CellularSource : AlertSource {
    override val id = "cellular"

    override fun isEnabled(settings: AlertSettings) = settings.cellular

    private class ServiceListener(val emit: (CellState) -> Unit) : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
        override fun onServiceStateChanged(serviceState: ServiceState) = emit(SignalRules.cellState(serviceState.state))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = flow<IslandContent?> {
        var previous: CellState? = null
        telephony<CellState>(env.context.applicationContext) { ServiceListener(it) }
            .distinctUntilChanged()
            // Une perte n'est confirmée que si elle dure : un retour rapide annule l'attente.
            .transformLatest { s ->
                if (s == CellState.NO_SERVICE) delay(SignalRules.CELL_LOSS_CONFIRM_MS)
                emit(s)
            }
            .distinctUntilChanged()
            .collect { s ->
                val event = SignalRules.cellTransition(previous, s)
                previous = s
                when (event) {
                    CellEvent.LOST -> emit(SignalContent(SignalKind.CELL_LOST, alertNow()))
                    CellEvent.BACK -> emit(SignalContent(SignalKind.CELL_BACK, alertNow()))
                    null -> Unit
                }
            }
    }.latestAlive()
}

/**
 * Passage en 5G (DisplayInfoListener : ce qu'affiche la barre d'état).
 * Demande READ_PHONE_STATE ; sans elle, ou si l'appareil refuse l'écouteur, la source reste muette.
 */
object FiveGSource : AlertSource {
    override val id = "fiveG"
    const val permission = Manifest.permission.READ_PHONE_STATE

    override fun isEnabled(settings: AlertSettings) = settings.fiveG

    override val requiredPermissions: List<String> get() = listOf(permission)

    private class DisplayListener(val emit: (Boolean) -> Unit) : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
        override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) =
            emit(SignalRules.is5g(info.networkType, info.overrideNetworkType))
    }

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = flow<IslandContent?> {
        val ctx = env.context.applicationContext
        if (!ctx.hasPermission(permission)) return@flow
        var previous: Boolean? = null
        telephony<Boolean>(ctx) { DisplayListener(it) }
            .distinctUntilChanged()
            .collect { now ->
                if (SignalRules.became5g(previous, now)) emit(SignalContent(SignalKind.FIVE_G, alertNow()))
                previous = now
            }
    }.latestAlive()
}

/** VPN connecté / déconnecté (ConnectivityManager, TRANSPORT_VPN), sans permission d'exécution. */
object VpnSource : AlertSource {
    override val id = "vpn"

    override fun isEnabled(settings: AlertSettings) = settings.vpn

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = callbackFlow<IslandContent?> {
        val cm = env.context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val registeredAt = SystemClock.elapsedRealtime()
        val tracker = VpnTracker()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (tracker.onAvailable(network.toString(), SystemClock.elapsedRealtime() - registeredAt)) {
                    trySend(SignalContent(SignalKind.VPN_ON, alertNow()))
                }
            }

            override fun onLost(network: Network) {
                if (tracker.onLost(network.toString())) trySend(SignalContent(SignalKind.VPN_OFF, alertNow()))
            }
        }
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        cm.registerNetworkCallback(request, callback)
        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.latestAlive()
}

/**
 * Point d'accès activé / désactivé : diffusion système « android.net.wifi.WIFI_AP_STATE_CHANGED »
 * (collante ; la diffusion rejouée à l'inscription donne l'état initial, sans alerte).
 */
object HotspotSource : AlertSource {
    override val id = "hotspot"
    private const val ACTION = "android.net.wifi.WIFI_AP_STATE_CHANGED"
    private const val EXTRA_STATE = "wifi_state"

    override fun isEnabled(settings: AlertSettings) = settings.hotspot

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = callbackFlow<IslandContent?> {
        val ctx = env.context.applicationContext
        var stable: Boolean? = null
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val (next, event) = SignalRules.hotspot(stable, intent.getIntExtra(EXTRA_STATE, -1), isInitialStickyBroadcast)
                stable = next
                if (event != null) trySend(SignalContent(if (event) SignalKind.HOTSPOT_ON else SignalKind.HOTSPOT_OFF, alertNow()))
            }
        }
        ContextCompat.registerReceiver(ctx, receiver, IntentFilter(ACTION), ContextCompat.RECEIVER_EXPORTED)
        awaitClose { runCatching { ctx.unregisterReceiver(receiver) } }
    }.latestAlive()
}

/** Alerte réseau ponctuelle : barres, 5G, cadenas VPN ou ondes du point d'accès. */
data class SignalContent(
    val kind: SignalKind,
    override val postedAt: Long,
) : AlertContent() {
    override val key = "alerts:signal:${kind.name.lowercase()}:$postedAt"
    override val alertId = kind.alertId
    override val baseTimeoutMs: Long? = 3_500L
    override val compactSlotWidth = 92.dp
    override val accent: Color = when (kind) {
        SignalKind.CELL_LOST, SignalKind.VPN_OFF, SignalKind.HOTSPOT_OFF -> Color(0xFFB8C0CC)
        SignalKind.CELL_BACK -> Color(0xFF5BE38A)
        SignalKind.FIVE_G -> Color(0xFF6AB0FF)
        SignalKind.VPN_ON -> Color(0xFF7BE0A8)
        SignalKind.HOTSPOT_ON -> Color(0xFFFFA24C)
    }

    @Composable
    private fun Glyph(size: Int) {
        when (kind) {
            SignalKind.CELL_LOST -> SignalGlyph(Color(0xFFFF8A80), lost = true, size = size.dp)
            SignalKind.CELL_BACK -> SignalGlyph(accent, lost = false, size = size.dp)
            SignalKind.FIVE_G -> PopIcon(Icons.Rounded._5g, accent, size = size.dp)
            SignalKind.VPN_ON -> PopIcon(Icons.Rounded.VpnLock, accent, size = size.dp)
            SignalKind.VPN_OFF -> PopIcon(Icons.Rounded.VpnKeyOff, accent, size = size.dp)
            SignalKind.HOTSPOT_ON -> PopIcon(Icons.Rounded.WifiTethering, accent, size = size.dp)
            SignalKind.HOTSPOT_OFF -> PopIcon(Icons.Rounded.WifiTetheringOff, accent, size = size.dp)
        }
    }

    @Composable
    private fun title(): String = stringResource(
        when (kind) {
            SignalKind.CELL_LOST -> R.string.alerts_cell_lost
            SignalKind.CELL_BACK -> R.string.alerts_cell_back
            SignalKind.FIVE_G -> R.string.alerts_five_g
            SignalKind.VPN_ON -> R.string.alerts_vpn_on
            SignalKind.VPN_OFF -> R.string.alerts_vpn_off
            SignalKind.HOTSPOT_ON -> R.string.alerts_hotspot_on
            SignalKind.HOTSPOT_OFF -> R.string.alerts_hotspot_off
        },
    )

    @Composable
    private fun short(): String = stringResource(
        when (kind) {
            SignalKind.CELL_LOST -> R.string.alerts_cell_lost_short
            SignalKind.CELL_BACK -> R.string.alerts_cell_back_short
            SignalKind.FIVE_G -> R.string.alerts_five_g_short
            SignalKind.VPN_ON, SignalKind.VPN_OFF -> R.string.alerts_vpn_short
            SignalKind.HOTSPOT_ON, SignalKind.HOTSPOT_OFF -> R.string.alerts_hotspot_short
        },
    )

    @Composable
    private fun subtitle(): String? = when (kind) {
        SignalKind.CELL_LOST -> stringResource(R.string.alerts_cell_lost_desc)
        SignalKind.FIVE_G -> null
        SignalKind.VPN_ON, SignalKind.HOTSPOT_ON -> stringResource(R.string.alerts_on)
        SignalKind.VPN_OFF, SignalKind.HOTSPOT_OFF -> stringResource(R.string.alerts_off)
        SignalKind.CELL_BACK -> null
    }

    @Composable
    override fun Leading() = Glyph(20)

    @Composable
    override fun Trailing() = AlertLabel(short(), color = accent)

    @Composable
    override fun Expanded(host: IslandHost) {
        AlertExpanded(accent = accent, title = title(), subtitle = subtitle(), glyph = { Glyph(26) })
    }
}
