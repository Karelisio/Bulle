package com.karelisio.bulle.modules.live

import com.karelisio.bulle.notifications.NotificationIcons

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.service.notification.StatusBarNotification
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Module « Live updates » : minuteurs, chronos, guidage, livraisons, VTC, téléchargements…
 * Tout vient des notifications actives (lecture locale, rien n'est stocké ni envoyé).
 * Les téléchargements sont regroupés par [LiveDownloads].
 */
object LiveModule : IslandModule {
    const val ID = "live"
    override val id = ID
    override val titleRes = R.string.module_live

    override fun isEnabled(settings: BulleSettings): Boolean = settings.modules[id] ?: settings.live.enabled

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> {
        val context = env.context.applicationContext
        // Première apparition de chaque activité (elapsedRealtime) : postedAt stable d'une publication à l'autre.
        val firstSeen = HashMap<String, Long>()
        val labels = HashMap<String, String?>()
        // Icônes converties une seule fois par publication de notification (clé → (postTime, visuels)).
        val visuals = HashMap<String, Pair<Long, LiveVisuals>>()
        val density = context.resources.displayMetrics.density
        val iconPx = (32 * density).toInt()

        return combine(
            NotificationRepository.active,
            env.settings.map { it.live }.distinctUntilChanged(),
        ) { active, settings ->
            val activities = active.values
                .asSequence()
                .filter { it.packageName != context.packageName && it.packageName !in settings.excludedPackages }
                .mapNotNull { sbn -> LiveParser.parse(sbn, context)?.let { sbn to it } }
                .filter { (_, a) -> settings.showGenericProgress || !a.genericProgress }
                .toList()
            firstSeen.keys.retainAll(activities.mapTo(HashSet()) { it.second.key })
            visuals.keys.retainAll(firstSeen.keys)
            val contents = activities.map { (sbn, a) ->
                LiveContent(
                    activity = a,
                    visuals = visuals[sbn.key]?.takeIf { it.first == sbn.postTime }?.second
                        ?: visualsOf(context, sbn, labels, iconPx).also { visuals[sbn.key] = sbn.postTime to it },
                    postedAt = firstSeen.getOrPut(a.key) { SystemClock.elapsedRealtime() },
                )
            }
            // Tous les téléchargements dans un seul contenu de clé stable (l'île étendue ne se
            // replie plus quand le téléchargement au premier plan change).
            LiveDownloads.group(contents) { n -> context.resources.getQuantityString(R.plurals.live_downloads_count, n, n) }
        }.flowOn(Dispatchers.Default)
    }

    private fun visualsOf(context: Context, sbn: StatusBarNotification, labels: HashMap<String, String?>, px: Int): LiveVisuals {
        val n = sbn.notification
        return LiveVisuals(
            appLabel = labels.getOrPut(sbn.packageName) { appLabel(context, sbn) },
            smallIcon = NotificationIcons.load(context, n.smallIcon, px),
            largeIcon = NotificationIcons.load(context, n.getLargeIcon(), px * 2),
        )
    }

    /**
     * Nom lisible de l'appli d'une notification : d'abord l'ApplicationInfo que le système
     * joint aux extras (aucune visibilité de paquet requise), sinon le gestionnaire de paquets.
     */
    @Suppress("DEPRECATION")
    fun appLabel(context: Context, sbn: StatusBarNotification): String? {
        val pm = context.packageManager
        val fromExtras = runCatching {
            sbn.notification.extras?.getParcelable<ApplicationInfo>("android.appInfo")?.loadLabel(pm)?.toString()
        }.getOrNull()
        return fromExtras ?: appLabel(context, sbn.packageName)
    }

    /** Nom lisible d'une appli à partir de son paquet (null si le paquet n'est pas visible). */
    @Suppress("DEPRECATION")
    fun appLabel(context: Context, packageName: String): String? = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()
}
