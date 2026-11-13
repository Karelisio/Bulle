package com.karelisio.bulle.modules.external

import com.karelisio.bulle.modules.external.ExternalApi as Api
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Analyse des extras de l'API d'intégration : validation, bornes, replis, clé par appli. */
class ExternalParserTest {
    private val now = 1_700_000_000_000L
    private val elapsed = 50_000L
    private val day = 24 * 60 * 60 * 1000L

    private fun parse(
        vararg extras: Pair<String, Any?>,
        action: String? = Api.ACTION_SHOW,
        caller: String = "com.example.a",
        preset: ExternalPreset? = null,
    ): ExternalCommand {
        val map = extras.toMap()
        return ExternalParser.parse(action, { map[it] }, caller, now, elapsed, preset)
    }

    private fun show(vararg extras: Pair<String, Any?>, preset: ExternalPreset? = null): ExternalSpec {
        val c = parse(Api.EXTRA_ID to "x", *extras, preset = preset)
        assertTrue("attendu Show, obtenu $c", c is ExternalCommand.Show)
        return (c as ExternalCommand.Show).spec
    }

    @Test
    fun idIsRequiredAndValidated() {
        assertTrue(parse() is ExternalCommand.Invalid)
        assertTrue(parse(Api.EXTRA_ID to "") is ExternalCommand.Invalid)
        assertTrue(parse(Api.EXTRA_ID to "a b") is ExternalCommand.Invalid)
        assertTrue(parse(Api.EXTRA_ID to "x".repeat(65)) is ExternalCommand.Invalid)
        assertTrue(parse(Api.EXTRA_ID to 42) is ExternalCommand.Invalid)
        assertTrue(parse(Api.EXTRA_ID to "ok", action = "com.evil.ACTION") is ExternalCommand.Invalid)
        assertTrue(parse(Api.EXTRA_ID to "ok", action = null) is ExternalCommand.Invalid)
        assertTrue(parse(Api.EXTRA_ID to " vps-paris:web_1.2 ") is ExternalCommand.Show)
    }

    @Test
    fun keyIsCallerPlusId() {
        val a = parse(Api.EXTRA_ID to "same", caller = "com.example.a") as ExternalCommand.Show
        val b = parse(Api.EXTRA_ID to "same", caller = "com.example.b") as ExternalCommand.Show
        assertEquals("com.example.a/same", a.spec.key)
        assertEquals("com.example.b/same", b.spec.key)
        // UPDATE = SHOW ; DISMISS ne vise que les contenus de l'appelant.
        assertTrue(parse(Api.EXTRA_ID to "same", action = Api.ACTION_UPDATE) is ExternalCommand.Show)
        val d = parse(Api.EXTRA_ID to "same", action = Api.ACTION_DISMISS, caller = "com.example.b")
        assertEquals("com.example.b/same", (d as ExternalCommand.Dismiss).key)
    }

    @Test
    fun textsAreCleanedAndTruncated() {
        val s = show(
            Api.EXTRA_TITLE to "  Bonjour\n\tle   monde\u0007 ",
            Api.EXTRA_TEXT to "a".repeat(500),
            Api.EXTRA_SUBTITLE to "   ",
        )
        assertEquals("Bonjour le monde", s.title)
        assertEquals(Api.MAX_TEXT_LENGTH, s.text!!.length)
        assertTrue(s.text!!.endsWith("…"))
        assertNull(s.subtitle)
        assertEquals(ExternalTemplate.STANDARD, s.template)
    }

    @Test
    fun priorityIsBoundedAndDerived() {
        assertEquals(ExternalPriority.LIVE, show().priority)
        assertEquals(ExternalPriority.LIVE, show(Api.EXTRA_PRIORITY to "call").priority)
        assertEquals(ExternalPriority.LIVE, show(Api.EXTRA_PRIORITY to "alarm").priority)
        assertEquals(ExternalPriority.MEDIA, show(Api.EXTRA_PRIORITY to "MEDIA").priority)
        assertEquals(ExternalPriority.ALERT, show(Api.EXTRA_TEMPLATE to "heart").priority)
        // Un service en panne est toujours une alerte, même si l'appli demande « media ».
        val down = show(Api.EXTRA_TEMPLATE to "status", Api.EXTRA_STATUS to "down", Api.EXTRA_PRIORITY to "media")
        assertEquals(ExternalPriority.ALERT, down.priority)
        assertEquals(ExternalStatus.DOWN, down.status)
        assertEquals(ExternalStatus.OK, show(Api.EXTRA_TEMPLATE to "status").status)
        assertEquals(ExternalStatus.OK, show(Api.EXTRA_TEMPLATE to "status", Api.EXTRA_STATUS to "??").status)
        assertNull(show(Api.EXTRA_STATUS to "down").status)
    }

