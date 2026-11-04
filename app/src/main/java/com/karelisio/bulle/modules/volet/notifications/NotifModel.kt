package com.karelisio.bulle.modules.volet.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Person
import android.app.RemoteInput
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.widget.RemoteViews

/** Section du volet, dans l'ordre d'affichage (comme Android stock). */
enum class NotifSection { CONVERSATIONS, ALERTING, SILENT }

/** Classement système réduit à ce qui sert au volet (testable sans Ranking). */
data class RankInfo(
    val rank: Int = -1,
    val importance: Int = NotificationManager.IMPORTANCE_DEFAULT,
    val isConversation: Boolean = false,
    val channelId: String? = null,
    val smartReplies: List<CharSequence> = emptyList(),
    /** Visibilité imposée par le canal sur l'écran verrouillé (-1000 = aucune). */
    val lockscreenVisibility: Int = NO_OVERRIDE,
) {
    companion object {
        const val NO_OVERRIDE = -1000

        fun from(r: android.service.notification.NotificationListenerService.Ranking): RankInfo = RankInfo(
            rank = r.rank,
            importance = r.importance,
            isConversation = runCatching { r.isConversation }.getOrDefault(false),
            channelId = runCatching { r.channel?.id }.getOrNull(),
            smartReplies = runCatching { r.smartReplies.orEmpty() }.getOrDefault(emptyList()),
            lockscreenVisibility = r.lockscreenVisibilityOverride,
        )
    }
}

/** Ce qu'il faut savoir d'une notification publiée pour la reconstruire. */
data class NotifInput(
    val key: String,
    val packageName: String,
    val notification: Notification,
    val postTime: Long,
    /** Clé de groupe : groupe déclaré par l'appli, sinon regroupement par appli. */
    val groupKey: String,
    val rank: RankInfo? = null,
)

/** Message d'une conversation (MessagingStyle). */
data class NotifMessage(
    val sender: CharSequence?,
    val text: CharSequence,
    val time: Long,
    val avatar: Icon?,
    /** Envoyé par l'utilisateur lui-même. */
    val fromSelf: Boolean,
)

data class NotifProgress(val max: Int, val current: Int, val indeterminate: Boolean) {
    val fraction: Float get() = if (max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
}

/** Bouton d'action ; [remoteInput] non nul = réponse directe. */
data class NotifAction(
    val index: Int,
    val title: CharSequence,
    val intent: PendingIntent?,
    val remoteInput: RemoteInput?,
    /** Réponses proposées (choix de l'appli, puis suggestions du système). */
    val choices: List<CharSequence>,
)

/** Notification reconstruite, immuable, prête à afficher. */
data class NotifItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val groupKey: String,
    val channelId: String?,
    val shortcutId: String?,
    val time: Long,
    val showTime: Boolean,
    val title: CharSequence?,
    val text: CharSequence?,
    val subText: CharSequence?,
    val bigText: CharSequence?,
    val lines: List<CharSequence>,
    val messages: List<NotifMessage>,
    val conversationTitle: CharSequence?,
    val isGroupConversation: Boolean,
    val largeIcon: Icon?,
    val picture: Icon?,
    val smallIcon: Icon?,
    /** Couleur de l'appli (0 = couleur du thème). */
    val color: Int,
    val progress: NotifProgress?,
    val actions: List<NotifAction>,
    val contentIntent: PendingIntent?,
    val autoCancel: Boolean,
    val clearable: Boolean,
    /** Persistante ou de service : style discret. */
    val persistent: Boolean,
    val isSummary: Boolean,
    val section: NotifSection,
    val rank: Int,
    /** Contenu masqué (appareil verrouillé). */
    val redacted: Boolean,
    /** Vue personnalisée à afficher telle quelle (aucun texte exploitable). */
    val customView: RemoteViews?,
) {
    val isMessaging: Boolean get() = messages.isNotEmpty()

    /** Le contenu déplié apporte-t-il quelque chose de plus ? */
    val expandable: Boolean
        get() = !redacted && (
            bigText != null || lines.isNotEmpty() || picture != null || messages.size > 1 || actions.isNotEmpty()
            )
}

