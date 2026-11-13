package com.karelisio.bulle.modules.external

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap

/** Icônes reçues des applis : filtrage et chargement. */
object ExternalIcons {
    /** Côté maximal (px) d'une icône chargée : tout est redimensionné. */
    private const val MAX_PX = 192

    /**
     * Garde une Icon seulement si elle est sûre : image (bitmap) ou ressource de l'appli appelante.
     * Les icônes par URI sont refusées (Bulle ne lit pas de contenu au nom d'une autre appli).
     */
    fun sanitize(icon: Icon, caller: String): Icon? = runCatching {
        when (icon.type) {
            Icon.TYPE_BITMAP, Icon.TYPE_ADAPTIVE_BITMAP, Icon.TYPE_DATA -> icon
            Icon.TYPE_RESOURCE -> icon.takeIf { it.resPackage == caller }
            else -> null
        }
    }.getOrNull()

    /** Icône à afficher pour un contenu : celle fournie, ou la ressource de l'appli, sinon null. */
    fun iconOf(spec: ExternalSpec): Icon? =
        spec.icon ?: spec.iconRes?.takeIf { !spec.local }?.let { runCatching { Icon.createWithResource(spec.packageName, it) }.getOrNull() }

    /** Charge l'icône en bitmap borné (à appeler hors du fil principal). */
    fun load(context: Context, icon: Icon, sizePx: Int): Bitmap? = runCatching {
        val px = sizePx.coerceIn(16, MAX_PX)
        icon.loadDrawable(context)?.toBitmap(px, px)
    }.getOrNull()

    /** Icône de l'appli (lanceur), pour les contenus sans icône. */
    fun appIcon(context: Context, packageName: String, sizePx: Int): Bitmap? = runCatching {
        val px = sizePx.coerceIn(16, MAX_PX)
        context.packageManager.getApplicationIcon(packageName).toBitmap(px, px)
    }.getOrNull()

    /** Nom de l'appli, ou le nom du paquet si elle est introuvable. */
    fun appLabel(context: Context, packageName: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)
}
