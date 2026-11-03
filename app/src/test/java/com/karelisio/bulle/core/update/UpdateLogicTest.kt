package com.karelisio.bulle.core.update

import com.karelisio.bulle.ui.about.Changelog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateLogicTest {

    @Test
    fun compareVersions() {
        assertTrue(SemVer.compare("v1.2.10", "1.2.9") > 0)
        assertTrue(SemVer.compare("1.10.0", "1.9.9") > 0)
        assertEquals(0, SemVer.compare("1.2", "v1.2.0"))
        assertTrue(SemVer.compare("1.2.0-beta", "1.2.0") < 0)
        assertTrue(SemVer.compare("1.2.0-beta.2", "1.2.0-beta.10") < 0)
        assertTrue(SemVer.compare("2.0.0", "10.0.0") < 0)
    }

    @Test
    fun isNewerIgnoresDebugSuffix() {
        assertTrue(SemVer.isNewer("v0.2.0", "0.1.0-debug"))
        assertFalse(SemVer.isNewer("v0.1.0", "0.1.0-debug"))
        assertFalse(SemVer.isNewer("v0.1.0", "0.2.0"))
    }

    @Test
    fun checkInterval() {
        assertTrue(UpdateChecker.isDue(0, System.currentTimeMillis()))
        assertFalse(UpdateChecker.isDue(1_000, 1_000 + UpdateChecker.CHECK_INTERVAL_MS - 1))
        assertTrue(UpdateChecker.isDue(1_000, 1_000 + UpdateChecker.CHECK_INTERVAL_MS))
    }

    @Test
    fun parseGithubRelease() {
        val json = """
            {
              "tag_name": "v1.4.2",
              "html_url": "https://github.com/karelisio/bulle/releases/tag/v1.4.2",
              "draft": false,
              "body": "## Nouveautés\r\n- Île plus fluide\r\n- **Paroles** synchronisées",
              "assets": [
                {"name": "notes.txt", "browser_download_url": "https://x/notes.txt", "size": 12},
                {"name": "Bulle-v1.4.2.apk", "browser_download_url": "https://x/Bulle-v1.4.2.apk", "size": 7340032}
              ],
              "unknown": {"nested": true}
            }
        """.trimIndent()
        val r = UpdateChecker.parseRelease(json)!!
        assertEquals("1.4.2", r.version)
        assertEquals("v1.4.2", r.tag)
        assertEquals("https://x/Bulle-v1.4.2.apk", r.apkUrl)
        assertEquals(7340032L, r.apkSize)
        assertTrue(r.changelog.startsWith("## Nouveautés\n- Île"))
    }

    @Test
    fun parseReleaseWithoutApk() {
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v1.0.0","assets":[]}"""))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"nightly","assets":[{"name":"a.apk","browser_download_url":"u"}]}"""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun parseInvalidJson() {
        UpdateChecker.parseRelease("<html>")
    }

    @Test
    fun markdownBlocks() {
        val blocks = Markdown.parse("# Titre\n\n- un\n  - deux\n1. trois\n\nTexte\nsuite")
        assertEquals(MdBlock.Heading(1, "Titre"), blocks[0])
        assertEquals(MdBlock.Bullet("un", 0), blocks[1])
        assertEquals(MdBlock.Bullet("deux", 1), blocks[2])
        assertEquals(MdBlock.Bullet("trois", 0), blocks[3])
        assertEquals(MdBlock.Paragraph("Texte suite"), blocks[4])
        assertEquals("gras et code", Markdown.inline("**gras** et `code`").text)
    }

    @Test
    fun changelogSections() {
        val s = Changelog.sections("# Changelog\n\n## 0.2.0\n- b\n\n## 0.1.0\n- a\n")
        assertEquals(listOf("0.2.0", "0.1.0"), s.map { it.title })
        assertEquals("- a", s[1].body)
    }
}
