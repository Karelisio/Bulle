package com.karelisio.bulle.modules.live

import android.app.Notification
import android.content.Context
import android.os.Build
import android.service.notification.StatusBarNotification

/**
 * Détection des « activités en cours » dans les notifications des autres applis.
 *
 * Fonction pure (hors lecture facultative du ProgressStyle Android 16, qui a besoin
 * d'un [Context]) : notification → [LiveActivity] ou null si ce n'est pas une activité
 * en cours intéressante.
 */
object LiveParser {

    /** Catégories qui signalent à elles seules une activité en cours. */
    private val liveCategories = setOf(
        Notification.CATEGORY_NAVIGATION,
        Notification.CATEGORY_STOPWATCH,
        Notification.CATEGORY_WORKOUT,
        Notification.CATEGORY_LOCATION_SHARING,
        // « transport » désigne normalement le média ; on ne la garde que hors média (VTC…).
        Notification.CATEGORY_TRANSPORT,
    )

    fun parse(sbn: StatusBarNotification, context: Context? = null): LiveActivity? =
        parse(sbn.key, sbn.packageName, sbn.notification, context)

    fun parse(key: String, packageName: String, n: Notification, context: Context? = null): LiveActivity? {
        val extras = n.extras ?: return null
        val flags = n.flags
        val category = n.category

        // --- Exclusions ---
        if (flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        if (isMedia(n)) return null
        if (isCall(n)) return null

        val ongoing = flags and Notification.FLAG_ONGOING_EVENT != 0
        val promoted = isPromoted(n)

        // --- Signaux ---
        val showChrono = extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false) && n.`when` > 0
        val countDown = extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false)
        val chrono = if (showChrono) LiveChrono(n.`when`, countDown) else null

        // Les alarmes sont gérées par un autre module ; seul un minuteur qui tourne
        // (compte à rebours, sans plein écran) est repris ici.
        if (category == Notification.CATEGORY_ALARM && (chrono?.countDown != true || n.fullScreenIntent != null)) {
            return null
        }

        val styleProgress = if (Build.VERSION.SDK_INT >= 36 && context != null) progressStyleOf(context, n) else null
        val progress = styleProgress ?: legacyProgress(n)
        val liveCategory = category in liveCategories

        val hasSignal = promoted || chrono != null || progress != null || liveCategory
        if (!hasSignal) return null
        // Une activité doit être en cours (ou promue). Exception : progression déterminée
        // explicitement catégorisée « progress » (certains téléchargements non persistants).
        val progressCategory = category == Notification.CATEGORY_PROGRESS && progress != null && !progress.indeterminate
        if (!ongoing && !promoted && !progressCategory) return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val text = (extras.getCharSequence(Notification.EXTRA_TEXT) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
            ?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val shortText = if (Build.VERSION.SDK_INT >= 36) n.shortCriticalText?.trim()?.takeIf { it.isNotEmpty() } else null

        val kind = when {
            chrono?.countDown == true -> LiveKind.TIMER
            category == Notification.CATEGORY_NAVIGATION -> LiveKind.NAVIGATION
            chrono != null -> LiveKind.STOPWATCH
            category == Notification.CATEGORY_STOPWATCH -> LiveKind.STOPWATCH
            progress != null -> LiveKind.PROGRESS
            else -> LiveKind.GENERIC
        }

        // Service au premier plan « muet » : rien d'utile à montrer.
        if (kind == LiveKind.GENERIC && title == null && text == null) return null
        // Une simple barre indéterminée sans catégorie ni promotion (« Synchronisation… ») n'est
        // pas une activité intéressante.
        if (kind == LiveKind.PROGRESS && progress?.indeterminate == true && !promoted && !liveCategory &&
            category != Notification.CATEGORY_PROGRESS
        ) {
            return null
        }

        val genericProgress = kind == LiveKind.PROGRESS && !promoted && styleProgress == null && !liveCategory

        val actions = n.actions.orEmpty()
            .filter { it.remoteInputs.isNullOrEmpty() && it.actionIntent != null && !it.title.isNullOrBlank() }
            .take(3)
            .map { LiveAction(it.title.toString(), it.actionIntent) }

        return LiveActivity(
            key = key,
            packageName = packageName,
            kind = kind,
            title = title,
            text = text,
            subText = subText,
            shortText = shortText,
            progress = progress,
            chrono = chrono,
            color = n.color,
            promoted = promoted || styleProgress != null,
            genericProgress = genericProgress,
            actions = actions,
            contentIntent = n.contentIntent,
        )
    }

    /** Notification multimédia (lecteur) : gérée par le module musique. */
    fun isMedia(n: Notification): Boolean {
        val extras = n.extras ?: return false
        if (extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) return true
        val template = extras.getString(Notification.EXTRA_TEMPLATE).orEmpty()
        return template.contains("MediaStyle") || template.contains("MediaCustomViewStyle")
    }

    /** Notification d'appel : gérée par le module appels. */
    fun isCall(n: Notification): Boolean {
        if (n.category == Notification.CATEGORY_CALL) return true
        val extras = n.extras ?: return false
        if (extras.getInt("android.callType", 0) != 0) return true
        return extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("CallStyle")
    }

    /** Notification promue en « Live update » (Android 16). */
    fun isPromoted(n: Notification): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return n.flags and Notification.FLAG_PROMOTED_ONGOING != 0
    }

    /** Barre de progression classique (setProgress). */
    fun legacyProgress(n: Notification): LiveProgress? {
        val extras = n.extras ?: return null
        val max = extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = extras.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        if (max <= 0 && !indeterminate) return null
        val current = extras.getInt(Notification.EXTRA_PROGRESS, 0).coerceIn(0, max.coerceAtLeast(0))
        return LiveProgress(current = current, max = max, indeterminate = indeterminate)
    }

    /** Progression segmentée de Notification.ProgressStyle (Android 16). */
    private fun progressStyleOf(context: Context, n: Notification): LiveProgress? {
        if (Build.VERSION.SDK_INT < 36) return null
        if (!n.extras.getString(Notification.EXTRA_TEMPLATE).orEmpty().contains("ProgressStyle")) return null
        val style = runCatching { Notification.Builder.recoverBuilder(context, n).style }.getOrNull()
            as? Notification.ProgressStyle ?: return null
        return runCatching {
            LiveProgress(
                current = style.progress,
                max = style.progressMax,
                indeterminate = style.isProgressIndeterminate,
                segments = style.progressSegments.map { LiveSegment(it.length, it.color) },
                points = style.progressPoints.map { LivePoint(it.position, it.color) },
            )
        }.getOrNull()
    }
}
