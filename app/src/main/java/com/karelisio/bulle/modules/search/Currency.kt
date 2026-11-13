package com.karelisio.bulle.modules.search

import com.karelisio.bulle.core.apps.normalize
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Requête « 20 € en $ » analysée (codes ISO 4217). */
data class CurrencyQuery(val amount: BigDecimal, val from: String, val to: String)

/** Taux de la BCE (base EUR) tels que servis par Frankfurter, avec l'heure de récupération. */
@Serializable
data class RatesSnapshot(
    val base: String = "EUR",
    /** Date de publication des taux (AAAA-MM-JJ). */
    val date: String = "",
    val rates: Map<String, Double> = emptyMap(),
    /** Heure de récupération (epoch ms). */
    val fetchedAt: Long = 0,
)

/** Analyse des requêtes de devises, lecture de la réponse Frankfurter et cache (fonctions pures). */
object Currency {
    const val LATEST_URL = "https://api.frankfurter.app/latest"
    /** Durée de validité du cache local. */
    const val CACHE_TTL_MS = 12 * 60 * 60 * 1000L

    /** Devises publiées par la BCE (et donc par Frankfurter). */
    val SUPPORTED = setOf(
        "AUD", "BGN", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "EUR", "GBP", "HKD", "HUF", "IDR", "ILS",
        "INR", "ISK", "JPY", "KRW", "MXN", "MYR", "NOK", "NZD", "PHP", "PLN", "RON", "SEK", "SGD", "THB",
        "TRY", "USD", "ZAR",
    )

    /** Symboles et noms usuels (FR + EN), en minuscules sans accents. */
    private val ALIASES: Map<String, String> = buildMap {
        fun alias(code: String, vararg names: String) { names.forEach { put(it, code) } }
        alias("EUR", "€", "euro", "euros")
        alias("USD", "$", "us$", "dollar", "dollars", "dollar us", "dollars us", "dollar americain", "dollars americains", "us dollar", "us dollars")
        alias("GBP", "£", "livre", "livres", "livre sterling", "livres sterling", "pound", "pounds", "sterling")
        alias("JPY", "¥", "yen", "yens")
        alias("CHF", "franc suisse", "francs suisses", "swiss franc", "swiss francs")
        alias("CAD", "c$", "ca$", "dollar canadien", "dollars canadiens", "canadian dollar", "canadian dollars")
        alias("AUD", "a$", "au$", "dollar australien", "dollars australiens", "australian dollar", "australian dollars")
        alias("CNY", "yuan", "yuans", "renminbi", "rmb")
        alias("INR", "₹", "roupie", "roupies", "rupee", "rupees")
        alias("KRW", "₩", "won", "wons")
        alias("TRY", "₺", "livre turque", "livres turques", "lira", "turkish lira")
        alias("PLN", "zl", "zloty", "zlotys")
        alias("SEK", "couronne suedoise", "couronnes suedoises", "swedish krona")
        alias("NOK", "couronne norvegienne", "couronnes norvegiennes", "norwegian krone")
        alias("DKK", "couronne danoise", "couronnes danoises", "danish krone")
        alias("CZK", "couronne tcheque", "couronnes tcheques", "czech koruna")
        alias("BRL", "r$", "real", "reais", "reals")
        alias("MXN", "peso mexicain", "pesos mexicains", "mexican peso", "mexican pesos")
        alias("ILS", "₪", "shekel", "shekels")
        alias("THB", "฿", "baht")
        alias("ZAR", "rand", "rands")
        alias("HUF", "forint", "forints")
        alias("PHP", "₱")
        for (code in SUPPORTED) put(code.lowercase(), code)
    }

    private val NUMBER = """\d+(?:[.,]\d+)?"""
    /** « 20 € », « 20€ », « €20 », « 100 usd », « 1,5 dollars ». */
    private val AMOUNT_AFTER = Regex("""^($NUMBER)\s*(.+)$""")
    private val AMOUNT_BEFORE = Regex("""^([^\d\s]{1,3})\s*($NUMBER)$""")

    fun codeFor(text: String): String? {
        val k = normalize(text.trim().trimEnd('.')).replace(Regex("\\s+"), " ")
        return ALIASES[k]
    }

    /** Analyse « 20 € en $ » / « 100 usd to eur » / « €20 → £ ». null sinon (aucune requête réseau). */
    fun parse(input: String): CurrencyQuery? {
        val s = input.trim()
        if (s.isEmpty()) return null
        for (m in UnitConverter.CONNECTOR.findAll(s)) {
            val left = s.substring(0, m.range.first).trim()
            val right = s.substring(m.range.last + 1).trim()
            if (left.isEmpty() || right.isEmpty()) continue
            val to = codeFor(right) ?: continue
            val (amountText, fromText) = AMOUNT_AFTER.matchEntire(left)?.let { it.groupValues[1] to it.groupValues[2] }
                ?: AMOUNT_BEFORE.matchEntire(left)?.let { it.groupValues[2] to it.groupValues[1] }
                ?: continue
            val amount = UnitConverter.parseNumber(amountText) ?: continue
            val from = codeFor(fromText) ?: continue
            return CurrencyQuery(amount, from, to)
        }
        return null
    }

    /** Lit la réponse JSON de Frankfurter. null si illisible. */
    fun parseRates(body: String, fetchedAt: Long): RatesSnapshot? = runCatching {
        val root = Json.parseToJsonElement(body) as JsonObject
        val base = (root["base"] as? JsonPrimitive)?.content ?: return null
        val date = (root["date"] as? JsonPrimitive)?.content ?: ""
        val rates = (root["rates"] as? JsonObject)?.mapNotNull { (k, v) ->
            (v as? JsonPrimitive)?.doubleOrNull?.takeIf { it > 0 }?.let { k.uppercase() to it }
        }?.toMap().orEmpty()
        if (rates.isEmpty()) null else RatesSnapshot(base.uppercase(), date, rates, fetchedAt)
    }.getOrNull()

    /** Cache encore valide (moins de 12 h, et pas dans le futur si l'horloge a reculé). */
    fun isFresh(snapshot: RatesSnapshot?, now: Long): Boolean =
        snapshot != null && snapshot.rates.isNotEmpty() && now >= snapshot.fetchedAt && now - snapshot.fetchedAt < CACHE_TTL_MS

    /** Faut-il interroger le réseau ? Seulement pour une vraie requête de devise, cache périmé, option active. */
    fun shouldFetch(query: CurrencyQuery?, snapshot: RatesSnapshot?, now: Long, enabled: Boolean): Boolean =
        enabled && query != null && query.from != query.to && !isFresh(snapshot, now)

    private fun rate(snapshot: RatesSnapshot, code: String): BigDecimal? =
        if (code == snapshot.base) BigDecimal.ONE else snapshot.rates[code]?.let { BigDecimal(it.toString()) }

    /** Convertit via la base des taux. null si une devise manque. */
    fun convert(query: CurrencyQuery, snapshot: RatesSnapshot): BigDecimal? {
        val from = rate(snapshot, query.from) ?: return null
        val to = rate(snapshot, query.to) ?: return null
        return query.amount.multiply(to, MathContext.DECIMAL64).divide(from, MathContext.DECIMAL64)
            .setScale(2, RoundingMode.HALF_EVEN)
    }
}
