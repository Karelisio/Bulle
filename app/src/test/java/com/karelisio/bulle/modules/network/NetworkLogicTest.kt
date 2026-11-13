package com.karelisio.bulle.modules.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

/** Règles pures du module Réseau : formatage, lissage, hystérésis, cumul journalier. */
class NetworkLogicTest {

    private val fr = Locale.FRANCE
    private val bytes = RateFormat.FR_BYTES_PER_S
    private val bits = RateFormat.FR_BITS_PER_S

    @Test
    fun formatsRatesReadably() {
        assertEquals("0 o/s", RateFormat.rate(0.0, RateUnit.BYTES, bytes, fr))
        assertEquals("512 o/s", RateFormat.rate(512.0, RateUnit.BYTES, bytes, fr))
        assertEquals("1,2 Ko/s", RateFormat.rate(1_200.0, RateUnit.BYTES, bytes, fr))
        assertEquals("12 Ko/s", RateFormat.rate(12_400.0, RateUnit.BYTES, bytes, fr))
        assertEquals("3,4 Mo/s", RateFormat.rate(3_400_000.0, RateUnit.BYTES, bytes, fr))
        assertEquals("1,0 Ko/s", RateFormat.rate(999.7, RateUnit.BYTES, bytes, fr))
        assertEquals("0 o/s", RateFormat.rate(-5.0, RateUnit.BYTES, bytes, fr))
        assertEquals("8,0 Mbit/s", RateFormat.rate(1_000_000.0, RateUnit.BITS, bits, fr))
        assertEquals("2.5 MB/s", RateFormat.rate(2_500_000.0, RateUnit.BYTES, listOf("B/s", "kB/s", "MB/s", "GB/s"), Locale.US))
        assertEquals("1,5 Go", RateFormat.amount(1_500_000_000L, RateFormat.FR_BYTES, fr))
        assertEquals("340 Mo", RateFormat.amount(340_000_000L, RateFormat.FR_BYTES, fr))
    }

    @Test
    fun rateBetweenIgnoresResetsAndZeroInterval() {
        assertEquals(1000.0, rateBetween(0, 0, 2000, 2000)!!, 0.001)
        assertEquals(0.0, rateBetween(5000, 0, 100, 1000)!!, 0.001)
        assertEquals(0.0, rateBetween(-1, 0, 100, 1000)!!, 0.001)
        assertNull(rateBetween(0, 1000, 100, 1000))
    }

    @Test
    fun smootherFollowsAndSnapsToZero() {
        val s = RateSmoother(alpha = 0.5)
        assertEquals(1000.0, s.push(1000.0), 0.001) // premier échantillon pris tel quel
        assertEquals(1500.0, s.push(2000.0), 0.001)
        assertEquals(750.0, s.push(0.0), 0.001)
        repeat(20) { s.push(0.0) }
        assertEquals(0.0, s.value, 0.0)
    }

    @Test
    fun historyKeepsSixtyPoints() {
        var h = emptyList<RatePoint>()
        repeat(75) { h = h.pushPoint(RatePoint(it.toDouble(), 0.0)) }
        assertEquals(60, h.size)
        assertEquals(74.0, h.last().rx, 0.0)
        assertEquals(15.0, h.first().rx, 0.0)
    }

    @Test
    fun gateShowsAfterThreeSecondsAndHidesAfterFive() {
        val g = HeavyTrafficGate(500_000.0)
        assertFalse(g.update(600_000.0, 0))
        assertFalse(g.update(600_000.0, 2_000))
        assertTrue(g.update(600_000.0, 3_000))
        // Un creux bref ne masque pas.
        assertTrue(g.update(100_000.0, 4_000))
        assertTrue(g.update(700_000.0, 5_000))
        assertTrue(g.update(100_000.0, 6_000))
        assertTrue(g.update(100_000.0, 10_000))
        assertFalse(g.update(100_000.0, 11_000))
        // Une pointe isolée sous 3 s ne déclenche rien.
        assertFalse(g.update(900_000.0, 12_000))
        assertFalse(g.update(100_000.0, 14_000))
        assertFalse(g.update(900_000.0, 15_000))
        assertFalse(g.update(900_000.0, 17_000))
    }

    @Test
    fun gateStaysWhilePinned() {
        val g = HeavyTrafficGate(1_000.0)
        g.update(2_000.0, 0)
        assertTrue(g.update(2_000.0, 3_000))
        assertTrue(g.update(0.0, 4_000, pinned = true))
        assertTrue(g.update(0.0, 20_000, pinned = true))
        assertFalse(g.update(0.0, 20_001, pinned = false))
    }

    @Test
    fun connectionKindFromTransports() {
        assertEquals(ConnectionInfo(NetKind.WIFI, false), ConnectionInfo.fromTransports(wifi = true, cellular = false, ethernet = false, vpn = false))
        assertEquals(ConnectionInfo(NetKind.MOBILE, true), ConnectionInfo.fromTransports(wifi = false, cellular = true, ethernet = false, vpn = true))
        assertEquals(ConnectionInfo(NetKind.NONE, true), ConnectionInfo.fromTransports(wifi = false, cellular = false, ethernet = false, vpn = true))
        assertEquals(NetKind.ETHERNET, ConnectionInfo.fromTransports(wifi = false, cellular = false, ethernet = true, vpn = false).kind)
    }

