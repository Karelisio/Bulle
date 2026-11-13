package com.karelisio.bulle.modules.search

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Taux de change : cache local de 12 h (DataStore du module), rafraîchi depuis Frankfurter
 * seulement quand une requête de devise est saisie. Hors ligne : dernier cache, avec sa date.
 */
object CurrencyRates {
    private const val TIMEOUT_MS = 6_000
    private val mutex = Mutex()

    /** Source réseau remplaçable (tests). */
    @Volatile
    internal var fetcher: (String) -> String? = ::httpGet

    /** Taux à utiliser pour [query] : cache frais, sinon rafraîchis, sinon cache périmé (ou null). */
    suspend fun ratesFor(context: Context, query: CurrencyQuery, enabled: Boolean, now: Long = System.currentTimeMillis()): RatesSnapshot? =
        mutex.withLock {
            val cached = SearchPrefs.cachedRates(context)
            if (!Currency.shouldFetch(query, cached, now, enabled)) return@withLock cached
            val fresh = withContext(Dispatchers.IO) { fetcher(Currency.LATEST_URL) }?.let { Currency.parseRates(it, now) }
            if (fresh != null) {
                SearchPrefs.saveRates(context, fresh)
                fresh
            } else {
                cached
            }
        }

    private fun httpGet(url: String): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = TIMEOUT_MS
            c.readTimeout = TIMEOUT_MS
            c.instanceFollowRedirects = true
            c.setRequestProperty("Accept", "application/json")
            if (c.responseCode !in 200..299) null else c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
