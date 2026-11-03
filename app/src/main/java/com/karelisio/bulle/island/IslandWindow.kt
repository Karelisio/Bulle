package com.karelisio.bulle.island

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.karelisio.bulle.island.overlay.OverlayOwner

/**
 * Fenêtre d'overlay d'accessibilité qui héberge l'île.
 *
 * La fenêtre est ancrée en haut, centrée sur l'île, et dimensionnée au plus juste
 * (enveloppe de l'île) : elle ne capte jamais les touchers en dehors.
 * Elle n'est pas focusable, sauf pendant une saisie (recherche d'applis).
 */
class IslandWindow(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private var view: ComposeView? = null

    private val params = LP(
        0, 0,
        LP.TYPE_ACCESSIBILITY_OVERLAY,
        LP.FLAG_NOT_FOCUSABLE or
            LP.FLAG_NOT_TOUCH_MODAL or
            LP.FLAG_LAYOUT_IN_SCREEN or
            LP.FLAG_LAYOUT_NO_LIMITS or
            LP.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        windowAnimations = 0
        title = "Bulle"
    }

    val isShown: Boolean get() = view != null

    init {
        owner.onCreate()
    }

    fun show(content: @Composable () -> Unit) {
        if (view != null) return
        val v = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
        }
        owner.attachTo(v)
        try {
            wm.addView(v, params)
            view = v
            owner.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "Impossible d'ajouter l'île", e)
        }
    }

    fun hide() {
        val v = view ?: return
        view = null
        runCatching { wm.removeViewImmediate(v) }
    }

    /** Place la fenêtre : centrée horizontalement sur [centerX], bord haut à [top]. */
    fun setBounds(centerX: Int, top: Int, width: Int, height: Int) {
        val x = centerX - width / 2
        if (params.x == x && params.y == top && params.width == width && params.height == height) return
        params.x = x
        params.y = top
        params.width = width
        params.height = height
        view?.let { runCatching { wm.updateViewLayout(it, params) } }
    }

    /** Rend la fenêtre focusable (saisie clavier) ou non. */
    fun setFocusable(focusable: Boolean) {
        val flags = if (focusable) params.flags and LP.FLAG_NOT_FOCUSABLE.inv()
        else params.flags or LP.FLAG_NOT_FOCUSABLE
        if (flags == params.flags) return
        params.flags = flags
        params.softInputMode = if (focusable) LP.SOFT_INPUT_STATE_VISIBLE else LP.SOFT_INPUT_STATE_UNSPECIFIED
        view?.let { runCatching { wm.updateViewLayout(it, params) } }
    }

    fun destroy() {
        hide()
        owner.onDestroy()
    }

    private companion object {
        const val TAG = "IslandWindow"
    }
}
