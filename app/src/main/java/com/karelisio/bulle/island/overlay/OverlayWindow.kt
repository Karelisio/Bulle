package com.karelisio.bulle.island.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy

/**
 * Fenêtre d'overlay d'accessibilité générique (île, volet, zones de déclenchement).
 * Gère le cycle de vie Compose maison, les drapeaux et les mises à jour du WindowManager.
 */
open class OverlayWindow(
    protected val context: Context,
    private val name: String,
    width: Int = 1,
    height: Int = 1,
    extraFlags: Int = 0,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner()
    private var root: RootView? = null

    var onOutsideTouch: () -> Unit = {}
    var onBack: () -> Boolean = { false }
    var onInsets: (WindowInsets) -> Unit = {}

    val params = LP(
        width, height,
        LP.TYPE_ACCESSIBILITY_OVERLAY,
        LP.FLAG_NOT_FOCUSABLE or
            LP.FLAG_NOT_TOUCH_MODAL or
            LP.FLAG_LAYOUT_IN_SCREEN or
            LP.FLAG_LAYOUT_NO_LIMITS or
            LP.FLAG_HARDWARE_ACCELERATED or
            extraFlags,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode = LP.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        fitInsetsTypes = 0
        windowAnimations = 0
        title = name
    }

    val isShown: Boolean get() = root != null
    val view: View? get() = root

    init {
        owner.onCreate()
    }

    /** Affiche du contenu Compose. */
    fun showCompose(content: @Composable () -> Unit): Boolean = showView(
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
        },
    )

    /** Affiche une vue quelconque. Retourne false si le système refuse la fenêtre. */
    fun showView(child: View): Boolean {
        if (root != null) return true
        val r = RootView(context).apply { addView(child) }
        owner.attachTo(r)
        r.setOnApplyWindowInsetsListener { _, insets -> onInsets(insets); insets }
        return try {
            wm.addView(r, params)
            root = r
            owner.onResume()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Impossible d'ajouter la fenêtre $name", e)
            false
        }
    }

    fun hide() {
        val r = root ?: return
        root = null
        runCatching { wm.removeViewImmediate(r) }
    }

    fun setFlag(flag: Int, on: Boolean) {
        val flags = if (on) params.flags or flag else params.flags and flag.inv()
        if (flags == params.flags) return
        params.flags = flags
        commit()
    }

    /** Rend la fenêtre focusable (saisie clavier, touche Retour) ou non. */
    fun setFocusable(focusable: Boolean, showKeyboard: Boolean = false) {
        params.softInputMode = (if (focusable && showKeyboard) LP.SOFT_INPUT_STATE_VISIBLE else LP.SOFT_INPUT_STATE_UNSPECIFIED) or
            LP.SOFT_INPUT_ADJUST_RESIZE
        setFlag(LP.FLAG_NOT_FOCUSABLE, !focusable)
    }

    /** Applique les modifications de [params]. */
    fun commit() {
        root?.let { runCatching { wm.updateViewLayout(it, params) } }
    }

    open fun destroy() {
        hide()
        owner.onDestroy()
    }

    /** Racine : touchers à l'extérieur et touche Retour. */
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
        const val TAG = "OverlayWindow"
    }
}
