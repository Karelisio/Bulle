package com.karelisio.bulle.notifications

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Événement ponctuel de notification (publication ou retrait). */
sealed interface NotificationEvent {
    val sbn: StatusBarNotification

    data class Posted(override val sbn: StatusBarNotification) : NotificationEvent
    data class Removed(override val sbn: StatusBarNotification, val reason: Int) : NotificationEvent
}

/**
 * Point d'accès partagé aux notifications des autres applis, alimenté par
 * [BulleNotificationListener]. Les modules (musique, live updates, appels, alertes…)
 * s'y abonnent ; rien n'est stocké ni envoyé ailleurs.
 */
object NotificationRepository {

    private val _active = MutableStateFlow<Map<String, StatusBarNotification>>(emptyMap())

    /** Notifications actuellement affichées (hors celles de Bulle), indexées par clé. */
    val active: StateFlow<Map<String, StatusBarNotification>> = _active.asStateFlow()

    private val _events = MutableSharedFlow<NotificationEvent>(extraBufferCapacity = 64)

    /** Flux des publications / retraits, pour les réactions ponctuelles (Écho, alertes). */
    val events: SharedFlow<NotificationEvent> = _events.asSharedFlow()

    private val _connected = MutableStateFlow(false)

    /** Le système a relié le listener (accès aux notifications accordé et actif). */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    /** Listener en cours, pour les actions (annuler une notification…). */
    @Volatile
    var listener: BulleNotificationListener? = null
        internal set

    fun component(context: Context) = ComponentName(context, BulleNotificationListener::class.java)

    /** L'accès aux notifications est-il accordé dans les réglages ? */
    fun isAccessGranted(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))

    internal fun onConnected(all: List<StatusBarNotification>, ownPackage: String) {
        _active.value = all.filter { it.packageName != ownPackage }.associateBy { it.key }
        _connected.value = true
    }

    internal fun onDisconnected() {
        _connected.value = false
        _active.value = emptyMap()
    }

    internal fun onPosted(sbn: StatusBarNotification) {
        _active.update { it + (sbn.key to sbn) }
        _events.tryEmit(NotificationEvent.Posted(sbn))
    }

    internal fun onRemoved(sbn: StatusBarNotification, reason: Int) {
        _active.update { it - sbn.key }
        _events.tryEmit(NotificationEvent.Removed(sbn, reason))
    }
}