/** Groupe affiché : une carte simple (1 élément) ou une pile repliable. */
data class NotifGroup(
    val id: String,
    val packageName: String,
    val appName: String,
    val items: List<NotifItem>,
    /** Résumé du groupe (non affiché comme carte, mais effacé avec le groupe). */
    val summaryKey: String?,
) {
    val lead: NotifItem get() = items.first()
    val clearable: Boolean get() = items.any { it.clearable }
}

data class NotifSectionModel(val section: NotifSection, val groups: List<NotifGroup>)

data class NotifListModel(val sections: List<NotifSectionModel>) {
    val isEmpty: Boolean get() = sections.isEmpty()
    val allItems: List<NotifItem> get() = sections.flatMap { s -> s.groups.flatMap { it.items } }

    /** Clés à effacer pour « Tout effacer » (effaçables seulement, résumés inclus). */
    val clearableKeys: List<String>
        get() = sections.flatMap { s ->
            s.groups.flatMap { g -> g.items.filter { it.clearable }.map { it.key } }
        }

    fun groupOf(key: String): NotifGroup? =
        sections.firstNotNullOfOrNull { s -> s.groups.firstOrNull { g -> g.items.any { it.key == key } } }

    companion object {
        val EMPTY = NotifListModel(emptyList())
    }
}

/** Reconstruction native des notifications à partir de leurs extras (fonctions pures). */
object NotifParser {

    private const val STYLE_MESSAGING = "android.app.Notification\$MessagingStyle"
    private const val STYLE_MEDIA = "android.app.Notification\$MediaStyle"
    private const val STYLE_DECORATED_MEDIA = "android.app.Notification\$DecoratedMediaCustomViewStyle"
    private const val PERSISTENT_FLAGS =
        Notification.FLAG_ONGOING_EVENT or Notification.FLAG_NO_CLEAR or Notification.FLAG_FOREGROUND_SERVICE

    /** Notifications média avec session : affichées par la carte média, pas dans la liste. */
    fun isMediaSession(n: Notification): Boolean {
        val template = n.extras?.getString(Notification.EXTRA_TEMPLATE)
        return (template == STYLE_MEDIA || template == STYLE_DECORATED_MEDIA) &&
            n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true
    }

    fun isPersistent(n: Notification): Boolean = n.flags and PERSISTENT_FLAGS != 0

    fun section(n: Notification, rank: RankInfo?, isMessaging: Boolean): NotifSection {
        val conversation = if (rank != null) rank.isConversation else isMessaging && n.shortcutId != null
        if (conversation && isMessaging) return NotifSection.CONVERSATIONS
        val silent = if (rank != null) {
            rank.importance in NotificationManager.IMPORTANCE_MIN..NotificationManager.IMPORTANCE_LOW
        } else {
            @Suppress("DEPRECATION")
            n.priority <= Notification.PRIORITY_LOW
        }
        return if (silent) NotifSection.SILENT else NotifSection.ALERTING
    }

    /**
     * Faut-il masquer le contenu ? Appareil verrouillé et notification privée (sauf si
     * l'utilisateur autorise le contenu privé) ou secrète.
     */
    fun shouldRedact(n: Notification, rank: RankInfo?, locked: Boolean, allowPrivate: Boolean): Boolean {
        if (!locked) return false
        val visibility = rank?.lockscreenVisibility?.takeIf { it != RankInfo.NO_OVERRIDE }
            ?.let { minOf(it, n.visibility) } ?: n.visibility
        return when (visibility) {
            Notification.VISIBILITY_SECRET -> true
            Notification.VISIBILITY_PRIVATE -> !allowPrivate
            else -> false
        }
    }

