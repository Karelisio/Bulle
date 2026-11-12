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
 * Suivi d'un seul doigt pour les zones de déclenchement (logique pure, testable).
 * Le premier doigt posé devient le pointeur actif ; les autres sont ignorés. Si le doigt actif
 * se lève alors qu'un autre reste posé, le suivi bascule sur celui-ci en repartant de sa
 * position actuelle : le volet ne saute pas.
 */
internal class SinglePointerDrag(private val slop: Float, private val callbacks: Callbacks) {
    interface Callbacks {
        fun onStart()
        fun onDrag(dyPx: Float)

        /** [cancelled] : geste annulé (vitesse nulle). */
        fun onEnd(cancelled: Boolean)
    }

    /** Identifiant du pointeur suivi, ou [NONE]. */
    var activeId = NONE
        private set
    var dragging = false
        private set
    private var downY = 0f
    private var lastY = 0f

    /** Premier doigt posé. */
    fun down(id: Int, y: Float) {
        activeId = id
        downY = y
        lastY = y
        dragging = false
    }

    /** Déplacement : [yOf] donne la position d'un pointeur (null s'il n'est plus là). */
    fun move(yOf: (Int) -> Float?) {
        if (activeId == NONE) return
        val y = yOf(activeId) ?: return
        if (!dragging && y - downY > slop) {
            dragging = true
            lastY = downY + slop
            callbacks.onStart()
        }
        if (dragging) {
            val dy = y - lastY
            lastY = y
            if (dy != 0f) callbacks.onDrag(dy)
        }
    }

    /**
     * Un doigt se lève alors que d'autres restent posés. [remaining] : pointeurs encore posés
     * (identifiant → position). Retourne vrai si le suivi a basculé sur un autre doigt.
     */
    fun pointerUp(id: Int, remaining: List<Pair<Int, Float>>): Boolean {
        if (id != activeId) return false
        val next = remaining.firstOrNull { it.first != id }
        if (next == null) {
            up(cancelled = false)
            return false
        }
        // Bascule sans saut : on repart de la position actuelle du nouveau doigt.
        activeId = next.first
        lastY = next.second
        if (!dragging) downY = next.second
        return true
    }

    /** Dernier doigt levé ou geste annulé. */
    fun up(cancelled: Boolean) {
        if (activeId == NONE) return
        if (dragging) callbacks.onEnd(cancelled)
        dragging = false
        activeId = NONE
    }

    companion object {
        const val NONE = -1
    }
}

/**
 * Zone de déclenchement invisible : capte le glissement vers le bas et le transmet au volet.
 * Une fois le geste commencé ici, tous ses mouvements lui reviennent, même hors de la zone :
 * le volet suit donc le doigt en temps réel. Un seul doigt est suivi (voir [SinglePointerDrag]).
 */
@SuppressLint("ViewConstructor")
private class StripView(context: Context, private val side: Int, private val listener: StripListener) : View(context) {
    private var tracker: VelocityTracker? = null
    private var lastVelocity = 0f
    private val drag = SinglePointerDrag(
        ViewConfiguration.get(context).scaledTouchSlop.toFloat(),
        object : SinglePointerDrag.Callbacks {
            override fun onStart() = listener.onStripDragStart(side)
            override fun onDrag(dyPx: Float) = listener.onStripDrag(dyPx)
            override fun onEnd(cancelled: Boolean) = listener.onStripDragEnd(if (cancelled) 0f else lastVelocity)
        },
    )

    /** Position écran d'un pointeur de l'évènement, ou null s'il n'y figure pas. */
    private fun MotionEvent.rawYOf(id: Int): Float? {
        val i = findPointerIndex(id)
        return if (i < 0) null else getRawY(i)
    }

    /** Vitesse verticale du doigt suivi (px/s). */
    private fun velocityOf(id: Int): Float {
        val t = tracker ?: return 0f
        t.computeCurrentVelocity(1000)
        return t.getYVelocity(id)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracker?.recycle()
                tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                lastVelocity = 0f
                drag.down(ev.getPointerId(0), ev.getRawY(0))
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Second doigt : ignoré (il reste dans le suivi de vitesse, sans effet sur le doigt actif).
                tracker?.addMovement(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                tracker?.addMovement(ev)
                drag.move { id -> ev.rawYOf(id) }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                tracker?.addMovement(ev)
                val upId = ev.getPointerId(ev.actionIndex)
                if (upId == drag.activeId) {
                    lastVelocity = velocityOf(upId)
                    val remaining = (0 until ev.pointerCount)
                        .filter { it != ev.actionIndex }
                        .map { ev.getPointerId(it) to ev.getRawY(it) }
                    drag.pointerUp(upId, remaining)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(ev)
                val cancelled = ev.actionMasked == MotionEvent.ACTION_CANCEL
                if (!cancelled && drag.activeId != SinglePointerDrag.NONE) lastVelocity = velocityOf(drag.activeId)
                drag.up(cancelled)
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