    @Test
    fun timeoutIsBounded() {
        assertEquals(Api.MIN_TIMEOUT_MS, show(Api.EXTRA_TIMEOUT_MS to 10).timeoutMs)
        assertEquals(Api.MAX_TIMEOUT_MS, show(Api.EXTRA_TIMEOUT_MS to Long.MAX_VALUE).timeoutMs)
        assertEquals(5_000L, show(Api.EXTRA_TIMEOUT_MS to "5000").timeoutMs)
        assertEquals(Api.MAX_TIMEOUT_MS, show().timeoutMs)
        assertEquals(Api.DEFAULT_ALERT_TIMEOUT_MS, show(Api.EXTRA_PRIORITY to "alert").timeoutMs)
        // Compte à rebours : disparaît une minute après l'échéance.
        val c = show(Api.EXTRA_TEMPLATE to "countdown", Api.EXTRA_TARGET_TIME to now + 10 * 60_000L)
        assertEquals(11 * 60_000L, c.timeoutMs)
        assertEquals(elapsed + 11 * 60_000L, c.expiresAt)
    }

    @Test
    fun autoExpandAndShowWhenHidden() {
        assertFalse(show().autoExpand)
        assertTrue(show(Api.EXTRA_PRIORITY to "alert").autoExpand)
        assertFalse(show(Api.EXTRA_PRIORITY to "alert", Api.EXTRA_AUTO_EXPAND to false).autoExpand)
        assertTrue(show(Api.EXTRA_AUTO_EXPAND to "true").autoExpand)
        // Visible malgré le masquage : seulement pour une alerte.
        assertFalse(show(Api.EXTRA_SHOW_WHEN_HIDDEN to true).showWhenHidden)
        assertTrue(show(Api.EXTRA_SHOW_WHEN_HIDDEN to true, Api.EXTRA_PRIORITY to "alert").showWhenHidden)
        assertTrue(
            show(Api.EXTRA_SHOW_WHEN_HIDDEN to true, Api.EXTRA_TEMPLATE to "status", Api.EXTRA_STATUS to "down").showWhenHidden,
        )
    }

    @Test
    fun progressIsClamped() {
        val p = show(Api.EXTRA_TEMPLATE to "progress", Api.EXTRA_PROGRESS to 500, Api.EXTRA_MAX to 100)
        assertEquals(ExternalTemplate.PROGRESS, p.template)
        assertEquals(100, p.progress)
        assertEquals(0, show(Api.EXTRA_PROGRESS to -3).progress)
        assertEquals(1, show(Api.EXTRA_MAX to 0).max)
        assertEquals(Int.MAX_VALUE, show(Api.EXTRA_MAX to Long.MAX_VALUE).max)
        assertTrue(show(Api.EXTRA_INDETERMINATE to true).indeterminate)
        assertEquals(42, show(Api.EXTRA_PROGRESS to 42.7).progress)
        assertEquals(0, show(Api.EXTRA_PROGRESS to Double.NaN).progress)
    }