    fun parse(
        input: NotifInput,
        appName: String,
        locked: Boolean = false,
        allowPrivateOnLock: Boolean = false,
    ): NotifItem {
        val original = input.notification
        val redact = shouldRedact(original, input.rank, locked, allowPrivateOnLock)
        // Masquée : version publique si l'appli en fournit une, sinon un texte générique.
        val n = if (redact) original.publicVersion ?: original else original
        val showContent = !redact || original.publicVersion != null
        val e = n.extras ?: Bundle()
        val template = e.getString(Notification.EXTRA_TEMPLATE)

        val title = e.getCharSequence(Notification.EXTRA_TITLE_BIG) ?: e.getCharSequence(Notification.EXTRA_TITLE)
        val text = e.getCharSequence(Notification.EXTRA_TEXT)
        val messages = if (showContent && template == STYLE_MESSAGING) messages(e) else emptyList()
        val lines = if (showContent) e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.filter { it.isNotBlank() }.orEmpty() else emptyList()
        val bigText = if (showContent) e.getCharSequence(Notification.EXTRA_BIG_TEXT)?.takeIf { it.isNotBlank() && it.toString() != text?.toString() } else null
        val progress = progress(e)
        val flags = original.flags
        val persistent = isPersistent(original)
        val isMessaging = messages.isNotEmpty()
        val custom = if (showContent && title.isNullOrBlank() && text.isNullOrBlank() && !isMessaging) {
            n.bigContentView ?: n.contentView
        } else {
            null
        }
        val time = original.`when`.takeIf { it > 0 } ?: input.postTime
        val smart = input.rank?.smartReplies.orEmpty()

        return NotifItem(
            key = input.key,
            packageName = input.packageName,
            appName = appName,
            groupKey = input.groupKey,
            channelId = input.rank?.channelId ?: original.channelId,
            shortcutId = original.shortcutId,
            time = time,
            showTime = e.getBoolean(Notification.EXTRA_SHOW_WHEN, true),
            title = if (showContent) (e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE).takeIf { isMessaging } ?: title) else null,
            text = if (showContent) text else null,
            subText = if (showContent) e.getCharSequence(Notification.EXTRA_SUB_TEXT) else null,
            bigText = bigText,
            lines = lines,
            messages = messages,
            conversationTitle = if (showContent) e.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) else null,
            isGroupConversation = e.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false),
            largeIcon = if (showContent) n.getLargeIcon() ?: bitmapIcon(e, Notification.EXTRA_LARGE_ICON) else null,
            picture = if (showContent) picture(e) else null,
            smallIcon = original.smallIcon,
            color = original.color,
            progress = progress,
            actions = if (redact) emptyList() else actions(original, smart),
            contentIntent = original.contentIntent,
            autoCancel = flags and Notification.FLAG_AUTO_CANCEL != 0,
            clearable = !persistent,
            persistent = persistent,
            isSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0,
            section = section(original, input.rank, isMessaging || (template == STYLE_MESSAGING)),
            rank = input.rank?.rank ?: -1,
            redacted = redact && original.publicVersion == null,
            customView = custom,
        )
    }

    fun progress(e: Bundle): NotifProgress? {
        val max = e.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = e.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        if (max <= 0 && !indeterminate) return null
        return NotifProgress(max, e.getInt(Notification.EXTRA_PROGRESS, 0), indeterminate)
    }

    /** Messages d'une conversation, du plus ancien au plus récent. */
    fun messages(e: Bundle): List<NotifMessage> {
        val array = parcelableArray(e, Notification.EXTRA_MESSAGES) ?: return emptyList()
        val self = parcelable(e, Notification.EXTRA_MESSAGING_PERSON, Person::class.java)
        return array.mapNotNull { p ->
            val b = p as? Bundle ?: return@mapNotNull null
            val body = b.getCharSequence("text") ?: return@mapNotNull null
            val person = parcelable(b, "sender_person", Person::class.java)
            val sender = person?.name ?: b.getCharSequence("sender")
            NotifMessage(
                sender = sender,
                text = body,
                time = b.getLong("time"),
                avatar = person?.icon,
                fromSelf = sender == null || (self?.name != null && sender.toString() == self.name.toString()),
            )
        }
    }

    private fun actions(n: Notification, smart: List<CharSequence>): List<NotifAction> =
        n.actions.orEmpty().mapIndexedNotNull { i, a ->
            val title = a.title?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val input = a.remoteInputs?.firstOrNull { it.allowFreeFormInput || !it.choices.isNullOrEmpty() }
            val choices = buildList {
                input?.choices?.let { addAll(it) }
                if (input != null && a.allowGeneratedReplies) addAll(smart.filter { s -> none { it.toString() == s.toString() } })
            }
            NotifAction(i, title, a.actionIntent, input, choices.take(5))
        }

    private fun picture(e: Bundle): Icon? {
        parcelable(e, Notification.EXTRA_PICTURE_ICON, Icon::class.java)?.let { return it }
        return bitmapIcon(e, Notification.EXTRA_PICTURE)
    }

    private fun bitmapIcon(e: Bundle, key: String): Icon? {
        val any = parcelable(e, key, Parcelable::class.java) ?: return null
        return when (any) {
            is Icon -> any
            is Bitmap -> Icon.createWithBitmap(any)
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun <T : Parcelable> parcelable(b: Bundle, key: String, cls: Class<T>): T? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) b.getParcelable(key, cls) else cls.cast(b.getParcelable(key))
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun parcelableArray(b: Bundle, key: String): Array<out Parcelable>? = runCatching {
        if (Build.VERSION.SDK_INT >= 33) b.getParcelableArray(key, Parcelable::class.java) else b.getParcelableArray(key)
    }.getOrNull()
}

