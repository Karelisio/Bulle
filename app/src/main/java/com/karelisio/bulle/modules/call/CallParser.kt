package com.karelisio.bulle.modules.call

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.graphics.drawable.Icon
import android.os.Build
import android.service.notification.StatusBarNotification
import com.karelisio.bulle.modules.live.LiveFormat
import java.util.Locale

/** État d'un appel. */
enum class CallState {
    /** Ça sonne. */
    INCOMING,

    /** Appel en cours. */
    ONGOING,

    /** Filtrage d'appel (l'assistant répond pour toi). */
    SCREENING,
}

/**
 * Appel détecté dans une notification. Les icônes (Person, grande icône) restent
 * des références : elles sont converties hors du fil principal par le module.
 */
data class CallInfo(
    val key: String,
    val packageName: String,
    val state: CallState,
    val callerName: String?,
    val isVideo: Boolean,
    /** Début de l'appel (heure murale), pour la durée ; null si inconnu. */
    val startWallMillis: Long?,
    val answerIntent: PendingIntent?,
    val declineIntent: PendingIntent?,
    val hangUpIntent: PendingIntent?,
    val contentIntent: PendingIntent?,
    val color: Int,
    val personIcon: Icon?,
    val largeIcon: Icon?,
)

/** Détection des appels dans les notifications (fonctions pures). */
object CallParser {

    // Libellés d'actions reconnus quand l'appli n'utilise pas CallStyle (FR, EN, DE, ES, IT, PT).
    private val answerWords = Regex(
        "\\b(answer|accept|pick up|répondre|repondre|décrocher|decrocher|accepter|annehmen|antworten|contestar|aceptar|rispondi|accetta|atender)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val declineWords = Regex(
        "\\b(decline|reject|dismiss|refuser|rejeter|ignorer|ablehnen|rechazar|rifiuta|recusar)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val hangUpWords = Regex(
        "\\b(hang ?up|end call|end|raccrocher|terminer|auflegen|beenden|colgar|finalizar|riaggancia|termina|desligar)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun parse(sbn: StatusBarNotification): CallInfo? = parse(sbn.key, sbn.packageName, sbn.notification)

    fun parse(key: String, packageName: String, n: Notification): CallInfo? {
        val extras = n.extras ?: return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        val callType = extras.getInt(EXTRA_CALL_TYPE, 0)
        val isCallStyle = callType != 0 || template.contains("CallStyle")
        val isCallCategory = n.category == Notification.CATEGORY_CALL
        if (!isCallStyle && !isCallCategory) return null

        // Actions de la notification classées par libellé (secours sans CallStyle).
        var answer: PendingIntent? = null
        var decline: PendingIntent? = null
        var hangUp: PendingIntent? = null
        n.actions.orEmpty().forEach { a ->
            val label = a.title?.toString().orEmpty()
            val intent = a.actionIntent ?: return@forEach
            when {
                answer == null && answerWords.containsMatchIn(label) -> answer = intent
                hangUp == null && hangUpWords.containsMatchIn(label) -> hangUp = intent
                decline == null && declineWords.containsMatchIn(label) -> decline = intent
            }
        }
        // Les intents explicites de CallStyle priment.
        answer = pendingIntent(extras, EXTRA_ANSWER_INTENT) ?: answer
        decline = pendingIntent(extras, EXTRA_DECLINE_INTENT) ?: decline
        hangUp = pendingIntent(extras, EXTRA_HANG_UP_INTENT) ?: hangUp

        val chrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) && n.`when` > 0
        val ongoingFlag = n.flags and Notification.FLAG_ONGOING_EVENT != 0

        val state = when (callType) {
            CALL_TYPE_INCOMING -> CallState.INCOMING
            CALL_TYPE_ONGOING -> CallState.ONGOING
            CALL_TYPE_SCREENING -> CallState.SCREENING
            else -> when {
                answer != null -> CallState.INCOMING
                chrono || hangUp != null -> CallState.ONGOING
                n.fullScreenIntent != null -> CallState.INCOMING
                ongoingFlag -> CallState.ONGOING
                else -> return null // Catégorie « appel » sans rien d'exploitable (appel manqué mal classé…).
            }
        }

        val person = person(extras)
        val name = person?.name?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            ?: extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }

        return CallInfo(
            key = key,
            packageName = packageName,
            state = state,
            callerName = name,
            isVideo = extras.getBoolean(EXTRA_CALL_IS_VIDEO, false),
            startWallMillis = if (state == CallState.ONGOING && n.`when` > 0) n.`when` else null,
            answerIntent = answer,
            declineIntent = decline,
            hangUpIntent = hangUp,
            contentIntent = n.contentIntent,
            color = n.color,
            personIcon = person?.icon,
            largeIcon = n.getLargeIcon(),
        )
    }

    /** Durée d'appel : « m:ss » sous une heure, « h:mm:ss » au-delà. */
    fun formatDuration(millis: Long): String = LiveFormat.clock(millis.coerceAtLeast(0))

    /** Initiales de l'appelant (2 lettres max), null pour un numéro ou un nom vide. */
    fun initials(name: String?): String? {
        val clean = name?.trim().orEmpty()
        if (clean.isEmpty() || clean.none { it.isLetter() }) return null
        val words = clean.split(Regex("\\s+")).filter { w -> w.firstOrNull()?.isLetter() == true }
        val letters = when {
            words.size >= 2 -> "${words.first().first()}${words.last().first()}"
            words.size == 1 -> words.first().first().toString()
            else -> return null
        }
        return letters.uppercase(Locale.getDefault())
    }

    private fun pendingIntent(extras: android.os.Bundle, key: String): PendingIntent? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(key, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(key) as? PendingIntent
        }
    }.getOrNull()

    private fun person(extras: android.os.Bundle): Person? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) {
            extras.getParcelable(EXTRA_CALL_PERSON, Person::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(EXTRA_CALL_PERSON) as? Person
        }
    }.getOrNull()

    // Clés des extras écrites par Notification.CallStyle depuis Android 12 (les constantes
    // publiques correspondantes n'existent qu'à partir d'Android 14 pour certaines).
    const val EXTRA_CALL_TYPE = "android.callType"
    const val EXTRA_CALL_IS_VIDEO = "android.callIsVideo"
    const val EXTRA_CALL_PERSON = "android.callPerson"
    const val EXTRA_ANSWER_INTENT = "android.answerIntent"
    const val EXTRA_DECLINE_INTENT = "android.declineIntent"
    const val EXTRA_HANG_UP_INTENT = "android.hangUpIntent"

    // Valeurs de Notification.CallStyle.CALL_TYPE_* (API 31).
    const val CALL_TYPE_INCOMING = 1
    const val CALL_TYPE_ONGOING = 2
    const val CALL_TYPE_SCREENING = 3
}
