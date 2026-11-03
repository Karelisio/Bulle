package com.karelisio.bulle.core.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** Une appli lançable. */
data class AppEntry(val label: String, val packageName: String, val component: ComponentName) {
    val searchKey: String = normalize(label)
}

internal fun normalize(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace("\\p{Mn}+".toRegex(), "").lowercase()

/** Catalogue des applis installées (pour la recherche, le Deck et les sélecteurs). */
object AppCatalog {

    @Volatile
    private var cache: List<AppEntry>? = null
    private val icons = LruCache<String, ImageBitmap>(96)

    suspend fun apps(context: Context, refresh: Boolean = false): List<AppEntry> {
        cache?.takeIf { !refresh }?.let { return it }
        return withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { ri ->
                    AppEntry(
                        label = ri.loadLabel(pm).toString(),
                        packageName = ri.activityInfo.packageName,
                        component = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                    )
                }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.component }
                .sortedBy { it.searchKey }
                .also { cache = it }
        }
    }

    fun invalidate() {
        cache = null
    }

    /** Recherche : les débuts de mot d'abord, puis les correspondances internes. */
    fun search(apps: List<AppEntry>, query: String, limit: Int = 8): List<AppEntry> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return emptyList()
        return apps.asSequence()
            .mapNotNull { app ->
                val k = app.searchKey
                val score = when {
                    k.startsWith(q) -> 0
                    k.split(' ', '-', '.').any { it.startsWith(q) } -> 1
                    k.contains(q) -> 2
                    else -> return@mapNotNull null
                }
                score to app
            }
            .sortedWith(compareBy({ it.first }, { it.second.searchKey }))
            .take(limit)
            .map { it.second }
            .toList()
    }

    suspend fun icon(context: Context, component: ComponentName, sizePx: Int): ImageBitmap? {
        val key = component.flattenToShortString() + "@" + sizePx
        icons.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getActivityIcon(component)
                    .toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }.getOrNull()?.also { icons.put(key, it) }
        }
    }

    fun launch(context: Context, app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        context.startActivity(intent)
    }
}
