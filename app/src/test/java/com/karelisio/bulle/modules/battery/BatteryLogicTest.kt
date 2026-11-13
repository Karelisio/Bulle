package com.karelisio.bulle.modules.battery

import android.bluetooth.BluetoothClass
import com.karelisio.bulle.modules.alerts.sources.BtKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Règles pures du module Batterie détaillée. */
class BatteryLogicTest {

    private val h = BatteryMath.HOUR_MS
    private val min = 60_000L

    @Test
    fun recordsOnlyChangesAndForgetsOldSamples() {
        var hist = emptyList<LevelSample>()
        hist = BatteryMath.record(hist, LevelSample(0, 80, false))
        hist = BatteryMath.record(hist, LevelSample(10 * min, 80, false))
        assertEquals(1, hist.size)
        hist = BatteryMath.record(hist, LevelSample(20 * min, 79, false))
        hist = BatteryMath.record(hist, LevelSample(30 * min, 79, true))
        assertEquals(3, hist.size)
        hist = BatteryMath.record(hist, LevelSample(13 * h, 90, true))
        assertEquals(listOf(LevelSample(13 * h, 90, true)), hist)
    }

    @Test
    fun dischargeEstimateFromSlope() {
        // 10 % par heure depuis 2 h : à 60 %, 6 h restantes.
        val samples = (0..20).map { LevelSample(it * 6 * min, 80 - it, false) }
        val now = 120 * min
        val ms = BatteryMath.dischargeRemainingMs(samples, 60, now)
        assertNotNull(ms)
        assertEquals(6.0, ms!! / h.toDouble(), 0.05)
    }

    @Test
    fun dischargeEstimateNeedsEnoughData() {
        assertNull(BatteryMath.dischargeRemainingMs(listOf(LevelSample(0, 80, false)), 80, 0))
        // Moins de 15 min couvertes.
        assertNull(BatteryMath.dischargeRemainingMs(listOf(LevelSample(0, 80, false), LevelSample(5 * min, 78, false)), 78, 5 * min))
        // Un seul % perdu.
        assertNull(BatteryMath.dischargeRemainingMs(listOf(LevelSample(0, 80, false), LevelSample(60 * min, 79, false)), 79, 60 * min))
        // Le segment s'arrête au dernier débranchement : la charge précédente est ignorée.
        val mixed = listOf(LevelSample(0, 50, true), LevelSample(60 * min, 90, true), LevelSample(61 * min, 90, false), LevelSample(65 * min, 89, false))
        assertNull(BatteryMath.dischargeRemainingMs(mixed, 89, 65 * min))
    }

    @Test
    fun chargeEstimatePrefersSystemThenSlope() {
        assertEquals(0L, BatteryMath.chargeRemainingMs(null, emptyList(), 100, 0))
        assertEquals(42 * min, BatteryMath.chargeRemainingMs(42 * min, emptyList(), 50, 0))
        // +30 % par heure : de 70 à 100 → 1 h.
        val samples = (0..6).map { LevelSample(it * 4 * min, 58 + 2 * it, true) }
        val ms = BatteryMath.chargeRemainingMs(-1, samples, 70, 24 * min)
        assertEquals(1.0, ms!! / h.toDouble(), 0.05)
        assertNull(BatteryMath.chargeRemainingMs(null, listOf(LevelSample(0, 50, true)), 50, 0))
    }

    @Test
    fun slopeOfFlatSeriesIsZero() {
        assertEquals(0.0, BatteryMath.slopePerHour(listOf(LevelSample(0, 50, false), LevelSample(h, 50, false)))!!, 1e-9)
        assertNull(BatteryMath.slopePerHour(listOf(LevelSample(0, 50, false), LevelSample(0, 49, false))))
    }

