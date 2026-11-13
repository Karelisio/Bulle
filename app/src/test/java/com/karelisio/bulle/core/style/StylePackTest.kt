package com.karelisio.bulle.core.style

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.IslandColorMode
import com.karelisio.bulle.core.settings.IslandFont
import com.karelisio.bulle.core.settings.IslandShape
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.core.settings.OpenDirection
import com.karelisio.bulle.core.settings.PositionTweak
import com.karelisio.bulle.modules.alerts.AlertEntrance
import com.karelisio.bulle.modules.alerts.AlertStyle
import com.karelisio.bulle.modules.alerts.ui.StyledAlert
import com.karelisio.bulle.ui.stylepacks.EntryTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeContent(override val moduleId: String) : IslandContent() {
    override val key = "fake:$moduleId"
    override val priority = Priority.ALERT
    override val postedAt: Long = 1

    @Composable
    override fun Leading() = Text("x")
}

class StylePackTest {

    private val sample = StylePack(
        name = "Essai",
        style = PackStyle(
            shape = IslandShape.DROP, size = 1.2f, colorMode = IslandColorMode.CUSTOM, customColor = 0xFF102030,
            outline = true, outlineWidthDp = 2f, font = IslandFont.MONO, textScale = 1.1f, cornerSmoothing = 0.5f,
            maxCompactWidth = 0.6f, maxExpandedWidth = 0.8f,
        ),
        motion = PackMotion(
            style = MotionStyle.PLAYFUL, speed = 1.5f, blurTransitions = false,
            entryAnimations = mapOf("default" to "DROP", "music" to "ELASTIC"),
        ),
    )

    // --- Packs intégrés ---

    @Test
    fun builtInPacksAreValidAndDistinct() {
        val all = BuiltInPacks.all
        assertTrue(all.size in 6..9)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { assertTrue("${it.id} : ${it.pack.problems()}", it.pack.isValid()) }
        // Le pack « Bulle » est exactement l'île d'origine.
        assertTrue(BuiltInPacks.byId("bulle")!!.pack.sameLookAs(StylePack.capture(BulleSettings())))
        // Chaque pack survit à un aller-retour QR.
        all.forEach { assertEquals(it.pack, StyleShare.decode(StyleShare.encodeQr(it.pack))) }
    }

    // --- Sérialisation et validation ---

    @Test
    fun jsonRoundTrip() {
        val json = StyleShare.encodeJson(sample)
        assertTrue(json.contains("\"format\": \"bulle-style\""))
        assertTrue(json.contains("\"version\": 1"))
        assertEquals(sample, StyleShare.decodeJson(json))
        assertEquals(sample, StyleShare.decode("\n  $json  "))
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val json = """{"format":"bulle-style","version":1,"name":"X","future":{"a":1},
            "style":{"size":1.1,"glow":true},"motion":{"speed":0.8,"trail":"long"}}"""
        val pack = StyleShare.decodeJson(json)
        assertNotNull(pack)
        assertEquals(1.1f, pack!!.style.size)
        assertEquals(0.8f, pack.motion.speed)
    }

    @Test
    fun outOfBoundsValuesAreRejected() {
        fun json(style: String = "", motion: String = "", name: String = "X") =
            """{"format":"bulle-style","version":1,"name":"$name","style":{$style},"motion":{$motion}}"""
        assertNotNull(StyleShare.decodeJson(json()))
        assertNull(StyleShare.decodeJson(json(style = "\"size\":5")))
        assertNull(StyleShare.decodeJson(json(style = "\"size\":0.1")))
        assertNull(StyleShare.decodeJson(json(style = "\"textScale\":2")))
        assertNull(StyleShare.decodeJson(json(style = "\"outlineWidthDp\":30")))
        assertNull(StyleShare.decodeJson(json(style = "\"cornerSmoothing\":-1")))
        assertNull(StyleShare.decodeJson(json(style = "\"maxCompactWidth\":0.05")))
        assertNull(StyleShare.decodeJson(json(style = "\"customColor\":-5")))
        assertNull(StyleShare.decodeJson(json(style = "\"customColor\":99999999999")))
        assertNull(StyleShare.decodeJson(json(motion = "\"speed\":9")))
        assertNull(StyleShare.decodeJson(json(name = "x".repeat(StylePack.MAX_NAME_LENGTH + 1))))
        // Types faux et énumérations inconnues : refus (validation stricte).
        assertNull(StyleShare.decodeJson(json(style = "\"size\":\"grand\"")))
        assertNull(StyleShare.decodeJson(json(style = "\"shape\":\"STAR\"")))
        assertNull(StyleShare.decodeJson(json(motion = "\"style\":\"WOBBLY\"")))
        // Clé d'animation mal formée : refus ; animation inconnue (version future) : ignorée.
        assertNull(StyleShare.decodeJson(json(motion = "\"entryAnimations\":{\"Bad Key!\":\"FADE\"}")))
        val future = StyleShare.decodeJson(json(motion = "\"entryAnimations\":{\"music\":\"TELEPORT\",\"call\":\"FADE\"}"))
        assertEquals(mapOf("call" to "FADE"), future!!.motion.entryAnimations)
    }

