package com.karelisio.bulle.core.backup

import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.core.update.UpdateSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupLogicTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val custom = BulleSettings(
        islandEnabled = false,
        onboardingDone = true,
        motion = MotionSettings(MotionStyle.SNAPPY, 1.5f),
        modules = mapOf("music" to false),
        update = UpdateSettings(lastCheck = 42, skippedVersion = "9.9.9"),
        backup = BackupSettings(autoBackup = false, lastAutoBackup = 1234),
    )

    @Test
    fun roundTrip() {
        val text = BackupCodec.encode(custom, "1.2.3", 1_700_000_000_000)
        assertTrue(text.contains("\"schema\": 1"))
        val result = BackupCodec.decode(text) as DecodeResult.Ok
        assertEquals(1, result.envelope.schema)
        assertEquals("1.2.3", result.envelope.appVersion)
        assertEquals(1_700_000_000_000, result.envelope.exportedAt)
        // L'état technique n'est pas exporté.
        assertEquals(BackupCodec.sanitize(custom), result.envelope.settings)
        assertEquals(UpdateSettings(), result.envelope.settings.update)
        assertEquals(0L, result.envelope.settings.backup.lastAutoBackup)
        assertEquals(MotionStyle.SNAPPY, result.envelope.settings.motion.style)
    }

    @Test
    fun mergeKeepsTechnicalState() {
        val current = BulleSettings(update = UpdateSettings(lastCheck = 99), backup = BackupSettings(lastAutoBackup = 77))
        val merged = BackupCodec.merge(current, BackupCodec.sanitize(custom))
        assertEquals(99L, merged.update.lastCheck)
        assertEquals(77L, merged.backup.lastAutoBackup)
        assertEquals(false, merged.backup.autoBackup)
        assertEquals(false, merged.islandEnabled)
    }

    @Test
    fun invalidFiles() {
        assertEquals(DecodeResult.InvalidJson, BackupCodec.decode("pas du json {"))
        assertEquals(DecodeResult.NotABackup, BackupCodec.decode("[1,2]"))
        assertEquals(DecodeResult.NotABackup, BackupCodec.decode("""{"foo": 1}"""))
        assertEquals(DecodeResult.NotABackup, BackupCodec.decode("""{"schema": 1, "settings": 3}"""))
        assertEquals(DecodeResult.UnsupportedSchema(2), BackupCodec.decode("""{"schema": 2, "settings": {}}"""))
        // Champs inconnus tolérés, valeurs manquantes par défaut.
        val ok = BackupCodec.decode("""{"schema": 1, "settings": {"islandEnabled": false, "futur": 1}}""")
        assertEquals(false, (ok as DecodeResult.Ok).envelope.settings.islandEnabled)
    }

    @Test
    fun exportFileName() {
        assertTrue(BackupCodec.exportFileName(0).matches(Regex("bulle-reglages-\\d{4}-\\d{2}-\\d{2}\\.json")))
    }

    @Test
    fun rotationKeepsSevenMostRecent() {
        val dir = tmp.newFolder("backups")
        java.io.File(dir, "autre.txt").writeText("x") // ignoré
        for (i in 1..10) BackupStore.writeAuto(dir, "{}", now = i * 1000L)
        val kept = BackupStore.list(dir)
        assertEquals(7, kept.size)
        assertEquals((10 downTo 4).map { it * 1000L }, kept.map { it.createdAt })
        assertTrue(java.io.File(dir, "autre.txt").exists())
    }
}
