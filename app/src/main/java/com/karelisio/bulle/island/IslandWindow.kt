package com.karelisio.bulle.island

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager.LayoutParams as LP
import androidx.compose.runtime.Composable
import com.karelisio.bulle.island.overlay.OverlayWindow

/**
 * Fenêtres d'overlay de l'île.
 *
 * Rendu stable ([stable] = vrai, par défaut) : deux fenêtres.
 *  - une fenêtre d'AFFICHAGE fixe, pleine largeur depuis le haut de l'écran, jamais redimensionnée
 *    pendant les animations et qui ne capte aucun toucher (FLAG_NOT_TOUCHABLE) : l'île y est
 *    dessinée et animée sans aucune relayout de fenêtre, donc sans scintillement ;
 *  - une petite fenêtre TACTILE invisible, calée sur l'enveloppe de l'île, qui transmet ses touchers
 *    à la fenêtre d'affichage. Elle seule est redimensionnée (sans effet visuel).
 *
 * Rendu classique ([stable] = faux) : une seule fenêtre dimensionnée au plus juste (ancien mode).
 */
class IslandWindow(private val context: Context) {

    var onOutsideTouch: () -> Unit = {}
    var onBack: () -> Boolean = { false }
    var onStatusBarVisibility: (Boolean) -> Unit = {}
    private var statusBarSignalReliable = false

    private val displayWin = OverlayWindow(context, "Bulle")
    private val touchWin = OverlayWindow(context, "Bulle — toucher")

    /** Mode de rendu (voir la classe). Appliqué à la prochaine mise à jour. */
    var stable: Boolean = true

    private var appliedStable: Boolean? = null
    private val density = context.resources.displayMetrics.density

    init {
        // La fenêtre d'accessibilité est au-dessus de la barre d'état : sur certains systèmes
        // celle-ci n'est jamais signalée visible. On n'utilise le signal « masquée » que si
        // on l'a déjà vue visible au moins une fois (sinon l'île serait masquée en permanence).
        displayWin.onInsets = { insets ->
            val visible = insets.isVisible(WindowInsets.Type.statusBars())
            if (visible) statusBarSignalReliable = true
            onStatusBarVisibility(visible || !statusBarSignalReliable)
        }
        displayWin.onOutsideTouch = { onOutsideTouch() }
        displayWin.onBack = { onBack() }
        touchWin.onOutsideTouch = { onOutsideTouch() }
        touchWin.onBack = { onBack() }
    }

    val isShown: Boolean get() = displayWin.isShown

    /** Paramètres de la fenêtre qui suit l'île (tactile en rendu stable, unique sinon). */
    val islandParams: LP get() = if (appliedStable != false && stable) touchWin.params else displayWin.params

    fun show(content: @Composable () -> Unit) {
        displayWin.showCompose(content)
    }

    /**
     * Place l'île : enveloppe de [width] × [height] centrée horizontalement sur [centerX],
     * bord haut à [top] ; [screenWidth] / [screenHeight] dimensionnent la fenêtre d'affichage fixe.
     */
    fun update(
        centerX: Int,
        top: Int,
        width: Int,
        height: Int,
        touchable: Boolean,
        watchOutside: Boolean,
        screenWidth: Int,
        screenHeight: Int,
    ) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val x = centerX - w / 2
        if (stable) {
            ensureStableMode()
            // Fenêtre d'affichage : pleine largeur, ne grandit que si l'île en a besoin (rare).
            val needed = maxOf(top + h + (48 * density).toInt(), (screenHeight * 0.5f).toInt())
            val dp = displayWin.params
            if (dp.width != screenWidth || dp.height < needed || dp.x != 0 || dp.y != 0) {
                dp.x = 0
                dp.y = 0
                dp.width = screenWidth.coerceAtLeast(1)
                dp.height = maxOf(dp.height, needed)
                IslandLog.log("Fenêtre d'affichage ${dp.width}×${dp.height}")
                displayWin.commit()
            }
            // Fenêtre tactile : suit l'enveloppe de l'île.
            val tp = touchWin.params
            var flags = tp.flags
            flags = if (touchable) flags and LP.FLAG_NOT_TOUCHABLE.inv() else flags or LP.FLAG_NOT_TOUCHABLE
            flags = if (watchOutside) flags or LP.FLAG_WATCH_OUTSIDE_TOUCH else flags and LP.FLAG_WATCH_OUTSIDE_TOUCH.inv()
            if (tp.x != x || tp.y != top || tp.width != w || tp.height != h || tp.flags != flags) {
                tp.x = x; tp.y = top; tp.width = w; tp.height = h; tp.flags = flags
                touchWin.commit()
            }
        } else {
            ensureClassicMode()
            val p = displayWin.params
            var flags = p.flags and LP.FLAG_NOT_TOUCHABLE.inv()
            flags = if (touchable) flags else flags or LP.FLAG_NOT_TOUCHABLE
            flags = if (watchOutside) flags or LP.FLAG_WATCH_OUTSIDE_TOUCH else flags and LP.FLAG_WATCH_OUTSIDE_TOUCH.inv()
            if (p.x == x && p.y == top && p.width == w && p.height == h && p.flags == flags) return
            p.x = x; p.y = top; p.width = w; p.height = h; p.flags = flags
            IslandLog.log("Fenêtre ${w}×$h à ($x,$top)" + (if (touchable) "" else " non touchable"))
            displayWin.commit()
        }
    }

    private fun ensureStableMode() {
        if (appliedStable == true) return
        appliedStable = true
        IslandLog.log("Rendu stable (fenêtre fixe + zone tactile)")
        displayWin.params.flags = (displayWin.params.flags or LP.FLAG_NOT_TOUCHABLE) and LP.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        displayWin.commit()
        if (!touchWin.isShown) touchWin.showView(TouchForwarder(context))
    }

    private fun ensureClassicMode() {
        if (appliedStable == false) return
        appliedStable = false
        IslandLog.log("Rendu classique (fenêtre ajustée)")
        touchWin.hide()
    }

    /** Rend la fenêtre d'affichage focusable (saisie clavier dans l'île, touche Retour). */
    fun setFocusable(focusable: Boolean, showKeyboard: Boolean = false) {
        displayWin.setFocusable(focusable, showKeyboard)
    }

    fun hide() {
        touchWin.hide()
        displayWin.hide()
    }

    fun destroy() {
        touchWin.destroy()
        displayWin.destroy()
    }

    /** Vue invisible de la fenêtre tactile : renvoie chaque toucher à la fenêtre d'affichage. */
    @SuppressLint("ViewConstructor")
    private inner class TouchForwarder(context: Context) : View(context) {
        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean {
            val target = displayWin.view ?: return false
            val e = MotionEvent.obtain(event)
            // Coordonnées de la fenêtre tactile → coordonnées de la fenêtre d'affichage.
            e.offsetLocation(
                (touchWin.params.x - displayWin.params.x).toFloat(),
                (touchWin.params.y - displayWin.params.y).toFloat(),
            )
            target.dispatchTouchEvent(e)
            e.recycle()
            return true
        }
    }
}