    @Test
    fun formatAndVersionAreChecked() {
        assertNull(StyleShare.decodeJson("""{"format":"bulle-style","version":2}"""))
        assertNull(StyleShare.decodeJson("""{"format":"bulle-style","version":0}"""))
        assertNull(StyleShare.decodeJson("""{"format":"autre","version":1}"""))
        assertNull(StyleShare.decodeJson("""{"version":1}"""))
        assertNotNull(StyleShare.decodeJson("""{"format":"bulle-style","version":1}"""))
        assertNull(StyleShare.decode("pas un style"))
        assertNull(StyleShare.decode("{ cassé"))
        assertNull(StyleShare.decode(""))
    }

    // --- QR / texte ---

    @Test
    fun qrTextRoundTrip() {
        val qr = StyleShare.encodeQr(sample)
        assertTrue(qr.startsWith("bulle-style:1:"))
        assertTrue(qr.substringAfter("bulle-style:1:").all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(sample, StyleShare.decode(qr))
        // Collé au milieu d'un message partagé.
        assertEquals(sample, StyleShare.decode("Mon style Bulle « Essai » : $qr\nÀ bientôt"))
        // Assez court pour un QR confortable à scanner.
        assertTrue("longueur ${qr.length}", qr.length < 600)
        val matrix = QrCode.encode(qr)
        assertNotNull(matrix)
        assertTrue(matrix!!.size >= 21)
    }

    @Test
    fun corruptedOrForeignQrIsRejected() {
        val qr = StyleShare.encodeQr(sample)
        assertNull(StyleShare.decode(qr.replace("bulle-style:1:", "bulle-style:2:")))
        assertNull(StyleShare.decode(qr.dropLast(12)))
        assertNull(StyleShare.decode("bulle-style:1:AAAA"))
        assertNull(StyleShare.decode("bulle-style:1:@@@"))
        // Bombe de décompression : refusée sans tout décompresser.
        val bomb = java.io.ByteArrayOutputStream().also { out ->
            val d = java.util.zip.Deflater(9, true)
            d.setInput(ByteArray(2_000_000) { ' '.code.toByte() })
            d.finish()
            val buf = ByteArray(4096)
            while (!d.finished()) out.write(buf, 0, d.deflate(buf))
            d.end()
        }.toByteArray()
        val bombText = "bulle-style:1:" + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bomb)
        assertNull(StyleShare.decode(bombText))
    }

    @Test
    fun fileNameIsSafe() {
        assertEquals("Essai.bulle-style", StyleShare.fileName(sample))
        assertEquals("Neon-du-soir.bulle-style", StyleShare.fileName(sample.copy(name = "Neon du soir/../")))
        assertEquals("style.bulle-style", StyleShare.fileName(sample.copy(name = "///")))
    }

    // --- Application ---

    @Test
    fun applyingAPackNeverTouchesPositionOrDeviceSettings() {
        val before = BulleSettings(
            position = PositionTweak(offsetXDp = 12f, offsetYDp = -4f, extraWidthDp = 30f, openDirection = OpenDirection.LEFT),
            motion = MotionSettings(haptics = false),
        ).let { it.copy(style = it.style.copy(stableWindow = false, hideWhenIdle = false)) }
        val after = sample.applyTo(before)
        assertEquals(before.position, after.position)
        assertEquals(before.visibility, after.visibility)
        assertEquals(before.modules, after.modules)
        assertFalse(after.style.stableWindow)
        assertFalse(after.style.hideWhenIdle)
        assertFalse(after.motion.haptics)
        // Les champs du pack, eux, sont appliqués.
        assertEquals(IslandShape.DROP, after.style.shape)
        assertEquals(1.2f, after.style.size)
        assertEquals(IslandFont.MONO, after.style.font)
        assertEquals(MotionStyle.PLAYFUL, after.motion.style)
        assertEquals(1.5f, after.motion.speed)
        assertEquals(mapOf("default" to "DROP", "music" to "ELASTIC"), after.motion.entryAnimations)
        // Capture puis application : retour exact au style de départ.
        assertEquals(before, StylePack.capture(before).applyTo(after))
        assertTrue(StylePack.capture(after).sameLookAs(sample))
    }

    @Test
    fun cleanName() {
        assertEquals("Mon style", StylePack.cleanName("  Mon style \n"))
        assertNull(StylePack.cleanName("   "))
        assertEquals(StylePack.MAX_NAME_LENGTH, StylePack.cleanName("y".repeat(100))!!.length)
    }

