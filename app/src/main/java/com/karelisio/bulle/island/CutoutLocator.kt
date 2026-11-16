package com.karelisio.bulle.island

import android.content.Context
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import com.karelisio.bulle.core.settings.OpenDirection
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
    /** Paysage : trou de caméra sur le bord gauche ou droit (null sinon). */
    val sideCutout: Rect? = null,
    /** Vrai si [sideCutout] est sur le bord gauche. */
    val sideCutoutLeft: Boolean = true,
)

/** Géométrie de l'île au repos (taille compacte), en pixels écran. */
data class IslandGeometry(
    val centerX: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val screenWidth: Int,
    val density: Float,
    /** Sens d'ouverture choisi (voir [OpenDirection]). */
    val openDirection: OpenDirection = OpenDirection.CENTER,
    /**
     * Paysage, caméra sur le bord gauche ou droit : île VERTICALE collée à la caméra.
     * [width] est alors l'épaisseur (perpendiculaire au bord) et [height] la longueur le long du bord ;
     * [top] est le haut de l'île au repos, centrée sur la caméra.
     */
    val vertical: Boolean = false,
    /** Hauteur de l'écran (pixels) ; 0 = inconnue. */
    val screenHeight: Int = 0,
    /** Île verticale : caméra sur le bord gauche (sinon droit). */
    val cameraLeft: Boolean = true,
) {
    /** Centre vertical de la caméra (île au repos). */
    val centerY: Int get() = top + height / 2

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
        val left = display?.cutout?.boundingRectLeft?.takeIf { !it.isEmpty }
        val right = display?.cutout?.boundingRectRight?.takeIf { !it.isEmpty }
        return ScreenInfo(
            width = metrics.bounds.width(),
            height = metrics.bounds.height(),
            statusBarHeight = statusBar,
            cutout = top,
            density = context.resources.displayMetrics.density,
            rotation = display?.rotation ?: 0,
            sideCutout = left ?: right,
            sideCutoutLeft = left != null,
        )
    }

    /**
     * Calcule l'île compacte : elle épouse le cutout avec une petite marge,
     * puis applique le réglage fin manuel.
     */
    fun geometry(info: ScreenInfo, tweak: PositionTweak, scale: Float = 1f, landscapeMarginDp: Float = 0f): IslandGeometry {
        val d = info.density
        fun dp(v: Float) = (v * d).roundToInt()
        val padH = dp(8f)
        val padV = dp(4f)

        var width: Int
        var height: Int
        var centerX: Int
        var top: Int
        val c = info.cutout
        val side = info.sideCutout
        if (c == null && info.width > info.height && side != null) {
            // Paysage : île verticale autour de la caméra (bord gauche ou droit), ouverte vers l'intérieur.
            return sideGeometry(
                side.left, side.top, side.right, side.bottom,
                info.width, info.height, d, scale, info.sideCutoutLeft, landscapeMarginDp,
            )
        }
        if (c == null && info.width > info.height) {
            // Paysage (caméra sur le côté) : pilule en haut au centre, sans les réglages fins du portrait.
            val sc = scale.coerceIn(0.4f, 2f)
            val w = (dp(104f) * sc).roundToInt().coerceIn(dp(16f), info.width)
            val h = (dp(28f) * sc).roundToInt().coerceIn(dp(12f), dp(120f))
            return IslandGeometry(info.width / 2, dp(4f), w, h, info.width, d, tweak.openDirection, screenHeight = info.height)
        }
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
        // Taille choisie : l'île grandit autour de la caméra (le haut reste calé).
        val sc = scale.coerceIn(0.4f, 2f)
        width = (width * sc).roundToInt()
        height = (height * sc).roundToInt()
        width += dp(tweak.extraWidthDp)
        height += dp(tweak.extraHeightDp)
        centerX += dp(tweak.offsetXDp)
        top += dp(tweak.offsetYDp)

        width = width.coerceIn(dp(16f), info.width)
        height = height.coerceIn(dp(12f), dp(120f))
        top = top.coerceAtLeast(0)
        centerX = centerX.coerceIn(width / 2, info.width - width / 2)
        return IslandGeometry(centerX, top, width, height, info.width, d, tweak.openDirection, screenHeight = info.height)
    }

    /**
     * Île verticale épousant un trou de caméra latéral ([left], [top], [right], [bottom], pixels écran) :
     * épaisseur = largeur du trou + marge, longueur = hauteur du trou + marge, centrée sur lui.
     */
    /** Marge maximale réglable autour de la caméra en paysage (dp). */
    const val MAX_LANDSCAPE_MARGIN_DP = 24f

    /**
     * Trou de caméra du portrait (bord haut) vu en paysage, caméra à gauche (rotation de 90°) :
     * sert à l'aperçu des réglages, pris en portrait.
     */
    fun portraitTopToLandscapeLeft(left: Int, top: Int, right: Int, bottom: Int, portraitWidth: Int): IntArray =
        intArrayOf(top, portraitWidth - right, bottom, portraitWidth - left)

    fun sideGeometry(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        screenWidth: Int,
        screenHeight: Int,
        density: Float,
        scale: Float = 1f,
        cameraLeft: Boolean = true,
        marginDp: Float = 0f,
    ): IslandGeometry {
        fun dp(v: Float) = (v * density).roundToInt()
        val pad = dp(4f + marginDp.coerceIn(0f, MAX_LANDSCAPE_MARGIN_DP))
        val sc = scale.coerceIn(0.4f, 2f)
        val camW = (right - left).coerceAtLeast(0)
        val camH = (bottom - top).coerceAtLeast(0)
        val thick = ((camW + 2 * pad) * sc).roundToInt().coerceIn(dp(16f), dp(120f)).coerceAtMost(screenWidth)
        val length = ((camH + 2 * pad) * sc).roundToInt().coerceIn(dp(24f), screenHeight.coerceAtLeast(dp(24f)))
        val cx = ((left + right) / 2).coerceIn(thick / 2, (screenWidth - thick / 2).coerceAtLeast(thick / 2))
        val t = ((top + bottom) / 2 - length / 2).coerceIn(0, (screenHeight - length).coerceAtLeast(0))
        val dir = if (cameraLeft) OpenDirection.RIGHT else OpenDirection.LEFT
        return IslandGeometry(
            cx, t, thick, length, screenWidth, density, dir,
            vertical = true, screenHeight = screenHeight, cameraLeft = cameraLeft,
        )
    }
}
