package com.karelisio.bulle.modules.volet.notifications

import androidx.compose.ui.unit.dp
import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.modules.volet.VoletCardDensity
import com.karelisio.bulle.modules.volet.VoletDensity
import com.karelisio.bulle.modules.volet.VoletSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dimensions des cartes selon densité / échelle, et compatibilité des réglages. */
class NotifDimsTest {

    @Test
    fun defaultsMatchHistoricalLook() {
        val d = NotifDims.of(VoletSettings())
        assertEquals(VoletCardDensity.NORMAL, d.density)
        assertEquals(16.dp, d.pad)
        assertEquals(10.dp, d.gap)
        assertEquals(38.dp, d.badge)
        assertEquals(44.dp, d.largeIcon)
        assertEquals(12.dp, d.iconGap)
        assertEquals(28.dp, d.corner)
        assertEquals(1f, d.textScale)
        assertEquals(2, d.collapsedLines)
        assertFalse(d.compact)
    }

    @Test
    fun compactIsClearlySmaller() {
        val n = NotifDims.of(VoletCardDensity.NORMAL, 28f)
        val c = NotifDims.of(VoletCardDensity.COMPACT, 28f)
        assertTrue(c.compact)
        // Marges verticales + pastille : au moins 30 % de moins.
        val normal = n.pad.value * 2 + n.badge.value
        val compact = c.pad.value * 2 + c.badge.value
        assertTrue("compacte = $compact / normale = $normal", compact <= normal * 0.7f)
        assertTrue(c.gap < n.gap && c.corner < n.corner && c.sectionTop < n.sectionTop)
    }

    @Test
    fun airyIsLarger() {
        val n = NotifDims.of(VoletCardDensity.NORMAL, 28f)
        val a = NotifDims.of(VoletCardDensity.AIRY, 28f)
        assertTrue(a.pad > n.pad && a.gap > n.gap && a.badge > n.badge)
    }

    @Test
    fun scaleAndLinesAreClamped() {
        val big = NotifDims.of(VoletCardDensity.NORMAL, 20f, textScale = 3f, collapsedLines = 9)
        assertEquals(1.2f, big.textScale)
        assertEquals(4, big.collapsedLines)
        // Les icônes suivent la taille du texte, pas les marges.
        assertEquals((38 * 1.2f).dp, big.badge)
        assertEquals(16.dp, big.pad)
        val small = NotifDims.of(VoletCardDensity.NORMAL, -5f, textScale = 0.1f, collapsedLines = 0)
        assertEquals(0.8f, small.textScale)
        assertEquals(1, small.collapsedLines)
        assertEquals(0.dp, small.corner)
        assertEquals(1f, NotifDims.of(VoletCardDensity.NORMAL, 20f, textScale = Float.NaN).textScale)
    }

    @Test
    fun legacyCompactDensityKeepsCompactCards() {
        assertEquals(VoletCardDensity.COMPACT, VoletSettings(density = VoletDensity.COMPACT).effectiveCardDensity)
        assertEquals(VoletCardDensity.NORMAL, VoletSettings().effectiveCardDensity)
        // Un choix explicite l'emporte.
        assertEquals(VoletCardDensity.AIRY, VoletSettings(density = VoletDensity.COMPACT, cardDensity = VoletCardDensity.AIRY).effectiveCardDensity)
    }

    @Test
    fun settingsSerializationRoundTripAndOldJson() {
        val s = VoletSettings(cardDensity = VoletCardDensity.COMPACT, cardTextScale = 0.9f, cardCollapsedLines = 3, cornerRadiusDp = 18f)
        val json = BulleJson.encodeToString(VoletSettings.serializer(), s)
        assertEquals(s, BulleJson.decodeFromString(VoletSettings.serializer(), json))
        // Réglages enregistrés par une version précédente : valeurs par défaut (rendu inchangé).
        val old = BulleJson.decodeFromString(VoletSettings.serializer(), """{"enabled":true,"density":"COMPACT"}""")
        assertNull(old.cardDensity)
        assertEquals(1f, old.cardTextScale)
        assertEquals(2, old.cardCollapsedLines)
        // Valeur inconnue (version plus récente) : retour au défaut sans tout réinitialiser.
        val future = BulleJson.decodeFromString(VoletSettings.serializer(), """{"cardDensity":"HUGE","cardCollapsedLines":3}""")
        assertNull(future.cardDensity)
        assertEquals(3, future.cardCollapsedLines)
    }

    @Test
    fun conversationLayoutShowsSendersOnlyWhenTheyDiffer() {
        fun msg(sender: String?, self: Boolean = false) = NotifMessage(sender, "t", 0L, null, self)
        val oneToOne = sampleNotifItem("k", "App", "Alice", "t", null, messages = listOf(msg("Alice"), msg("Moi", true), msg("Alice")))
        assertFalse(ConversationLayout.showSenders(oneToOne))
        val group = oneToOne.copy(messages = listOf(msg("Alice"), msg("Bob")), isGroupConversation = true)
        assertTrue(ConversationLayout.showSenders(group))
        // Groupe où une seule personne a écrit : pas d'avatar par message.
        assertFalse(ConversationLayout.showSenders(group.copy(messages = listOf(msg("Alice"), msg("Alice")))))
        assertEquals("Alice", ConversationLayout.headName(oneToOne).toString())
    }
}
