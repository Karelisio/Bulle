package com.karelisio.bulle.modules.alerts

import com.karelisio.bulle.modules.alerts.sources.BtConnectGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bulle « Bluetooth connecté » : annoncée une fois, pas en boucle sur des liaisons répétées. */
class BtConnectGateTest {
    private val min = 60_000L

    @Test
    fun firstConnectionIsAnnounced() {
        assertTrue(BtConnectGate.allow(lastShown = null, lastDisconnect = null, now = 0L))
    }

    @Test
    fun repeatedConnectionsDoNotLoop() {
        // Déjà annoncé il y a 1 min, liaisons répétées sans vraie déconnexion : rien.
        assertFalse(BtConnectGate.allow(lastShown = 0L, lastDisconnect = null, now = min))
        // Déconnexion/reconnexion éclair (10 s) : rien non plus.
        assertFalse(BtConnectGate.allow(lastShown = 0L, lastDisconnect = 50_000L, now = min))
    }

    @Test
    fun realReconnectionOrLongDelayIsAnnouncedAgain() {
        // Resté déconnecté 3 min : vraie reconnexion.
        assertTrue(BtConnectGate.allow(lastShown = 0L, lastDisconnect = 2 * min, now = 5 * min))
        // 30 min plus tard : à nouveau annoncé.
        assertTrue(BtConnectGate.allow(lastShown = 0L, lastDisconnect = null, now = 30 * min))
    }
}
