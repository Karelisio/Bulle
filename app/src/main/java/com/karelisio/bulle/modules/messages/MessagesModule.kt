package com.karelisio.bulle.modules.messages

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import androidx.core.content.ContextCompat
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.modules.volet.notifications.AppLabels
import com.karelisio.bulle.modules.volet.notifications.NotifInput
import com.karelisio.bulle.modules.volet.notifications.NotifModelFactory
import com.karelisio.bulle.modules.volet.notifications.RankInfo
import com.karelisio.bulle.notifications.NotificationEvent
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Saisie en cours dans l'île (clé du contenu → saisie active), et instant où elle s'est arrêtée :
 * un contenu en cours de réponse n'expire pas, puis repart pour une durée complète.
 */
data class MessageInputState(val active: Set<String> = emptySet(), val releasedAt: Map<String, Long> = emptyMap())

object MessageInput {
    private val _state = MutableStateFlow(MessageInputState())
    val state: StateFlow<MessageInputState> = _state.asStateFlow()

    fun set(key: String, active: Boolean, now: Long = SystemClock.elapsedRealtime()) = _state.update { s ->
        when {
            active && key !in s.active -> s.copy(active = s.active + key, releasedAt = s.releasedAt - key)
            !active && key in s.active -> s.copy(active = s.active - key, releasedAt = s.releasedAt + (key to now))
            else -> s
        }
    }

    fun clear(key: String) = _state.update { it.copy(active = it.active - key, releasedAt = it.releasedAt - key) }
}

/**
 * Module « Messages » : chaque nouveau message d'une appli de messagerie s'affiche dans l'île
 * (expéditeur, début du message), avec réponse rapide dans la vue étendue.
 * Les mises à jour silencieuses (même dernier message, « alerter une seule fois », notre propre
 * réponse) ne déclenchent rien. Nécessite l'accès aux notifications.
 */
object MessagesModule : IslandModule {
    const val ID = "messages"
    override val id = ID
    override val titleRes = R.string.module_messages

    /** Conversations affichées en même temps au plus (les plus récentes). */
    private const val MAX_ENTRIES = 3

    /** Une conversation annoncée : [seq] change à chaque nouveau message (nouvelle clé de contenu). */
    internal data class Entry(val info: MessageInfo, val postedAt: Long, val seq: Int) {
        val contentKey: String get() = "messages:${info.key}:$seq"
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = channelFlow {
        val ctx = env.context.applicationContext
        val prefs = MessagesPrefs.loaded(ctx)
        val tracker = MessageTracker()
        NotificationRepository.active.value.values.forEach { tracker.seed(it.key, it.notification) }
        val entries = MutableStateFlow<List<Entry>>(emptyList())
        var seq = 0

        launch {
            NotificationRepository.events.collect { e ->
                when (e) {
                    is NotificationEvent.Posted -> {
                        val sbn = e.sbn
                        val isNew = tracker.onPosted(sbn.key, sbn.notification)
                        if (sbn.packageName == ctx.packageName || sbn.packageName in prefs.value.excludedApps) return@collect
                        val rank = NotificationRepository.ranking(sbn.key)
                        val isConversation = runCatching { rank?.isConversation == true }.getOrDefault(false)
                        if (!MessageDetector.isMessaging(sbn.notification, isConversation)) return@collect
                        val existing = entries.value.firstOrNull { it.info.key == sbn.key }
                        // Mise à jour silencieuse d'une conversation affichée : contenu rafraîchi sur place.
                        if (!isNew && existing == null) return@collect
                        val info = extract(ctx, sbn, rank?.let(RankInfo::from))
                        if (info.messages.isEmpty()) return@collect
                        val typing = existing != null && existing.contentKey in MessageInput.state.value.active
                        val entry = when {
                            existing == null -> Entry(info, SystemClock.elapsedRealtime(), ++seq)
                            !isNew || typing -> existing.copy(info = info) // pendant une réponse, la clé ne change pas
                            else -> Entry(info, SystemClock.elapsedRealtime(), ++seq)
                        }
                        entries.update { list -> (list.filter { it.info.key != sbn.key } + entry).takeLast(MAX_ENTRIES) }
                    }
                    is NotificationEvent.Removed -> {
                        tracker.onRemoved(e.sbn.key)
                        val active = MessageInput.state.value.active
                        // Lue ailleurs : le contenu part, sauf si une réponse est en cours.
                        entries.update { list -> list.filter { it.info.key != e.sbn.key || it.contentKey in active } }
                    }
                }
            }
        }

        val newMessage = ctx.getString(R.string.messages_new)
        combine(entries, prefs, lockedFlow(ctx), MessageInput.state) { list, s, locked, input ->
            list.map { build(it, s, locked, input, newMessage) }
        }
            .transformLatest { list ->
                // Retire les contenus expirés : leur clé disparaît, un message balayé pourra revenir.
                while (true) {
                    val now = SystemClock.elapsedRealtime()
                    val alive = list.filter { (it.expiresAt ?: Long.MAX_VALUE) > now }
                    emit(alive)
                    val next = alive.mapNotNull { it.expiresAt }.minOrNull() ?: break
                    delay((next - now).coerceAtLeast(1) + 50)
                }
            }
            .distinctUntilChanged()
            .collect { send(it) }
    }

    private fun extract(ctx: Context, sbn: StatusBarNotification, rank: RankInfo?): MessageInfo =
        MessageExtractor.extract(
            NotifInput(sbn.key, sbn.packageName, sbn.notification, sbn.postTime, NotifModelFactory.groupKey(sbn), rank),
            appName = AppLabels.label(ctx, sbn),
        )

    internal fun build(entry: Entry, s: MessagesSettings, locked: Boolean, input: MessageInputState, newMessage: String): MessageContent {
        val key = entry.contentKey
        val visibility = MessagePrivacy.visibility(locked, s.showContent)
        return MessageContent(
            info = entry.info,
            visibility = visibility,
            contentKey = key,
            baseTimeoutMs = s.timeoutMs,
            autoExpand = s.autoExpand,
            typing = key in input.active,
            releasedAt = input.releasedAt[key],
            postedAt = entry.postedAt,
            compactSubtitle = if (visibility.showText) entry.info.preview?.let(::firstLine) else newMessage,
        )
    }

    private fun firstLine(text: String): String = text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: text

    /** État de verrouillage de l'appareil, réévalué à l'allumage, l'extinction et au déverrouillage. */
    private fun lockedFlow(ctx: Context): Flow<Boolean> = callbackFlow {
        val km = ctx.getSystemService(KeyguardManager::class.java)
        fun current() = km?.isKeyguardLocked == true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(current())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(current())
        awaitClose { runCatching { ctx.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()
}
