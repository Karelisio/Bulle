package com.karelisio.bulle.core.apps

import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
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

    @Volatile
    private var watching = false

    /**
     * Invalide le cache à chaque installation / désinstallation / mise à jour d'appli.
     * Sans cela (invalidate() n'était jamais appelé), une appli installée n'apparaissait
     * dans la recherche et les sélecteurs qu'au redémarrage du processus (service persistant).
     */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            invalidate()
            icons.evictAll()
        }
    }

    private fun watchPackages(context: Context) {
        if (watching) return
        synchronized(this) {
            if (watching) return
            watching = true
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching {
            // Diffusions système protégées : reçues même avec RECEIVER_NOT_EXPORTED.
            ContextCompat.registerReceiver(
                context.applicationContext, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.onFailure { watching = false }
    }

    suspend fun apps(context: Context, refresh: Boolean = false): List<AppEntry> {
        watchPackages(context)
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

    /**
     * Icône thématique (calque monochrome de l'icône adaptative, Android 13+), à teinter.
     * null si l'appli n'en fournit pas.
     */
    suspend fun themedIcon(context: Context, component: ComponentName, sizePx: Int): ImageBitmap? {
        if (Build.VERSION.SDK_INT < 33) return null
        val key = "mono:" + component.flattenToShortString() + "@" + sizePx
        icons.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching {
                val d = context.packageManager.getActivityIcon(component)
                val mono = (d as? AdaptiveIconDrawable)?.monochrome ?: return@runCatching null
                // Le calque occupe 108 dp dont 72 visibles : on recadre au centre.
                val full = (sizePx * 1.5f).toInt()
                val bmp = mono.toBitmap(full, full, Bitmap.Config.ARGB_8888)
                val inset = (full - sizePx) / 2
                Bitmap.createBitmap(bmp, inset, inset, sizePx, sizePx).asImageBitmap()
            }.getOrNull()?.also { icons.put(key, it) }
        }
    }

    /** Lance l'appli ; false si elle n'existe plus (favori d'une appli désinstallée…). */
    fun launch(context: Context, app: AppEntry): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        // Appelé depuis l'île (service d'accessibilité) : une exception ici faisait tomber le service.
        return try {
            context.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w("AppCatalog", "Appli introuvable : ${app.component}", e)
            false
        } catch (e: SecurityException) {
            Log.w("AppCatalog", "Lancement refusé : ${app.component}", e)
            false
        }
    }
}
