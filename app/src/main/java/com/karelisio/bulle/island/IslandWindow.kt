package com.karelisio.bulle.island

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.karelisio.bulle.island.overlay.OverlayOwner

/**
 * Fenêtre d'overlay d'accessibilité qui héberge l'île.
 *
 * Ancrée en haut et centrée horizontalement sur la caméra, elle est dimensionnée
 * au plus juste (enveloppe de l'île) : elle ne capte jamais les touchers en dehors.
 * Non focusable, sauf pendant une saisie (recherche d'applis).
 */
class IslandWindow(private val context: Context) {

    private val wm = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private var root: RootView? = null

    var onOutsideTouch: () -> Unit = {}
    var onStatusBarVisibility: (Boolean) -> Unit = {}
    var onBack: () -> Boolean = { false }
    private var statusBarSignalReliable = false

    private val params = LP(
        1, 1,
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

    init {
        owner.onCreate()
    }

    fun show(content: @Composable () -> Unit) {
        if (root != null) return
        val compose = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
        }
        val r = RootView(context).apply { addView(compose) }
        owner.attachTo(r)
        r.setOnApplyWindowInsetsListener { _, insets ->
            // La fenêtre d'accessibilité est au-dessus de la barre d'état : sur certains systèmes
            // celle-ci n'est jamais signalée visible. On n'utilise le signal « masquée » que si
            // on l'a déjà vue visible au moins une fois (sinon l'île serait masquée en permanence).
            val visible = insets.isVisible(WindowInsets.Type.statusBars())
            if (visible) statusBarSignalReliable = true
            onStatusBarVisibility(visible || !statusBarSignalReliable)
            insets
        }
        try {
            wm.addView(r, params)
            root = r
            owner.onResume()
        } catch (e: Exception) {
            Log.e(TAG, "Impossible d'ajouter l'île", e)
        }
    }

    fun hide() {
        val r = root ?: return
        root = null
        runCatching { wm.removeViewImmediate(r) }
    }

    /**
     * Place la fenêtre (centrée horizontalement sur [centerX], bord haut à [top]) et
     * règle sa réactivité aux touchers, en une seule mise à jour du WindowManager.
     */
    fun update(centerX: Int, top: Int, width: Int, height: Int, touchable: Boolean, watchOutside: Boolean) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        var flags = params.flags
        flags = if (touchable) flags and LP.FLAG_NOT_TOUCHABLE.inv() else flags or LP.FLAG_NOT_TOUCHABLE
        flags = if (watchOutside) flags or LP.FLAG_WATCH_OUTSIDE_TOUCH else flags and LP.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        val x = centerX - w / 2
        if (params.x == x && params.y == top && params.width == w && params.height == h && params.flags == flags) return
        params.x = x
        params.y = top
        params.width = w
        params.height = h
        params.flags = flags
        commit()
    }

    /** Rend la fenêtre focusable (saisie clavier) ou non. */
    fun setFocusable(focusable: Boolean) {
        params.softInputMode = if (focusable) LP.SOFT_INPUT_STATE_VISIBLE else LP.SOFT_INPUT_STATE_UNSPECIFIED
        setFlag(LP.FLAG_NOT_FOCUSABLE, !focusable)
    }

    private fun setFlag(flag: Int, on: Boolean) {
        val flags = if (on) params.flags or flag else params.flags and flag.inv()
        if (flags == params.flags) return
        params.flags = flags
        commit()
    }

    private fun commit() {
        root?.let { runCatching { wm.updateViewLayout(it, params) } }
    }

    fun destroy() {
        hide()
        owner.onDestroy()
    }

    /** Racine qui intercepte les touchers à l'extérieur (pour replier l'île étendue). */
    @SuppressLint("ViewConstructor")
    private inner class RootView(context: Context) : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                onOutsideTouch()
                return true
            }
            return super.dispatchTouchEvent(ev)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private companion object {
        const val TAG = "IslandWindow"
    }
}
