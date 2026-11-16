package com.karelisio.bulle.modules.volet.qs

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tuile « Point d'accès » : vraie bascule avec Shizuku, sinon écran du point d'accès (pas les Paramètres généraux). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HotspotTileTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun shizukuCommandRoundTrip() {
        assertEquals("bulle-tether wifi on", HotspotTile.shizukuCommand(true))
        assertEquals("bulle-tether wifi off", HotspotTile.shizukuCommand(false))
        assertEquals(true, TetherShell.parse(HotspotTile.shizukuCommand(true)!!))
        assertEquals(false, TetherShell.parse(HotspotTile.shizukuCommand(false)!!))
        // Les autres commandes passent au shell normalement.
        assertNull(TetherShell.parse("svc wifi enable"))
        assertNull(TetherShell.parse("bulle-tether usb on"))
        assertNull(TetherShell.parse("bulle-tether wifi maybe"))
    }

    @Test
    fun tetheringArgumentsFollowTheSignature() {
        // Android 12+ : startTethering(requête, paquet, attribution, écouteur).
        assertEquals(
            listOf(TetherShell.Arg.REQUEST, TetherShell.Arg.PACKAGE, TetherShell.Arg.TAG, TetherShell.Arg.LISTENER),
            TetherShell.plan(listOf("android.net.TetheringRequestParcel", "java.lang.String", "java.lang.String", "android.net.IIntResultListener")),
        )
        // Android 11 : startTethering(requête, paquet, écouteur).
        assertEquals(
            listOf(TetherShell.Arg.REQUEST, TetherShell.Arg.PACKAGE, TetherShell.Arg.LISTENER),
            TetherShell.plan(listOf("android.net.TetheringRequestParcel", "java.lang.String", "android.net.IIntResultListener")),
        )
        // stopTethering(type, paquet, attribution, écouteur).
        assertEquals(
            listOf(TetherShell.Arg.TYPE, TetherShell.Arg.PACKAGE, TetherShell.Arg.TAG, TetherShell.Arg.LISTENER),
            TetherShell.plan(listOf("int", "java.lang.String", "java.lang.String", "android.net.IIntResultListener")),
        )
    }

    @Test
    fun withShizukuTheTileTogglesForReal() {
        assertEquals(ToggleMethod.SHIZUKU, ToggleChooser.choose(HotspotTile, context, ShizukuStatus.READY))
        assertEquals(ToggleMethod.SHIZUKU, ToggleChooser.choose(HotspotTile, context, ShizukuStatus.NEEDS_PERMISSION))
        assertEquals(ToggleMethod.SETTINGS, ToggleChooser.choose(HotspotTile, context, ShizukuStatus.UNAVAILABLE))
    }

    @Test
    fun withoutShizukuTheHotspotScreenOpens() {
        val intent = HotspotTile.settingsIntent(context)
        assertNotEquals(Settings.ACTION_SETTINGS, intent.action)
        val candidates = HotspotIntents.candidates()
        assertEquals("com.android.settings.WIFI_TETHER_SETTINGS", candidates.first().action)
        assertFalse(candidates.any { it.action == Settings.ACTION_SETTINGS })
        // Premier candidat ouvrable retenu ; aucun : réseaux sans fil (jamais les Paramètres généraux).
        val tether = HotspotIntents.pick(candidates) { it.action == "android.settings.TETHER_SETTINGS" }
        assertEquals("android.settings.TETHER_SETTINGS", tether.action)
        val magic = HotspotIntents.pick(candidates) { it.component?.className?.endsWith("WifiApSettingsActivity") == true }
        assertEquals("com.android.settings", magic.component?.packageName)
        assertEquals(Settings.ACTION_WIRELESS_SETTINGS, HotspotIntents.pick(candidates) { false }.action)
        assertTrue(candidates.all { it.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0 })
    }

    @Test
    fun apStateIsReadFromTheBroadcast() {
        assertTrue(HotspotIntents.isActive(13))
        assertTrue(HotspotIntents.isActive(12))
        assertFalse(HotspotIntents.isActive(11))
        assertFalse(HotspotIntents.isActive(14))
    }
}
