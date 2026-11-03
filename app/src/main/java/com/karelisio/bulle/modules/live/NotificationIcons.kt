package com.karelisio.bulle.modules.live

import android.content.Context
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Conversion des icônes de notification en ImageBitmap, hors du fil principal.
 * Les icônes de ressource (petites icônes) sont mises en cache : même instance
 * d'une publication à l'autre, donc des contenus égaux et pas de recomposition.
 */
object NotificationIcons {

    private val cache = LruCache<String, ImageBitmap>(48)

    fun load(context: Context, icon: Icon?, sizePx: Int): ImageBitmap? {
        if (icon == null || sizePx <= 0) return null
        val cacheKey = if (icon.type == Icon.TYPE_RESOURCE) {
            runCatching { "${icon.resPackage}:${icon.resId}:$sizePx" }.getOrNull()
        } else {
            null
        }
        cacheKey?.let { k -> cache.get(k)?.let { return it } }
        val bitmap = runCatching {
            icon.loadDrawable(context)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
        }.getOrNull() ?: return null
        cacheKey?.let { cache.put(it, bitmap) }
        return bitmap
    }
}
