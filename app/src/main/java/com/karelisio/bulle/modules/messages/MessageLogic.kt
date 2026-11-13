package com.karelisio.bulle.modules.messages

import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Icon
import com.karelisio.bulle.modules.volet.notifications.NotifAction
import com.karelisio.bulle.modules.volet.notifications.NotifInput
import com.karelisio.bulle.modules.volet.notifications.NotifItem
import com.karelisio.bulle.modules.volet.notifications.NotifMessage
import com.karelisio.bulle.modules.volet.notifications.NotifParser

/** Empreinte du dernier message d'une notification : sert à reconnaître une mise à jour silencieuse. */
data class MessageSignature(val sender: String?, val text: String, val time: Long, val fromSelf: Boolean)

/** Détection des nouveaux messages (fonctions pures, testables sans listener). */
object MessageDetector {

    private val MESSAGING_TEMPLATE = Notification.MessagingStyle::class.java.name

    /** Notification de messagerie : catégorie « message », MessagingStyle ou conversation pour le système. */
    fun isMessaging(n: Notification, isConversation: Boolean): Boolean =
        n.category == Notification.CATEGORY_MESSAGE ||
            n.extras?.getString(Notification.EXTRA_TEMPLATE) == MESSAGING_TEMPLATE ||
            isConversation

    /** Empreinte du dernier message (MessagingStyle), sinon du titre et du texte ; null = rien à lire. */
    fun signature(n: Notification): MessageSignature? {
        val e = n.extras ?: return null
        NotifParser.messages(e).lastOrNull()?.let { m ->
            return MessageSignature(m.sender?.toString(), m.text.toString(), m.time, m.fromSelf)
        }
        val text = e.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() } ?: return null
        val title = e.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        return MessageSignature(title, text, 0L, fromSelf = false)
    }

    /**
     * Faut-il annoncer cette publication comme un nouveau message ?
     * @param known la notification était déjà affichée (c'est une mise à jour) ;
     * @param previous empreinte connue avant cette publication.
     */
    fun isNewMessage(n: Notification, current: MessageSignature?, known: Boolean, previous: MessageSignature?): Boolean {
        if (current == null || current.fromSelf) return false // rien à lire, ou notre propre réponse
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false // le résumé double les enfants
        if (known) {
            if (current == previous) return false // même dernier message : simple mise à jour
            if (n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0) return false // l'appli demande le silence
        }
        return true
    }
}

/** Mémoire des empreintes par notification : distingue nouvelles publications et mises à jour. */
class MessageTracker {
    private val known = HashMap<String, MessageSignature?>()

    /** Notification déjà affichée au démarrage du module : jamais annoncée. */
    fun seed(key: String, n: Notification) {
        known[key] = MessageDetector.signature(n)
    }

    /** Enregistre la publication et indique si c'est un nouveau message à annoncer. */
    fun onPosted(key: String, n: Notification): Boolean {
        val wasKnown = known.containsKey(key)
        val previous = known[key]
        val current = MessageDetector.signature(n)
        known[key] = current
        return MessageDetector.isNewMessage(n, current, wasKnown, previous)
    }

    fun onRemoved(key: String) {
        known.remove(key)
    }
}

/** Ce que l'île affiche d'une conversation. */
data class MessageInfo(
    val key: String,
    val packageName: String,
    val appName: String,
    /** Nom affiché : expéditeur, ou titre de la conversation de groupe. */
    val sender: String?,
    val isGroup: Boolean,
    /** Les 1 à 3 derniers messages, du plus ancien au plus récent. */
    val messages: List<NotifMessage>,
    /** Avatar de l'expéditeur (Person), sinon grande icône de la notification. */
    val avatar: Icon?,
    /** Notification reconstruite par le parseur du Volet (icône et couleur de l'appli…). */
    val item: NotifItem,
    /** Action de réponse directe (RemoteInput), avec ses réponses proposées. */
    val reply: NotifAction?,
    val markAsRead: PendingIntent?,
    val contentIntent: PendingIntent?,
) {
    /** Début du dernier message reçu (seconde ligne compacte). */
    val preview: String? get() = messages.lastOrNull { !it.fromSelf }?.text?.toString() ?: messages.lastOrNull()?.text?.toString()
}

/** Extraction d'une conversation, en réutilisant le parseur du Volet. */
object MessageExtractor {

    const val MAX_MESSAGES = 3

    fun extract(input: NotifInput, appName: String): MessageInfo {
        // Parsé sans masquage : la confidentialité est appliquée à l'affichage (MessagePrivacy).
        val item = NotifParser.parse(input, appName)
        val n = input.notification
        val messages = item.messages.ifEmpty {
            val text = item.text?.takeIf { it.isNotBlank() }
            if (text == null) emptyList() else listOf(NotifMessage(item.title, text, item.time, item.largeIcon, fromSelf = false))
        }.takeLast(MAX_MESSAGES)
        val lastIncoming = messages.lastOrNull { !it.fromSelf }
        val sender = when {
            item.isGroupConversation -> item.conversationTitle ?: item.title
            else -> lastIncoming?.sender ?: item.title
        }?.toString()?.takeIf { it.isNotBlank() }
        val original = n.actions.orEmpty()
        val replies = item.actions.filter { it.remoteInput?.allowFreeFormInput == true || it.choices.isNotEmpty() }
        val reply = replies.firstOrNull { original.getOrNull(it.index)?.semanticAction == Notification.Action.SEMANTIC_ACTION_REPLY }
            ?: replies.firstOrNull()
        val markAsRead = original.firstOrNull { it.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ }?.actionIntent
        return MessageInfo(
            key = input.key,
            packageName = input.packageName,
            appName = appName,
            sender = sender,
            isGroup = item.isGroupConversation,
            messages = messages,
            avatar = (if (item.isGroupConversation) null else lastIncoming?.avatar) ?: item.largeIcon,
            item = item,
            reply = reply,
            markAsRead = markAsRead,
            contentIntent = item.contentIntent,
        )
    }
}

/** Ce qu'on a le droit de montrer. */
data class MessageVisibility(val showSender: Boolean, val showText: Boolean, val canReply: Boolean)

object MessagePrivacy {
    /**
     * Appareil verrouillé : « Nouveau message » et l'appli seulement, sans réponse.
     * Contenu désactivé dans les réglages : l'expéditeur reste visible, pas le texte.
     */
    fun visibility(locked: Boolean, showContent: Boolean): MessageVisibility = when {
        locked -> MessageVisibility(showSender = false, showText = false, canReply = false)
        else -> MessageVisibility(showSender = true, showText = showContent, canReply = true)
    }
}
