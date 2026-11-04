package com.karelisio.bulle.modules.volet

import org.junit.Assert.assertEquals
import org.junit.Test

class VoletMotionTest {
    private val n = VoletZone.NOTIFICATIONS
    private val q = VoletZone.QUICK_SETTINGS

    @Test
    fun slowReleaseSnapsToNearest() {
        assertEquals(0f, VoletMotion.settleTarget(0.3f, 0f, n, 2f))
        assertEquals(1f, VoletMotion.settleTarget(0.6f, 0f, n, 2f))
        assertEquals(1f, VoletMotion.settleTarget(1.3f, 0f, n, 2f))
        assertEquals(2f, VoletMotion.settleTarget(1.6f, 0f, n, 2f))
    }

    @Test
    fun flings() {
        assertEquals(1f, VoletMotion.settleTarget(0.2f, 3f, n, 2f))
        assertEquals(2f, VoletMotion.settleTarget(1.1f, 3f, n, 2f))
        assertEquals(0f, VoletMotion.settleTarget(0.9f, -3f, n, 2f))
        assertEquals(1f, VoletMotion.settleTarget(1.8f, -3f, n, 2f))
    }

    @Test
    fun firstSwipeFromNotificationsStopsAtHalf() {
        assertEquals(1f, VoletMotion.settleTarget(1.1f, 3f, n, 1f))
    }

    @Test
    fun quickSettingsFlingOpensFullGrid() {
        assertEquals(2f, VoletMotion.settleTarget(0.5f, 3f, q, 2f))
    }
}
