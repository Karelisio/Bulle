package com.karelisio.bulle.island

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import com.karelisio.bulle.core.settings.PositionTweak
import kotlin.math.max
import kotlin.math.roundToInt

/** Ce que l'on sait de l'écran et du trou de caméra, en pixels, pour la rotation courante. */
data class ScreenInfo(
    val width: Int,
    val height: Int,
    val statusBarHeight: Int,
    val cutout: Rect?,
    val density: Float,
    val rotation: Int,
)

/** Géométrie de l'île au repos (taille compacte), en pixels écran. */
data class IslandGeometry(
    val centerX: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val screenWidth: Int,
    val density: Float,
) {
    companion object {
        val Empty = IslandGeometry(0, 0, 0, 0, 0, 1f)
    }
}

object CutoutLocator {

    fun screenInfo(context: Context): ScreenInfo {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val metrics = context.getSystemService(WindowManager::class.java).maximumWindowMetrics
        val statusBar = metrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top
        // Le cutout du haut (trou ou pilule). Display.getCutout() suit la rotation courante.
        val top = display?.cutout?.boundingRectTop?.takeIf { !it.isEmpty }
        return ScreenInfo(
            width = metrics.bounds.width(),
            height = metrics.bounds.height(),
            statusBarHeight = statusBar,
            cutout = top,
            density = context.resources.displayMetrics.density,
            rotation = display?.rotation ?: 0,
        )
    }

    /**
     * Calcule l'île compacte : elle épouse le cutout avec une petite marge,
     * puis applique le réglage fin manuel.
     */
    fun geometry(info: ScreenInfo, tweak: PositionTweak): IslandGeometry {
        val d = info.density
        fun dp(v: Float) = (v * d).roundToInt()
        val padH = dp(8f)
        val padV = dp(4f)

        var width: Int
        var height: Int
        var centerX: Int
        var top: Int
        val c = info.cutout
        if (c != null) {
            width = c.width() + 2 * padH
            height = c.height() + 2 * padV
            centerX = c.centerX()
            top = c.top - padV
        } else {
            // Pas de cutout détecté : pilule centrée dans la barre d'état.
            height = max(dp(24f), (info.statusBarHeight * 0.75f).roundToInt())
            width = dp(96f)
            centerX = info.width / 2
            top = max(0, (info.statusBarHeight - height) / 2)
        }
        width += dp(tweak.extraWidthDp)
        height += dp(tweak.extraHeightDp)
        centerX += dp(tweak.offsetXDp)
        top += dp(tweak.offsetYDp)

        width = width.coerceIn(dp(16f), info.width)
        height = height.coerceIn(dp(12f), dp(120f))
        top = top.coerceAtLeast(0)
        centerX = centerX.coerceIn(width / 2, info.width - width / 2)
        return IslandGeometry(centerX, top, width, height, info.width, d)
    }
}