/** Construction de la liste : filtrage, sections, groupes et tri (fonctions pures). */
object NotifListBuilder {

    fun build(items: List<NotifItem>): NotifListModel {
        val sections = NotifSection.entries.mapNotNull { section ->
            val inSection = items.filter { it.section == section }
            if (inSection.isEmpty()) return@mapNotNull null
            val groups = inSection.groupBy { it.groupKey }.map { (gk, members) ->
                val children = members.filter { !it.isSummary }
                val summary = members.firstOrNull { it.isSummary }
                // Résumé seul (sans enfants) : affiché comme une notification normale.
                val shown = sorted(children.ifEmpty { members })
                NotifGroup(
                    id = "${section.name}|$gk",
                    packageName = shown.first().packageName,
                    appName = shown.first().appName,
                    items = shown,
                    summaryKey = summary?.key?.takeIf { children.isNotEmpty() },
                )
            }.sortedWith(compareBy<NotifGroup> { order(it.lead) }.thenByDescending { it.lead.time })
            NotifSectionModel(section, groups)
        }
        return NotifListModel(sections)
    }

    /** Rang du système si connu, sinon heure décroissante. */
    fun sorted(items: List<NotifItem>): List<NotifItem> =
        items.sortedWith(compareBy<NotifItem> { order(it) }.thenByDescending { it.time })

    private fun order(i: NotifItem): Int = if (i.rank >= 0) i.rank else Int.MAX_VALUE
}

/** Heure relative : valeur et unité, mises en forme par l'interface. */
data class RelativeTime(val unit: Unit, val value: Long) {
    enum class Unit { NOW, MINUTES, HOURS, DAYS }

    companion object {
        fun of(now: Long, time: Long): RelativeTime {
            val d = (now - time).coerceAtLeast(0L) / 60_000L
            return when {
                d < 1 -> RelativeTime(Unit.NOW, 0)
                d < 60 -> RelativeTime(Unit.MINUTES, d)
                d < 60 * 24 -> RelativeTime(Unit.HOURS, d / 60)
                else -> RelativeTime(Unit.DAYS, d / (60 * 24))
            }
        }
    }
}

/** Initiales d'un nom (avatar de repli). */
fun initials(name: CharSequence?): String {
    val words = name?.toString()?.trim()?.split(Regex("\\s+"))?.filter { it.isNotEmpty() }.orEmpty()
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(1).uppercase()
        else -> (words[0].take(1) + words[1].take(1)).uppercase()
    }
}
