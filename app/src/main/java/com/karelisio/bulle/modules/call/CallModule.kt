package com.karelisio.bulle.modules.call

import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.modules.live.LiveModule
import com.karelisio.bulle.notifications.NotificationIcons
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Module « Appels » : appels entrants et en cours, détectés via les notifications
 * (CallStyle ou catégorie « call »). Aucune permission téléphonie : on se contente
 * de déclencher les actions de la notification (décrocher, refuser, raccrocher).
 */
object CallModule : IslandModule {
    const val ID = "call"
    override val id = ID
    override val titleRes = R.string.module_call

    override fun isEnabled(settings: BulleSettings): Boolean = settings.modules[id] ?: settings.calls.enabled

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> {
        val context = env.context.applicationContext
        val firstSeen = HashMap<String, Pair<Long, Long>>() // clé → (elapsedRealtime, heure murale)
        val avatarPx = (104 * context.resources.displayMetrics.density).toInt()
        // Avatar converti une seule fois par publication de notification (clé → (postTime, image)).
        val avatars = HashMap<String, Pair<Long, ImageBitmap?>>()
        val labels = HashMap<String, String?>()
        val starts = CallStartTracker()
        fun avatarOf(key: String, postTime: Long, load: () -> ImageBitmap?): ImageBitmap? {
            avatars[key]?.let { (time, image) -> if (time == postTime) return image }
            return load().also { avatars[key] = postTime to it }
        }

        return combine(
            NotificationRepository.active,
            env.settings.map { it.calls }.distinctUntilChanged(),
        ) { active, settings ->
            val calls = active.values
                .filter { it.packageName != context.packageName }
                .mapNotNull { sbn -> CallParser.parse(sbn)?.let { sbn to it } }
            val liveKeys = calls.mapTo(HashSet()) { stateKey(it.second) }
            firstSeen.keys.retainAll(liveKeys)
            starts.retain(liveKeys)
            avatars.keys.retainAll(calls.mapTo(HashSet()) { it.first.key })
            calls.map { (sbn, call) ->
                val seen = firstSeen.getOrPut(stateKey(call)) { SystemClock.elapsedRealtime() to System.currentTimeMillis() }
                val appLabel = labels.getOrPut(sbn.packageName) { LiveModule.appLabel(context, sbn) }
                CallContent(
                    call = call,
                    avatar = avatarOf(sbn.key, sbn.postTime) {
                        NotificationIcons.load(context, call.personIcon ?: call.largeIcon, avatarPx)
                    },
                    appLabel = appLabel,
                    compactSubtitle = CallContent.compactSubtitle(call, appLabel) { context.getString(it) },
                    autoExpand = settings.autoExpand,
                    // Plus ancien `when` connu (notification reconstruite) ; sans `when` fiable, la durée
                    // part du moment où l'appel a été vu en cours.
                    startWallMillis = starts.resolve(stateKey(call), call.startWallMillis, System.currentTimeMillis(), seen.second),
                    postedAt = seen.first,
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    private fun stateKey(call: CallInfo) = call.state.name + ":" + call.key
}
