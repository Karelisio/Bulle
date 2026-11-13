package com.karelisio.bulle.modules.search

import com.karelisio.bulle.modules.translate.TranslateLanguages
import com.karelisio.bulle.modules.translate.TranslateSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Devises (analyse, réponse Frankfurter, cache), aiguillage de la saisie, raccourcis Réglages, langues. */
class CurrencyAndShortcutsTest {

    private val sample = """{"amount":1.0,"base":"EUR","date":"2026-11-12","rates":{"USD":1.1,"GBP":0.85,"JPY":160.5}}"""

    @Test
    fun parsesCurrencyQueries() {
        assertEquals(CurrencyQuery(BigDecimal(20), "EUR", "USD"), Currency.parse("20 € en $"))
        assertEquals(CurrencyQuery(BigDecimal(100), "USD", "EUR"), Currency.parse("100 usd to eur"))
        assertEquals(CurrencyQuery(BigDecimal(20), "EUR", "GBP"), Currency.parse("€20 → £"))
        assertEquals(CurrencyQuery(BigDecimal("1.5"), "USD", "EUR"), Currency.parse("1,5 dollars en euros"))
        assertEquals(CurrencyQuery(BigDecimal(50), "CHF", "EUR"), Currency.parse("50 CHF in EUR"))
        assertNull(Currency.parse("5 km en miles"))
        assertNull(Currency.parse("20 €"))
        assertNull(Currency.parse("20 € en pommes"))
        assertNull(Currency.parse("spotify"))
    }

    @Test
    fun readsRatesAndConverts() {
        val snap = Currency.parseRates(sample, fetchedAt = 1_000)!!
        assertEquals("EUR", snap.base)
        assertEquals("2026-11-12", snap.date)
        assertEquals(1.1, snap.rates.getValue("USD"), 1e-9)
        assertEquals(BigDecimal("22.00"), Currency.convert(CurrencyQuery(BigDecimal(20), "EUR", "USD"), snap))
        assertEquals(BigDecimal("100.00"), Currency.convert(CurrencyQuery(BigDecimal(110), "USD", "EUR"), snap))
        assertEquals(BigDecimal("17.00"), Currency.convert(CurrencyQuery(BigDecimal(22), "USD", "GBP"), snap))
        assertNull(Currency.convert(CurrencyQuery(BigDecimal(1), "EUR", "CAD"), snap))
        assertNull(Currency.parseRates("pas du json", 0))
        assertNull(Currency.parseRates("""{"base":"EUR","rates":{}}""", 0))
    }

    @Test
    fun cacheFreshnessAndFetchPolicy() {
        val snap = Currency.parseRates(sample, fetchedAt = 0)!!
        val hour = 3_600_000L
        assertTrue(Currency.isFresh(snap, 11 * hour))
        assertFalse(Currency.isFresh(snap, 13 * hour))
        assertFalse(Currency.isFresh(snap.copy(fetchedAt = 10 * hour), 0)) // horloge reculée
        assertFalse(Currency.isFresh(null, 0))
        val q = CurrencyQuery(BigDecimal.ONE, "EUR", "USD")
        assertFalse(Currency.shouldFetch(null, null, 0, enabled = true)) // pas de devise saisie : aucune requête
        assertFalse(Currency.shouldFetch(q, null, 0, enabled = false))
        assertFalse(Currency.shouldFetch(q, snap, 11 * hour, enabled = true))
        assertTrue(Currency.shouldFetch(q, snap, 13 * hour, enabled = true))
        assertTrue(Currency.shouldFetch(q, null, 0, enabled = true))
        assertFalse(Currency.shouldFetch(q.copy(to = "EUR"), null, 0, enabled = true))
    }

    @Test
    fun routesQueriesToSections() {
        val all = SearchSettings()
        assertTrue(QuickAnswers.of("2+2", all) is QuickAnswer.Calc)
        assertTrue(QuickAnswers.of("5 km en miles", all) is QuickAnswer.Units)
        assertTrue(QuickAnswers.of("20 € en $", all) is QuickAnswer.Money)
        assertNull(QuickAnswers.of("20 € en $", all.copy(currency = false)))
        assertNull(QuickAnswers.of("2+2", all.copy(calculator = false)))
        assertNull(QuickAnswers.of("5 km en miles", all.copy(units = false)))
        assertNull(QuickAnswers.of("spotify", all))
        assertTrue(QuickAnswers.isTextQuery("wi", null))
        assertFalse(QuickAnswers.isTextQuery("w", null))
        assertFalse(QuickAnswers.isTextQuery("2+2", null))
    }

    @Test
    fun filtersSettingsShortcuts() {
        assertEquals("wifi", SettingsShortcuts.filter("wifi").first().id)
        assertEquals("wifi", SettingsShortcuts.filter("Wi-Fi").first().id)
        assertEquals("battery", SettingsShortcuts.filter("batt").first().id)
        assertEquals("battery", SettingsShortcuts.filter("Batterie").first().id)
        assertEquals("airplane", SettingsShortcuts.filter("avion").first().id)
        assertEquals("airplane", SettingsShortcuts.filter("mode avion").first().id)
        assertEquals("airplane", SettingsShortcuts.filter("airplane").first().id)
        assertEquals("developer", SettingsShortcuts.filter("développeur").first().id)
        assertEquals("location", SettingsShortcuts.filter("gps").first().id)
        assertEquals("hotspot", SettingsShortcuts.filter("réseau").first().id) // mot-clé exact d'abord
        assertTrue(SettingsShortcuts.filter("w").isEmpty())
        assertTrue(SettingsShortcuts.filter("zzzz").isEmpty())
        assertTrue(SettingsShortcuts.filter("e").isEmpty())
        assertTrue(SettingsShortcuts.filter("s", limit = 10).isEmpty())
        assertTrue(SettingsShortcuts.filter("notif").size <= 3)
        assertEquals(SettingsShortcuts.all.size, SettingsShortcuts.all.map { it.id }.toSet().size)
    }

    @Test
    fun translationLanguages() {
        val supported = listOf("de", "en", "fr")
        assertEquals("fr", TranslateLanguages.target(TranslateSettings(), supported, phone = "fr"))
        assertEquals("en", TranslateLanguages.target(TranslateSettings(), supported, phone = "xx"))
        assertEquals("de", TranslateLanguages.target(TranslateSettings(targetLanguage = "de"), supported, phone = "fr"))
        assertEquals("fr", TranslateLanguages.target(TranslateSettings(targetLanguage = "zz"), supported, phone = "fr"))
        assertEquals("en", TranslateLanguages.resolveTarget("fr", "fr"))
        assertEquals("fr", TranslateLanguages.resolveTarget("en", "en"))
        assertEquals("fr", TranslateLanguages.resolveTarget(null, "fr"))
        assertEquals(listOf("fr", "en", "de"), TranslateLanguages.ordered(supported))
    }
}
