package com.karelisio.bulle.modules.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {

    private val thresholds = listOf(20, 10)

    @Test
    fun lowBattery_noAlertWithoutPreviousValue() {
        assertNull(AlertRules.crossedLowThreshold(null, 5, thresholds, charging = false))
    }

    @Test
    fun lowBattery_crossingDownTriggersThreshold() {
        assertEquals(20, AlertRules.crossedLowThreshold(21, 20, thresholds, charging = false))
        assertEquals(10, AlertRules.crossedLowThreshold(11, 10, thresholds, charging = false))
    }

    @Test
    fun lowBattery_staysQuietBelowOrRisingOrCharging() {
        assertNull(AlertRules.crossedLowThreshold(19, 18, thresholds, charging = false))
        assertNull(AlertRules.crossedLowThreshold(19, 21, thresholds, charging = false))
        assertNull(AlertRules.crossedLowThreshold(21, 20, thresholds, charging = true))
        assertNull(AlertRules.crossedLowThreshold(21, 20, emptyList(), charging = false))
    }

    @Test
    fun lowBattery_jumpAcrossSeveralKeepsLowest() {
        assertEquals(10, AlertRules.crossedLowThreshold(25, 9, thresholds, charging = false))
    }

    @Test
    fun plugged_onlyOnTransition() {
        assertFalse(AlertRules.justPlugged(null, 2))
        assertTrue(AlertRules.justPlugged(0, 1))
        assertFalse(AlertRules.justPlugged(1, 1))
        assertFalse(AlertRules.justPlugged(1, 0))
    }

    @Test
    fun chargeKind() {
        assertEquals(ChargeKind.WIRELESS, AlertRules.chargeKind(true, 3_000_000, 9_000_000))
        assertEquals(ChargeKind.NORMAL, AlertRules.chargeKind(false, 0, 0))
        // 2 A à 5 V = 10 W : normale.
        assertEquals(ChargeKind.NORMAL, AlertRules.chargeKind(false, 2_000_000, 0))
        // 3 A à 9 V = 27 W : rapide.
        assertEquals(ChargeKind.FAST, AlertRules.chargeKind(false, 3_000_000, 9_000_000))
    }

    @Test
    fun batteryPercent() {
        assertEquals(50, AlertRules.batteryPercent(50, 100))
        assertEquals(100, AlertRules.batteryPercent(255, 255))
        assertNull(AlertRules.batteryPercent(-1, 100))
        assertNull(AlertRules.batteryPercent(10, 0))
    }

    @Test
    fun ssidParsing() {
        assertEquals("Maison", AlertRules.cleanSsid("\"Maison\""))
        assertEquals("Box 5G", AlertRules.cleanSsid("Box 5G"))
        assertNull(AlertRules.cleanSsid("<unknown ssid>"))
        assertNull(AlertRules.cleanSsid("\"\""))
        assertNull(AlertRules.cleanSsid(null))
    }

    @Test
    fun bluetoothBattery() {
        assertEquals(80, AlertRules.bluetoothBattery(80))
        assertNull(AlertRules.bluetoothBattery(-1))
        assertNull(AlertRules.bluetoothBattery(101))
    }

    @Test
    fun screenshotPath() {
        assertTrue(AlertRules.isScreenshotPath("Pictures/Screenshots/", "a.png"))
        assertTrue(AlertRules.isScreenshotPath("DCIM/Screenshots/", "Screenshot_2026.jpg"))
        assertTrue(AlertRules.isScreenshotPath(null, "Screenshot_1.png"))
        assertFalse(AlertRules.isScreenshotPath("DCIM/Camera/", "IMG_1.jpg"))
    }
}
