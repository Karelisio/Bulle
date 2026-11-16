package com.karelisio.bulle.modules.edges

import kotlin.math.abs

/** Ce que le détecteur reconnaît. */
sealed interface EdgeEvent {
    data class Gesture(val gesture: EdgeGesture) : EdgeEvent
    data object SliderStart : EdgeEvent
    /** Déplacement du curseur, en fraction de la course (positif = vers le haut). */
    data class SliderMove(val delta: Float) : EdgeEvent
    data object SliderEnd : EdgeEvent
}

/**
 * Reconnaissance des gestes d'une zone de bord (logique pure, testée, temps en ms et
 * distances en pixels).
 *
 * - glissement vertical le long du bord (haut / bas), ou curseur continu si [slider] ;
 * - double toucher, appui long ;
 * - glissement vers l'intérieur avec maintien, seulement si [swipeIn] (sinon le geste Retour
 *   du système reste seul maître des glissements horizontaux).
 */
class EdgeGestureDetector(
    private val inwardSign: Int,
    private val slopPx: Float,
    private val swipeMinPx: Float,
    private val inwardMinPx: Float,
    /** Course complète du curseur (pixels pour aller de 0 à 100 %). */
    private val sliderTravelPx: Float,
    var slider: Boolean = false,
    var swipeIn: Boolean = false,
    var doubleTapEnabled: Boolean = true,
    var longPressEnabled: Boolean = true,
) {
    private enum class Mode { IDLE, DOWN, SLIDER, SWIPE, INWARD, DONE }

    private var mode = Mode.IDLE
    private var downX = 0f
    private var downY = 0f
    private var downT = 0L
    private var lastY = 0f
    private var holdX = 0f
    private var holdY = 0f
    private var lastUpT = -1L
    private var lastUpX = 0f
    private var lastUpY = 0f
    private var inwardSince = -1L

    /** Une pression est en cours (pour programmer les contrôles du temps). */
    val active: Boolean get() = mode != Mode.IDLE && mode != Mode.DONE

    fun down(x: Float, y: Float, t: Long): List<EdgeEvent> {
        val events = ArrayList<EdgeEvent>()
        val isDouble = doubleTapEnabled && lastUpT >= 0 && t - lastUpT <= DOUBLE_TAP_MS &&
            abs(x - lastUpX) < slopPx * 2 && abs(y - lastUpY) < slopPx * 2
        downX = x; downY = y; downT = t; lastY = y; inwardSince = -1L
        if (isDouble) {
            lastUpT = -1L
            mode = Mode.DONE
            events += EdgeEvent.Gesture(EdgeGesture.DOUBLE_TAP)
        } else {
            mode = Mode.DOWN
        }
        return events
    }

    fun move(x: Float, y: Float, t: Long): List<EdgeEvent> {
        val dx = x - downX
        val dy = y - downY
        val events = ArrayList<EdgeEvent>()
        when (mode) {
            Mode.DOWN -> {
                val inward = dx * inwardSign
                if (swipeIn && inward > slopPx && inward > abs(dy) * 1.2f) {
                    mode = Mode.INWARD
                } else if (abs(dy) > slopPx && abs(dy) > abs(dx)) {
                    if (slider) {
                        mode = Mode.SLIDER
                        events += EdgeEvent.SliderStart
                        lastY = y
                    } else {
                        mode = Mode.SWIPE
                    }
                }
            }
            Mode.SLIDER -> {
                val d = (lastY - y) / sliderTravelPx.coerceAtLeast(1f)
                if (d != 0f) events += EdgeEvent.SliderMove(d)
                lastY = y
            }
            Mode.INWARD -> {
                // Maintien : le chrono repart dès que le doigt bouge de plus que le slop.
                if (dx * inwardSign >= inwardMinPx) {
                    if (inwardSince < 0 || abs(x - holdX) > slopPx || abs(y - holdY) > slopPx) {
                        holdX = x; holdY = y; inwardSince = t
                    }
                } else {
                    inwardSince = -1L
                }
            }
            else -> Unit
        }
        return events
    }

    /** À appeler régulièrement pendant la pression : appui long et maintien vers l'intérieur. */
    fun tick(x: Float, y: Float, t: Long): List<EdgeEvent> {
        when (mode) {
            Mode.DOWN -> if (longPressEnabled && t - downT >= LONG_PRESS_MS &&
                abs(x - downX) < slopPx && abs(y - downY) < slopPx
            ) {
                mode = Mode.DONE
                return listOf(EdgeEvent.Gesture(EdgeGesture.LONG_PRESS))
            }
            Mode.INWARD -> if ((x - downX) * inwardSign >= inwardMinPx && inwardSince >= 0 && t - inwardSince >= HOLD_MS) {
                mode = Mode.DONE
                return listOf(EdgeEvent.Gesture(EdgeGesture.SWIPE_IN))
            }
            else -> Unit
        }
        return emptyList()
    }

    fun up(x: Float, y: Float, t: Long): List<EdgeEvent> {
        val dx = x - downX
        val dy = y - downY
        val events = ArrayList<EdgeEvent>()
        when (mode) {
            Mode.SLIDER -> events += EdgeEvent.SliderEnd
            Mode.SWIPE, Mode.DOWN -> {
                if (abs(dy) >= swipeMinPx && abs(dy) > abs(dx) * 1.5f) {
                    events += EdgeEvent.Gesture(if (dy < 0) EdgeGesture.SWIPE_UP else EdgeGesture.SWIPE_DOWN)
                } else if (mode == Mode.DOWN && abs(dx) < slopPx && abs(dy) < slopPx && t - downT < LONG_PRESS_MS) {
                    // Toucher simple : retenu pour un éventuel double toucher.
                    lastUpT = t; lastUpX = x; lastUpY = y
                }
            }
            else -> Unit
        }
        mode = Mode.IDLE
        return events
    }

    /** Le système a repris le geste (geste Retour, ACTION_CANCEL). */
    fun cancel(): List<EdgeEvent> {
        val wasSlider = mode == Mode.SLIDER
        mode = Mode.IDLE
        lastUpT = -1L
        return if (wasSlider) listOf(EdgeEvent.SliderEnd) else emptyList()
    }

    companion object {
        const val LONG_PRESS_MS = 450L
        const val DOUBLE_TAP_MS = 300L
        const val HOLD_MS = 320L
    }
}