    @Test
    fun savedStylesOps() {
        var list = SavedStyles()
        list = SavedStylesOps.add(list, sample, id = "a", now = 1)
        list = SavedStylesOps.add(list, sample.copy(name = "Deux"), id = "b", now = 2)
        assertEquals(listOf("b", "a"), list.items.map { it.id })
        list = SavedStylesOps.rename(list, "a", "  Renommé ")
        assertEquals("Renommé", list.items.first { it.id == "a" }.pack.name)
        assertEquals(list, SavedStylesOps.rename(list, "a", "   "))
        val removed = list.items[0]
        list = SavedStylesOps.remove(list, "b")
        assertEquals(listOf("a"), list.items.map { it.id })
        list = SavedStylesOps.restore(list, removed, 0)
        assertEquals(listOf("b", "a"), list.items.map { it.id })
        assertEquals(list, SavedStylesOps.restore(list, removed, 0))
        // Fichier de styles : JSON tolérant (champs inconnus ignorés).
        val json = BulleJson.encodeToString(SavedStyles.serializer(), list)
        assertEquals(list, BulleJson.decodeFromString(SavedStyles.serializer(), json))
    }

    // --- Animations d'entrée ---

    @Test
    fun entryAnimationResolution() {
        val none = emptyMap<String, String>()
        assertEquals(EntryAnimation.INFLATE, EntryAnimations.resolve("music", none))
        assertEquals(EntryAnimation.INFLATE, EntryAnimations.resolve(null, none))
        val map = mapOf("default" to "SLIDE", "music" to "DROP", "call" to "NOPE")
        assertEquals(EntryAnimation.DROP, EntryAnimations.resolve("music", map))
        assertEquals(EntryAnimation.SLIDE, EntryAnimations.resolve("messages", map))
        // Valeur inconnue : repli sur la valeur par défaut.
        assertEquals(EntryAnimation.SLIDE, EntryAnimations.resolve("call", map))
        // Animations réduites : fondu, sauf le gonflement d'origine qui reste inchangé.
        assertEquals(EntryAnimation.FADE, EntryAnimations.resolve("music", map, reduceMotion = true))
        assertEquals(EntryAnimation.INFLATE, EntryAnimations.resolve("music", none, reduceMotion = true))
        // Une alerte avec sa propre animation garde la priorité.
        assertEquals(EntryAnimation.INFLATE, EntryAnimations.resolve("alerts", mapOf("alerts" to "RIPPLE"), contentOwnsEntrance = true))
    }

    @Test
    fun alertsWithOwnEntranceKeepPriority() {
        val motion = MotionSettings(entryAnimations = mapOf("default" to "RIPPLE", "alerts" to "DROP"))
        val plainAlert = FakeContent("alerts")
        val styled = StyledAlert(plainAlert, AlertStyle(entrance = AlertEntrance.BOUNCE))
        val styledNoEntrance = StyledAlert(plainAlert, AlertStyle(accent = 0xFFFF0000))
        assertEquals(EntryAnimation.DROP, EntryAnimations.resolve(plainAlert, motion, reduceMotion = false))
        assertEquals(EntryAnimation.INFLATE, EntryAnimations.resolve(styled, motion, reduceMotion = false))
        assertEquals(EntryAnimation.DROP, EntryAnimations.resolve(styledNoEntrance, motion, reduceMotion = false))
        assertEquals(EntryAnimation.RIPPLE, EntryAnimations.resolve(FakeContent("music"), motion, reduceMotion = false))
    }

    @Test
    fun entryAnimationEditing() {
        var map = EntryAnimations.with(emptyMap(), "music", EntryAnimation.DROP)
        assertEquals(mapOf("music" to "DROP"), map)
        map = EntryAnimations.with(map, "default", EntryAnimation.FADE)
        map = EntryAnimations.with(map, "default", EntryAnimation.INFLATE)
        assertEquals(mapOf("music" to "DROP"), map)
        map = EntryAnimations.with(map, "music", null)
        assertTrue(map.isEmpty())
        assertEquals(mapOf("a" to "FADE"), EntryAnimations.sanitize(mapOf("a" to "FADE", "B C" to "FADE", "c" to "??")))
    }

    @Test
    fun settingsStayCompatible() {
        // Ancien fichier sans le champ, et valeur d'une version future : lecture sans erreur.
        val old = BulleJson.decodeFromString(BulleSettings.serializer(), """{"motion":{"speed":1.2}}""")
        assertTrue(old.motion.entryAnimations.isEmpty())
        val future = BulleJson.decodeFromString(BulleSettings.serializer(), """{"motion":{"entryAnimations":{"music":"WARP"}}}""")
        assertEquals(EntryAnimation.INFLATE, EntryAnimations.resolve("music", future.motion.entryAnimations))
    }

    @Test
    fun entryTrackerPlaysOncePerArrival() {
        val t = EntryTracker()
        t.onPrimary("a")
        assertTrue(t.consume("a"))
        assertFalse(t.consume("a")) // Recomposition (retour de la carte étendue) : pas de rejeu.
        t.onPrimary("a")
        assertFalse(t.consume("a"))
        t.onPrimary("b")
        assertFalse(t.consume("a"))
        assertTrue(t.consume("b"))
        t.onPrimary(null)
        t.onPrimary("b")
        assertTrue(t.consume("b")) // Nouvelle arrivée après disparition.
    }
}
