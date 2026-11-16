package com.karelisio.bulle.modules.edges

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EdgeGestureDetectorTest {

    private fun detector(slider: Boolean = false, swipeIn: Boolean = false) = EdgeGestureDetector(
        inwardSign = 1, slopPx = 10f, swipeMinPx = 50f, inwardMinPx = 40f, sliderTravelPx = 200f,
        slider = slider, swipeIn = swipeIn,
    )

    private fun gestures(events: List<EdgeEvent>) = events.filterIsInstance<EdgeEvent.Gesture>().map { it.gesture }

    @Test
    fun swipeAlongTheEdge() {
        val d = detector()
        d.down(5f, 500f, 0)
        d.move(6f, 450f, 50)
        assertEquals(listOf(EdgeGesture.SWIPE_UP), gestures(d.up(6f, 400f, 100)))
        d.down(5f, 400f, 1000)
        d.move(5f, 470f, 1050)
        assertEquals(listOf(EdgeGesture.SWIPE_DOWN), gestures(d.up(5f, 480f, 1100)))
    }

    @Test
    fun doubleTapAndLongPress() {
        val d = detector()
        d.down(5f, 500f, 0)
        assertTrue(d.up(5f, 500f, 60).isEmpty())
        assertEquals(listOf(EdgeGesture.DOUBLE_TAP), gestures(d.down(6f, 502f, 200)))
        d.up(6f, 502f, 240)
        d.down(5f, 500f, 2000)
        assertTrue(d.tick(5f, 500f, 2200).isEmpty())
        assertEquals(listOf(EdgeGesture.LONG_PRESS), gestures(d.tick(5f, 500f, 2460)))
    }

    @Test
    fun horizontalSwipeIsLeftToTheBackGesture() {
        val d = detector()
        d.down(5f, 500f, 0)
        d.move(80f, 505f, 50)
        assertTrue(gestures(d.up(120f, 505f, 100)).isEmpty())
    }

    @Test
    fun inwardSwipeWithHold() {
        val d = detector(swipeIn = true)
        d.down(5f, 500f, 0)
        d.move(30f, 502f, 30)
        d.move(60f, 503f, 60)
        assertTrue(d.tick(60f, 503f, 200).isEmpty())
        assertEquals(listOf(EdgeGesture.SWIPE_IN), gestures(d.tick(60f, 503f, 60 + EdgeGestureDetector.HOLD_MS)))
    }

    @Test
    fun sliderModeEmitsContinuousMoves() {
        val d = detector(slider = true)
        d.down(5f, 500f, 0)
        val start = d.move(5f, 480f, 20)
        assertTrue(EdgeEvent.SliderStart in start)
        val moves = d.move(5f, 380f, 60).filterIsInstance<EdgeEvent.SliderMove>()
        assertEquals(0.5f, moves.single().delta, 0.001f)
        assertEquals(listOf(EdgeEvent.SliderEnd), d.up(5f, 380f, 100))
    }

    @Test
    fun cancelByTheSystemLaunchesNothing() {
        val d = detector(slider = true)
        d.down(5f, 500f, 0)
        d.move(5f, 470f, 20)
        assertEquals(listOf(EdgeEvent.SliderEnd), d.cancel())
        // Le toucher annulé ne compte pas pour un double toucher.
        assertTrue(gestures(d.down(5f, 500f, 100)).isEmpty())
    }
}