    @Test
    fun timesAreBoundedAndTemplatesFallBack() {
        assertEquals(ExternalTemplate.STANDARD, show(Api.EXTRA_TEMPLATE to "countdown").template)
        assertEquals(
            ExternalTemplate.STANDARD,
            show(Api.EXTRA_TEMPLATE to "countdown", Api.EXTRA_TARGET_TIME to now + 1000 * day).template,
        )
        val c = show(Api.EXTRA_TEMPLATE to "countdown", Api.EXTRA_TARGET_TIME to now + day, Api.EXTRA_START_TIME to now - day)
        assertEquals(ExternalTemplate.COUNTDOWN, c.template)
        assertEquals(now - day, c.startTime)
        // Début après l'échéance : ignoré.
        assertNull(show(Api.EXTRA_TARGET_TIME to now + 1000, Api.EXTRA_START_TIME to now + 30_000).startTime)

        // Chronomètre : des années en arrière acceptées (temps sans cigarette), pas dans le futur.
        val chrono = show(Api.EXTRA_TEMPLATE to "chronometer", Api.EXTRA_START_TIME to now - 5 * 365 * day)
        assertEquals(ExternalTemplate.CHRONOMETER, chrono.template)
        assertEquals(
            ExternalTemplate.STANDARD,
            show(Api.EXTRA_TEMPLATE to "chronometer", Api.EXTRA_START_TIME to now + 10 * 60_000L).template,
        )
        assertEquals(ExternalTemplate.STANDARD, show(Api.EXTRA_TEMPLATE to "nope").template)
    }

    @Test
    fun itemsAreLimited() {
        val texts = Array(12) { "Article $it" }
        texts[1] = "   "
        val l = show(
            Api.EXTRA_TEMPLATE to "list",
            Api.EXTRA_ITEMS to texts,
            Api.EXTRA_ITEMS_CHECKED to booleanArrayOf(true, true, false),
        )
        assertEquals(ExternalTemplate.LIST, l.template)
        assertEquals(Api.MAX_ITEMS, l.items.size)
        assertEquals(ExternalItem("Article 0", checked = true), l.items[0])
        // La ligne vide est retirée ; les coches restent associées à la position d'origine.
        assertEquals(ExternalItem("Article 2", checked = false), l.items[1])
        assertEquals(ExternalTemplate.STANDARD, show(Api.EXTRA_TEMPLATE to "list").template)
        val fromList = show(Api.EXTRA_TEMPLATE to "list", Api.EXTRA_ITEMS to arrayListOf("a", "b".repeat(200)))
        assertEquals(Api.MAX_ITEM_LENGTH, fromList.items[1].text.length)
    }

    @Test
    fun actionsNeedAPendingIntent() {
        val s = show(Api.EXTRA_ACTION_LABELS to arrayOf("Un", "Deux"), Api.EXTRA_ACTION_INTENTS to arrayOf("pas un PI", null))
        assertTrue(s.actions.isEmpty())
        assertNull(show(Api.EXTRA_CONTENT_INTENT to "x").contentIntent)
    }

    @Test
    fun colorsAndPatterns() {
        assertEquals(0xFFFF6FA5.toInt(), show(Api.EXTRA_ACCENT_COLOR to 0xFF6FA5).accent)
        assertEquals(0x80112233.toInt(), show(Api.EXTRA_ACCENT_COLOR to 0x80112233.toInt()).accent)
        assertEquals(0xFF336699.toInt(), show(Api.EXTRA_ACCENT_COLOR to "#336699").accent)
        assertNull(show(Api.EXTRA_ACCENT_COLOR to "rouge").accent)
        assertEquals(BreathPattern.BOX, show(Api.EXTRA_BREATH_PATTERN to "box").breathPattern)
        assertEquals(BreathPattern.RELAX_478, show(Api.EXTRA_BREATH_PATTERN to "zzz").breathPattern)
    }

    @Test
    fun presetAppliesOnlyWhenAppSendsNothing() {
        val preset = ExternalPreset(ExternalTemplate.HEART, 0xFFFF6FA5.toInt(), ExternalPriority.ALERT)
        val p = show(preset = preset)
        assertEquals(ExternalTemplate.HEART, p.template)
        assertEquals(0xFFFF6FA5.toInt(), p.accent)
        assertEquals(ExternalPriority.ALERT, p.priority)
        val explicit = show(Api.EXTRA_TEMPLATE to "standard", Api.EXTRA_ACCENT_COLOR to 0xFF000000.toInt(), Api.EXTRA_PRIORITY to "live", preset = preset)
        assertEquals(ExternalTemplate.STANDARD, explicit.template)
        assertEquals(0xFF000000.toInt(), explicit.accent)
        assertEquals(ExternalPriority.LIVE, explicit.priority)
    }
}