    // --- Cumul journalier ---

    private val zone = ZoneId.of("Europe/Paris")
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m).atZone(zone).toInstant().toEpochMilli()
    private fun reading(total: Long, mobile: Long, time: Long, kind: NetKind = NetKind.WIFI, boot: Int = 7) =
        TrafficReading(total, mobile, boot, time, kind)

    @Test
    fun firstReadingStartsPartialDay() {
        val s = DailyUsage.accumulate(UsageState(), reading(10_000, 2_000, at(13, 14)), zone)
        assertEquals(0L, s.totalBytes)
        assertTrue(s.partial)
        assertEquals(at(13, 14), s.since)
    }

    @Test
    fun splitsMobileAndWifiByCounters() {
        var s = DailyUsage.accumulate(UsageState(), reading(10_000, 2_000, at(13, 14)), zone)
        s = DailyUsage.accumulate(s, reading(15_000, 3_000, at(13, 15)), zone)
        assertEquals(1_000L, s.mobileBytes)
        assertEquals(4_000L, s.wifiBytes)
    }

    @Test
    fun withoutMobileCountersUsesNetworkTypeOfInterval() {
        var s = DailyUsage.accumulate(UsageState(), reading(1_000, -1, at(13, 9), kind = NetKind.MOBILE), zone)
        s = DailyUsage.accumulate(s, reading(4_000, -1, at(13, 10), kind = NetKind.WIFI), zone)
        assertEquals(3_000L, s.mobileBytes) // l'intervalle écoulé était en mobile
        s = DailyUsage.accumulate(s, reading(6_000, -1, at(13, 11), kind = NetKind.WIFI), zone)
        assertEquals(2_000L, s.wifiBytes)
    }

    @Test
    fun negativeDeltaAfterRebootIsIgnored() {
        var s = DailyUsage.accumulate(UsageState(), reading(50_000, 0, at(13, 9)), zone)
        s = DailyUsage.accumulate(s, reading(60_000, 0, at(13, 10)), zone)
        assertEquals(10_000L, s.wifiBytes)
        // Redémarrage : compteurs repartis de zéro.
        s = DailyUsage.accumulate(s, reading(1_000, 0, at(13, 11)), zone)
        assertEquals(10_000L, s.wifiBytes)
        s = DailyUsage.accumulate(s, reading(3_000, 0, at(13, 12)), zone)
        assertEquals(12_000L, s.wifiBytes)
        // Nouveau numéro de démarrage même si le compteur a dépassé l'ancienne valeur : ignoré.
        s = DailyUsage.accumulate(s, reading(900_000, 0, at(13, 13), boot = 8), zone)
        assertEquals(12_000L, s.wifiBytes)
    }

    @Test
    fun midnightResetsAndKeepsShareAfterMidnight() {
        var s = DailyUsage.accumulate(UsageState(), reading(0, 0, at(12, 20)), zone)
        s = DailyUsage.accumulate(s, reading(5_000, 0, at(12, 23)), zone)
        assertEquals(5_000L, s.wifiBytes)
        // 23:00 → 01:00 : la moitié de l'intervalle est après minuit.
        s = DailyUsage.accumulate(s, reading(9_000, 0, at(13, 1)), zone)
        assertEquals(DailyUsage.epochDay(at(13, 1), zone), s.day)
        assertEquals(2_000L, s.wifiBytes)
        assertFalse(s.partial) // suivi continu depuis la veille
    }

    @Test
    fun gapOfSeveralDaysMarksPartial() {
        var s = DailyUsage.accumulate(UsageState(), reading(0, 0, at(10, 20)), zone)
        s = DailyUsage.accumulate(s, reading(1_000, 0, at(13, 8)), zone)
        assertTrue(s.partial)
        assertEquals(at(13, 8), s.since)
    }

    @Test
    fun todayHidesStaleDay() {
        val s = DailyUsage.accumulate(UsageState(), reading(0, 0, at(12, 20)), zone).copy(wifiBytes = 99)
        assertEquals(0L, DailyUsage.today(s, at(13, 9), zone).totalBytes)
        assertEquals(99L, DailyUsage.today(s, at(12, 22), zone).totalBytes)
    }

    @Test
    fun manualResetRestartsFromCurrentCounters() {
        val r = reading(42_000, 1_000, at(13, 16))
        val s = DailyUsage.fresh(DailyUsage.epochDay(r.atMillis, zone), r)
        assertEquals(0L, s.totalBytes)
        assertTrue(s.partial)
        val next = DailyUsage.accumulate(s, reading(43_000, 1_000, at(13, 17)), zone)
        assertEquals(1_000L, next.wifiBytes)
    }
}
