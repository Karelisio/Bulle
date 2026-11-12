package com.karelisio.bulle.modules.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalRulesTest {

    // --- Regroupement du volume -------------------------------------------------------------

    @Test
    fun volumeBurstGroupsChangesInsideWindow() {
        val a = VolumeBurst.next(null, 5, 15, now = 1_000, windowMs = 2_500)
        val b = VolumeBurst.next(a, 6, 15, now = 1_200, windowMs = 2_500)
        val c = VolumeBurst.next(b, 7, 15, now = 3_600, windowMs = 2_500)
        // Même rafale : même identité (clé), niveau et instant mis à jour.
        assertEquals(1_000L, b.startedAt)
        assertEquals(6, b.level)
        assertEquals(1_200L, b.lastAt)
        // Fenêtre comptée depuis le DERNIER changement : une touche maintenue reste une seule alerte.
        assertEquals(1_000L, c.startedAt)
        assertEquals(7, c.level)
    }

    @Test
    fun volumeBurstStartsAgainAfterSilence() {
        val a = VolumeBurst.next(null, 5, 15, now = 1_000, windowMs = 2_500)
        val b = VolumeBurst.next(a, 4, 15, now = 3_501, windowMs = 2_500)
        assertEquals(3_501L, b.startedAt)
        assertEquals(4, b.level)
        // Horloge qui recule (redémarrage) : nouvelle rafale plutôt qu'une durée négative.
        assertEquals(500L, VolumeBurst.next(a, 4, 15, now = 500, windowMs = 2_500).startedAt)
    }

    @Test
    fun volumePercent() {
        assertEquals(60, VolumeBurst(9, 15, 0, 0).percent)
        assertEquals(0, VolumeBurst(3, 0, 0, 0).percent)
        assertEquals(100, VolumeBurst(15, 15, 0, 0).percent)
    }

    // --- Réseau mobile ------------------------------------------------------------------------

    @Test
    fun cellTransitions() {
        assertNull(SignalRules.cellTransition(null, CellState.NO_SERVICE)) // état initial
        assertEquals(CellEvent.LOST, SignalRules.cellTransition(CellState.IN_SERVICE, CellState.NO_SERVICE))
        assertEquals(CellEvent.BACK, SignalRules.cellTransition(CellState.NO_SERVICE, CellState.IN_SERVICE))
        assertNull(SignalRules.cellTransition(CellState.IN_SERVICE, CellState.IN_SERVICE))
        // Mode avion : ni perte ni retour (alerte « Mode avion » dédiée).
        assertNull(SignalRules.cellTransition(CellState.IN_SERVICE, CellState.OFF))
        assertNull(SignalRules.cellTransition(CellState.OFF, CellState.IN_SERVICE))
        assertNull(SignalRules.cellTransition(CellState.OFF, CellState.NO_SERVICE))
    }

    @Test
    fun cellStateMapping() {
        assertEquals(CellState.IN_SERVICE, SignalRules.cellState(0))
        assertEquals(CellState.NO_SERVICE, SignalRules.cellState(1))
        assertEquals(CellState.NO_SERVICE, SignalRules.cellState(2))
        assertEquals(CellState.OFF, SignalRules.cellState(3))
    }

    @Test
    fun fiveGDetection() {
        assertTrue(SignalRules.is5g(networkType = 20, overrideType = 0)) // NR autonome
        assertTrue(SignalRules.is5g(networkType = 13, overrideType = 3)) // LTE + NR (NSA)
        assertTrue(SignalRules.is5g(networkType = 13, overrideType = 5)) // 5G+
        assertFalse(SignalRules.is5g(networkType = 13, overrideType = 2)) // LTE avancée
        assertFalse(SignalRules.became5g(null, true)) // état initial
        assertTrue(SignalRules.became5g(false, true))
        assertFalse(SignalRules.became5g(true, true))
        assertFalse(SignalRules.became5g(true, false))
    }

    // --- VPN ----------------------------------------------------------------------------------

    @Test
    fun vpnInitialNetworkIsSilentButItsLossIsAnnounced() {
        val t = VpnTracker(graceMs = 1_500)
        assertFalse(t.onAvailable("100", sinceRegisterMs = 10))
        assertTrue(t.onLost("100"))
    }

    @Test
    fun vpnAnnouncesFirstConnectAndLastDisconnect() {
        val t = VpnTracker(graceMs = 1_500)
        assertTrue(t.onAvailable("101", sinceRegisterMs = 5_000))
        assertFalse(t.onAvailable("101", sinceRegisterMs = 5_100)) // doublon
        assertFalse(t.onAvailable("102", sinceRegisterMs = 6_000)) // déjà connecté
        assertFalse(t.onLost("101")) // il en reste un
        assertTrue(t.onLost("102"))
        assertFalse(t.onLost("999")) // inconnu
    }

    // --- Point d'accès ------------------------------------------------------------------------

    @Test
    fun hotspotIgnoresInitialStickyAndTransients() {
        assertEquals(true to null, SignalRules.hotspot(null, 13, initial = true))
        assertEquals(null to null, SignalRules.hotspot(null, 12, initial = false)) // en cours d'activation
        assertEquals(true to true, SignalRules.hotspot(false, 13, initial = false))
        assertEquals(true to null, SignalRules.hotspot(true, 13, initial = false))
        assertEquals(false to false, SignalRules.hotspot(true, 11, initial = false))
        // Sans diffusion initiale : jamais allumé depuis le démarrage, donc considéré éteint.
        assertEquals(true to true, SignalRules.hotspot(null, 13, initial = false))
        assertEquals(false to null, SignalRules.hotspot(null, 11, initial = false))
    }

    // --- Son et USB ---------------------------------------------------------------------------

    @Test
    fun ringerKinds() {
        assertEquals(RingerKind.SILENT, SignalRules.ringerKind(0))
        assertEquals(RingerKind.VIBRATE, SignalRules.ringerKind(1))
        assertEquals(RingerKind.NORMAL, SignalRules.ringerKind(2))
    }

    @Test
    fun usbModesAndTransitions() {
        assertEquals(UsbMode.FILES, SignalRules.usbMode { it == "mtp" || it == "adb" })
        assertEquals(UsbMode.PHOTOS, SignalRules.usbMode { it == "ptp" })
        assertEquals(UsbMode.TETHERING, SignalRules.usbMode { it == "ncm" })
        assertEquals(UsbMode.CHARGE_ONLY, SignalRules.usbMode { false })
        assertNull(SignalRules.usbTransition(null, true))
        assertEquals(true, SignalRules.usbTransition(false, true))
        assertEquals(false, SignalRules.usbTransition(true, false))
        assertNull(SignalRules.usbTransition(true, true))
    }
}
