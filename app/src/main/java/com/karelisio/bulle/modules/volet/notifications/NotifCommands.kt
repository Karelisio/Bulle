package com.karelisio.bulle.modules.volet.notifications

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.notification.StatusBarNotification
import com.karelisio.bulle.modules.volet.VoletHost
import com.karelisio.bulle.notifications.NotificationRepository
import java.util.concurrent.ConcurrentHashMap

/** Actions sur les notifications des autres applis (via le listener) et intents de réglages. */
object NotifCommands {

    fun cancel(key: String) {
        runCatching { NotificationRepository.listener?.cancelNotification(key) }
    }

    fun snooze(key: String, durationMs: Long) {
        runCatching { NotificationRepository.listener?.snoozeNotification(key, durationMs) }
    }

    /** Ouvre la notification ; l'efface si elle le demande (FLAG_AUTO_CANCEL). */
    fun open(host: VoletHost, item: NotifItem): Boolean {
        // Notification système de changement de sortie audio : ouvre directement le choix de la sortie.
        if (isAudioOutputNotice(item)) {
            com.karelisio.bulle.modules.volet.media.MediaOutput.openSwitcher(host.context, item.packageName, host::close, host::startActivity)
            return true
        }
        val ok = host.send(item.contentIntent)
        if (ok && item.autoCancel) cancel(item.key)
        return ok
    }

    private val AUDIO_OUTPUT = Regex("(p[ée]riph[ée]rique|sortie)\\s+audio|audio\\s+(device|output)", RegexOption.IGNORE_CASE)

    /** « Changement de périphérique audio » (système) : texte reconnu, sans intention propre utile. */
    internal fun isAudioOutputNotice(item: NotifItem): Boolean {
        val text = listOfNotNull(item.title, item.text).joinToString(" ")
        val system = item.packageName == "android" || item.packageName.contains("systemui") || item.packageName.startsWith("com.hihonor") || item.packageName.startsWith("com.huawei")
        return (system || item.contentIntent == null) && AUDIO_OUTPUT.containsMatchIn(text)
    }

    /** Page de réglages du canal (ou de la conversation), sinon des notifications de l'appli. */
    fun settingsIntent(item: NotifItem): Intent {
        val channel = item.channelId
        return if (channel != null) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, item.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, channel)
                .apply { item.shortcutId?.let { putExtra(Settings.EXTRA_CONVERSATION_ID, it) } }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, item.packageName)
        }
    }

    /** Intent complémentaire portant la réponse directe ([fromChoice] : réponse proposée). */
    fun replyFillIn(input: RemoteInput, text: CharSequence, fromChoice: Boolean): Intent {
        val fillIn = Intent()
        val results = Bundle().apply { putCharSequence(input.resultKey, text) }
        RemoteInput.addResultsToIntent(arrayOf(input), fillIn, results)
        RemoteInput.setResultsSource(fillIn, if (fromChoice) RemoteInput.SOURCE_CHOICE else RemoteInput.SOURCE_FREE_FORM_INPUT)
        return fillIn
    }

    fun reply(host: VoletHost, action: NotifAction, text: CharSequence, fromChoice: Boolean): Boolean {
        val input = action.remoteInput ?: return false
        return host.send(action.intent, replyFillIn(input, text, fromChoice), closeAfter = false)
    }
}

/** Noms des applis, mis en cache. */
object AppLabels {
    private val cache = ConcurrentHashMap<String, String>()

    fun label(context: Context, sbn: StatusBarNotification): String = cache.getOrPut(sbn.packageName) {
        val pm = context.packageManager
        // Le Builder range l'ApplicationInfo de l'appli dans les extras.
        val info = runCatching {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT >= 33) {
                sbn.notification.extras.getParcelable("android.appInfo", ApplicationInfo::class.java)
            } else {
                sbn.notification.extras.getParcelable("android.appInfo") as? ApplicationInfo
            }
        }.getOrNull() ?: runCatching { pm.getApplicationInfo(sbn.packageName, 0) }.getOrNull()
        info?.let { runCatching { pm.getApplicationLabel(it).toString() }.getOrNull() } ?: sbn.packageName
    }
}

/** Passage des notifications actives au modèle du volet. */
object NotifModelFactory {

    /** Clé de groupe : groupe déclaré par l'appli, sinon toutes les notifications de l'appli. */
    fun groupKey(sbn: StatusBarNotification): String =
        if (sbn.isGroup) sbn.groupKey else "app|${sbn.user.hashCode()}|${sbn.packageName}"

    fun build(
        context: Context,
        active: Collection<StatusBarNotification>,
        ranking: (String) -> RankInfo?,
        locked: Boolean,
        excludedApps: Set<String>,
    ): NotifListModel {
        val allowPrivate = allowPrivateOnLockscreen(context)
        val items = active.asSequence()
            .filter { it.packageName !in excludedApps && it.packageName != context.packageName }
            .filter { !NotifParser.isMediaSession(it.notification) }
            .map { sbn ->
                NotifParser.parse(
                    NotifInput(sbn.key, sbn.packageName, sbn.notification, sbn.postTime, groupKey(sbn), ranking(sbn.key)),
                    appName = AppLabels.label(context, sbn),
                    locked = locked,
                    allowPrivateOnLock = allowPrivate,
                )
            }
            .toList()
        return NotifListBuilder.build(items)
    }

    /** Réglage système « afficher le contenu sensible sur l'écran verrouillé » (masqué par défaut). */
    private fun allowPrivateOnLockscreen(context: Context): Boolean = runCatching {
        Settings.Secure.getInt(context.contentResolver, "lock_screen_allow_private_notifications", 1) != 0
    }.getOrDefault(false)
}
