package com.karelisio.bulle.modules.volet

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.window.OnBackAnimationCallback
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.compose.runtime.Composable
import com.karelisio.bulle.island.overlay.OverlayWindow

/** Rectangle d'une zone de déclenchement, en pixels écran. */
data class StripRect(val x: Int, val y: Int, val width: Int, val height: Int)

/** Écouteur des glissements commencés dans une zone de déclenchement. */
interface StripListener {
    fun onStripDragStart(side: Int)
    fun onStripDrag(dyPx: Float)
    fun onStripDragEnd(velocityPxPerSec: Float)
}

/**
 * Zone de déclenchement invisible : capte le glissement vers le bas et le transmet au volet.
 * Une fois le geste commencé ici, tous ses mouvements lui reviennent, même hors de la zone :
 * le volet suit donc le doigt en temps réel.
 */
@SuppressLint("ViewConstructor")
private class StripView(context: Context, private val side: Int, private val listener: StripListener) : View(context) {
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downY = 0f
    private var lastY = 0f
    private var dragging = false
    private var tracker: VelocityTracker? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = ev.rawY
                lastY = ev.rawY
                dragging = false
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                if (!dragging && ev.rawY - downY > slop) {
                    dragging = true
                    lastY = downY + slop
                    listener.onStripDragStart(side)
                }
                if (dragging) {
                    listener.onStripDrag(ev.rawY - lastY)
                    lastY = ev.rawY
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                if (dragging) {
                    tracker?.computeCurrentVelocity(1000)
                    listener.onStripDragEnd(if (ev.actionMasked == MotionEvent.ACTION_CANCEL) 0f else tracker?.yVelocity ?: 0f)
                }
                dragging = false
                tracker?.recycle()
                tracker = null
            }
        }
        return true
    }
}

/** Les deux zones de déclenchement, de part et d'autre de l'île. */
class TriggerStrips(private val context: Context, private val listener: StripListener) {
    private val windows = arrayOfNulls<OverlayWindow>(2)
    private val views = arrayOfNulls<View>(2)

    /** [rects] : zone gauche (côté 0) et droite (côté 1) ; null = pas de zone. */
    fun update(rects: List<StripRect?>, touchable: Boolean, preview: Boolean, previewColor: Int) {
        for (side in 0..1) {
            val r = rects.getOrNull(side)
            if (r == null || r.width <= 0 || r.height <= 0) {
                windows[side]?.destroy()
                windows[side] = null
                views[side] = null
                continue
            }
            val w = windows[side] ?: OverlayWindow(context, "Bulle — zone du volet").also { win ->
                val v = StripView(context, side, listener)
                win.params.width = r.width
                win.params.height = r.height
                win.params.x = r.x
                win.params.y = r.y
                if (!win.showView(v)) return@also
                views[side] = v
                windows[side] = win
            }
            if (!w.isShown) continue
            views[side]?.setBackgroundColor(if (preview) previewColor else Color.TRANSPARENT)
            val p = w.params
            val flags = if (touchable || preview) p.flags and LP.FLAG_NOT_TOUCHABLE.inv() else p.flags or LP.FLAG_NOT_TOUCHABLE
            if (p.x != r.x || p.y != r.y || p.width != r.width || p.height != r.height || p.flags != flags) {
                p.x = r.x; p.y = r.y; p.width = r.width; p.height = r.height; p.flags = flags
                w.commit()
            }
        }
    }

    fun destroy() {
        windows.forEach { it?.destroy() }
        windows.fill(null)
        views.fill(null)
    }
}

/**
 * Fenêtre plein écran du volet. Ajoutée seulement pendant l'ouverture et retirée une fois
 * fermé : aucune fenêtre ne reste au-dessus des applis quand le volet est fermé.
 */
class VoletPanelWindow(context: Context) : OverlayWindow(
    context, "Bulle — volet", LP.MATCH_PARENT, LP.MATCH_PARENT,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private var lastBlur = -1
    private var backCallback: OnBackInvokedCallback? = null

    /** Retour prédictif : progression du geste (0 – 1). */
    var onBackProgress: (Float) -> Unit = {}

    fun show(content: @Composable () -> Unit): Boolean {
        val ok = showCompose(content)
        if (ok) registerBack()
        return ok
    }

    /** Flou de l'arrière-plan si le système le permet (sinon le voile teinté suffit). */
    fun setBlur(radiusPx: Int) {
        if (Build.VERSION.SDK_INT < 31 || !wm.isCrossWindowBlurEnabled) return
        val r = radiusPx.coerceAtLeast(0)
        // Mises à jour espacées : chaque changement relance une composition de fenêtres.
        if (r == lastBlur || (lastBlur >= 0 && kotlin.math.abs(r - lastBlur) < 6 && r != 0)) return
        lastBlur = r
        params.flags = if (r > 0) params.flags or LP.FLAG_BLUR_BEHIND else params.flags and LP.FLAG_BLUR_BEHIND.inv()
        params.blurBehindRadius = r
        commit()
    }

    private fun registerBack() {
        if (Build.VERSION.SDK_INT < 33) return
        val dispatcher = view?.findOnBackInvokedDispatcher() ?: return
        val cb: OnBackInvokedCallback = if (Build.VERSION.SDK_INT >= 34) {
            object : OnBackAnimationCallback {
                override fun onBackProgressed(backEvent: android.window.BackEvent) = onBackProgress(backEvent.progress)
                override fun onBackCancelled() = onBackProgress(0f)
                override fun onBackInvoked() {
                    onBackProgress(0f)
                    onBack()
                }
            }
        } else {
            OnBackInvokedCallback { onBack() }
        }
        dispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
        backCallback = cb
    }

    override fun destroy() {
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback?.let { cb -> runCatching { view?.findOnBackInvokedDispatcher()?.unregisterOnBackInvokedCallback(cb) } }
        }
        backCallback = null
        super.destroy()
    }
}