    @Test
    fun normalizesCurrentUnitAndSign() {
        // µA documentés, signe positif en charge.
        assertEquals(2_000_000L, BatteryMath.normalizeCurrentMicroAmps(2_000_000, charging = true))
        // Appareil qui donne un signe négatif en charge.
        assertEquals(2_000_000L, BatteryMath.normalizeCurrentMicroAmps(-2_000_000, charging = true))
        // Appareil en mA.
        assertEquals(-450_000L, BatteryMath.normalizeCurrentMicroAmps(450, charging = false))
        assertEquals(1_500_000L, BatteryMath.normalizeCurrentMicroAmps(-1500, charging = true))
        assertNull(BatteryMath.normalizeCurrentMicroAmps(0, charging = true))
        assertNull(BatteryMath.normalizeCurrentMicroAmps(Int.MIN_VALUE.toLong(), charging = true))
        assertNull(BatteryMath.normalizeCurrentMicroAmps(90_000_000, charging = true))
    }

    @Test
    fun normalizesVoltageTemperatureAndPower() {
        assertEquals(4_200, BatteryMath.normalizeVoltageMillivolts(4_200))
        assertEquals(4_000, BatteryMath.normalizeVoltageMillivolts(4))
        assertEquals(4_200, BatteryMath.normalizeVoltageMillivolts(4_200_000))
        assertNull(BatteryMath.normalizeVoltageMillivolts(0))
        assertEquals(31.5, BatteryMath.temperatureCelsius(315)!!, 1e-9)
        assertNull(BatteryMath.temperatureCelsius(Int.MIN_VALUE))
        assertEquals(10.0, BatteryMath.powerWatts(-2_500_000, 4_000)!!, 1e-9)
        assertNull(BatteryMath.powerWatts(null, 4_000))
    }

    @Test
    fun hoursMinutesRoundsToMinute() {
        assertEquals(1L to 5L, BatteryMath.hoursMinutes(65 * min + 20_000))
        assertEquals(0L to 45L, BatteryMath.hoursMinutes(44 * min + 40_000))
    }

    @Test
    fun parsesBluetoothLevel() {
        assertEquals(80, BtAccessories.parseLevel(80))
        assertEquals(0, BtAccessories.parseLevel(0))
        assertNull(BtAccessories.parseLevel(-1))
        assertNull(BtAccessories.parseLevel(101))
        assertNull(BtAccessories.parseLevel(null))
        assertNull(BtAccessories.parseLevel("80"))
    }

    @Test
    fun accessoryListUpdates() {
        val watch = BtAccessory("AA", "Montre", BtKind.WATCH, 60)
        val buds = BtAccessory("BB", "Écouteurs", BtKind.HEADPHONES, null)
        var list = BtAccessories.upsert(emptyList(), watch)
        list = BtAccessories.upsert(list, buds)
        assertEquals(listOf("Écouteurs", "Montre"), list.map { it.name })
        // Reconnexion sans niveau connu : on garde l'ancien.
        list = BtAccessories.upsert(list, watch.copy(level = null))
        assertEquals(60, list.first { it.address == "AA" }.level)
        list = BtAccessories.withLevel(list, "BB", 40)
        assertEquals(40, list.first { it.address == "BB" }.level)
        list = BtAccessories.withLevel(list, "BB", null)
        assertEquals(40, list.first { it.address == "BB" }.level)
        list = BtAccessories.remove(list, "AA")
        assertEquals(listOf("BB"), list.map { it.address })
    }

    @Test
    fun accessoryKindFromClass() {
        assertEquals(BtKind.WATCH, BtAccessories.kindOf(BluetoothClass.Device.Major.WEARABLE, null))
        assertEquals(BtKind.HEADPHONES, BtAccessories.kindOf(BluetoothClass.Device.Major.AUDIO_VIDEO, 0))
        assertEquals(BtKind.CAR, BtAccessories.kindOf(BluetoothClass.Device.Major.AUDIO_VIDEO, BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO))
        assertNull(BtAccessories.kindOf(BluetoothClass.Device.Major.COMPUTER, null))
        assertEquals(BtKind.OTHER, BtAccessories.kindOf(null, null))
    }
}
