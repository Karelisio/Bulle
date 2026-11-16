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

    /**
     * Demande au système d'effacer [key]. Chaque demande est notée dans le Journal de l'île
     * (drapeaux, listener relié ou non, exception) ; le retrait effectif l'est par le dépôt.
     */
    fun cancel(key: String) {
        val listener = NotificationRepository.listener
        val flags = NotificationRepository.active.value[key]?.notification?.flags
        com.karelisio.bulle.island.IslandLog.log(
            "Effacement demandé : $key — " + NotifDismiss.describeFlags(flags) + (if (listener == null) ", listener absent" else ""),
        )
        if (listener == null) return
        NotificationRepository.expectRemoval(key)
        runCatching { listener.cancelNotification(key) }
            .onFailure { com.karelisio.bulle.island.IslandLog.log("Effacement refusé : $key (${it.javaClass.simpleName}: ${it.message})") }
    }

    fun snooze(key: String, durationMs: Long) {
        runCatching { NotificationRepository.listener?.snoozeNotification(key, durationMs) }
    }

    /** Ouvre la notification ; l'efface si elle le demande (FLAG_AUTO_CANCEL). */
    fun open(host: VoletHost, item: NotifItem): Boolean {
        com.karelisio.bulle.island.IslandLog.log(
            "Volet : ouverture d'une notification de ${item.packageName} (canal ${item.channelId}, sortie audio : ${isAudioOutputNotice(item)})",
        )
        // Notification système de changement de sortie audio : ouvre directement le choix de la sortie.
        if (isAudioOutputNotice(item)) {
            com.karelisio.bulle.modules.volet.media.MediaOutput.openSwitcher(host.context, item.packageName, host::close, host::startActivity)
            return true
        }
        val ok = host.send(item.contentIntent)
        if (ok && item.autoCancel) cancel(item.key)
        return ok
    }

    // Espaces quelconques (y compris insécables), accents facultatifs, singulier ou pluriel.
    private val AUDIO_OUTPUT = Regex(
        "(p[ée]riph[ée]riques?|sorties?|appareils?)[\\s\\u00A0\\u202F]+(audio|de\\s+sortie\\s+audio)" +
            "|audio[\\s\\u00A0\\u202F]+(devices?|outputs?)|output[\\s\\u00A0\\u202F]+devices?",
        RegexOption.IGNORE_CASE,
    )

    /** « Changement de périphérique audio » (système) : texte reconnu, sans intention propre utile. */
    internal fun isAudioOutputNotice(item: NotifItem): Boolean = isAudioOutputPackage(item.packageName) || isAudioOutputText(
        listOfNotNull(item.title, item.text, item.subText, item.bigText).plus(item.lines).joinToString(" "),
    )

    /**
     * Service audio de la surcouche qui publie « Changement de périphérique audio » (Honor / Huawei :
     * com.hihonor.imedia.sws, souvent en vue personnalisée sans texte lisible).
     */
    internal fun isAudioOutputPackage(packageName: String): Boolean = packageName.endsWith(".imedia.sws")

    /** Texte d'une notification de changement de sortie audio (logique pure, testée). */
    internal fun isAudioOutputText(text: CharSequence): Boolean =
        // Le texte suffit : le paquet qui publie cette notification varie selon les surcouches.
        AUDIO_OUTPUT.containsMatchIn(text)

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
