package com.karelisio.bulle.modules.halo

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.util.LruCache
import android.view.Display
import android.view.RoundedCorner
import android.view.WindowManager
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Lecture de l'écran réel (coins arrondis, caméra) et des couleurs (icônes, Material You). */
object HaloDevice {

    /**
     * Écran courant : taille réelle, rayon des coins arrondis (RoundedCorner API) et caméra du haut.
     * Sans rayon connu (surcouche qui ne le déclare pas), un arrondi courant est supposé.
     */
    fun screen(context: Context): HaloScreen {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val density = context.resources.displayMetrics.density
        fun radius(pos: Int): Float = runCatching { display?.getRoundedCorner(pos)?.radius?.toFloat() }.getOrNull() ?: 0f
        var tl = radius(RoundedCorner.POSITION_TOP_LEFT)
        var tr = radius(RoundedCorner.POSITION_TOP_RIGHT)
        var br = radius(RoundedCorner.POSITION_BOTTOM_RIGHT)
        var bl = radius(RoundedCorner.POSITION_BOTTOM_LEFT)
        if (tl + tr + br + bl == 0f) {
            val guess = FALLBACK_RADIUS_DP * density
            tl = guess; tr = guess; br = guess; bl = guess
        }
        val cut = display?.cutout?.boundingRectTop?.takeIf { !it.isEmpty }
        return HaloScreen(
            width = bounds.width().toFloat(),
            height = bounds.height().toFloat(),
            topLeft = tl, topRight = tr, bottomRight = br, bottomLeft = bl,
            cutout = cut?.let { HaloRect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) },
            density = density,
        )
    }

    /** Arrondi supposé quand le système ne déclare pas ses coins (dp). */
    const val FALLBACK_RADIUS_DP = 36f

    /** Accent Material You (couleurs du fond d'écran), clair pour briller sur un bord noir. */
    fun dynamicColor(context: Context): Int =
        runCatching { context.getColor(android.R.color.system_accent1_200) }.getOrDefault(HaloPalette.DEFAULT)

    private val iconColors = LruCache<String, Int>(64)

    /** Couleur dominante de l'icône d'une appli (vive de préférence), en cache ; null si illisible. */
    suspend fun iconColor(context: Context, packageName: String): Int? {
        iconColors.get(packageName)?.let { return it }
        return withContext(Dispatchers.Default) {
            runCatching {
                val icon = context.packageManager.getApplicationIcon(packageName)
                val bitmap = icon.toBitmap(64, 64, Bitmap.Config.ARGB_8888)
                val p = Palette.from(bitmap).maximumColorCount(16).generate()
                (p.vibrantSwatch ?: p.lightVibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch)?.rgb
            }.getOrNull()?.also { iconColors.put(packageName, it) }
        }
    }
}
