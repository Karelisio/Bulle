package com.karelisio.bulle.modules.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.island.Priority
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.transformLatest

/**
 * Une source d'alertes (batterie, Bluetooth, Wi-Fi…).
 *
 * Le cycle de vie suit la collecte du flux : la source « démarre » (s'abonne au système)
 * quand [contents] est collecté et « s'arrête » (se désinscrit) quand la collecte cesse
 * (callbackFlow + awaitClose). Elle ne signale que des changements, jamais l'état initial,
 * sauf pour les états persistants (alarme qui sonne, lampe allumée).
 */
interface AlertSource {
    val id: String

    /** Cette alerte est-elle cochée dans les réglages ? */
    fun isEnabled(settings: AlertSettings): Boolean

    /** Contenus actuellement affichés par la source. */
    fun contents(env: ModuleEnv): Flow<List<IslandContent>>
}

/** Base des contenus ponctuels du module : priorité, durée de vie et module communs. */
abstract class AlertContent : IslandContent() {
    override val moduleId: String get() = AlertsModule.ID
    override val priority: Priority get() = Priority.ALERT
    override val timeoutMs: Long? get() = DEFAULT_TIMEOUT_MS
    override val canBeSecondary: Boolean get() = false

    companion object {
        const val DEFAULT_TIMEOUT_MS = 4_000L
    }
}

/** Heure courante utilisée comme postedAt et comme suffixe de clé (une clé par alerte). */
internal fun alertNow(): Long = SystemClock.elapsedRealtime()

/**
 * Transforme un flux d'alertes ponctuelles en liste « dernière alerte encore vivante » :
 * l'alerte est retirée de la liste après son timeout, pour que sa clé disparaisse proprement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun Flow<IslandContent?>.latestAlive(): Flow<List<IslandContent>> = transformLatest { c ->
    if (c == null) {
        emit(emptyList())
        return@transformLatest
    }
    emit(listOf(c))
    val remaining = (c.expiresAt ?: return@transformLatest) - SystemClock.elapsedRealtime()
    delay(remaining.coerceAtLeast(0) + 250)
    emit(emptyList())
}

/** Abonnement à des broadcasts système, désinscrit automatiquement. */
internal fun broadcasts(context: Context, filter: IntentFilter): Flow<Intent> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            trySend(intent)
        }
    }
    // Diffusions protégées (seul le système peut les envoyer) : exportées pour les recevoir
    // aussi depuis les processus système hors system_server (Bluetooth, téléphonie…).
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    awaitClose { runCatching { context.unregisterReceiver(receiver) } }
}

internal fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
