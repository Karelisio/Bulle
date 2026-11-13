package com.karelisio.bulle.modules.search

import com.karelisio.bulle.core.apps.normalize
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Catégories d'unités prises en charge. */
enum class UnitCategory { LENGTH, MASS, TEMPERATURE, VOLUME, AREA, SPEED, DATA, TIME }

/**
 * Une unité : [factor] = valeur de 1 unité exprimée dans l'unité de base de sa catégorie
 * (mètre, kilogramme, litre, m², m/s, octet, seconde). La température a sa propre formule.
 */
data class MeasureUnit(
    val id: String,
    val category: UnitCategory,
    val symbol: String,
    val factor: BigDecimal,
    val aliases: List<String>,
)

/** Requête « 5 km en miles » analysée. */
data class UnitQuery(val amount: BigDecimal, val from: MeasureUnit, val to: MeasureUnit)

/** Conversion calculée. */
data class UnitConversion(val query: UnitQuery, val result: BigDecimal)

/**
 * Conversions d'unités de la recherche (fonctions pures). Connecteurs FR + EN : « en », « in »,
 * « to », « vers », « → », « -> », « = ». Nombres à virgule ou à point.
 */
object UnitConverter {
    private val MC = MathContext.DECIMAL64

    private fun u(id: String, cat: UnitCategory, symbol: String, factor: String, vararg aliases: String) =
        MeasureUnit(id, cat, symbol, BigDecimal(factor), (listOf(id, symbol) + aliases).map(::key).distinct())

    val units: List<MeasureUnit> = listOf(
        // Longueur (base : mètre)
        u("mm", UnitCategory.LENGTH, "mm", "0.001", "millimetre", "millimetres", "millimeter", "millimeters", "millimètre", "millimètres"),
        u("cm", UnitCategory.LENGTH, "cm", "0.01", "centimetre", "centimetres", "centimeter", "centimeters", "centimètre", "centimètres"),
        u("m", UnitCategory.LENGTH, "m", "1", "metre", "metres", "meter", "meters", "mètre", "mètres"),
        u("km", UnitCategory.LENGTH, "km", "1000", "kilometre", "kilometres", "kilometer", "kilometers", "kilomètre", "kilomètres"),
        u("in", UnitCategory.LENGTH, "in", "0.0254", "inch", "inches", "pouce", "pouces", "\""),
        u("ft", UnitCategory.LENGTH, "ft", "0.3048", "foot", "feet", "pied", "pieds", "'"),
        u("yd", UnitCategory.LENGTH, "yd", "0.9144", "yard", "yards"),
        u("mi", UnitCategory.LENGTH, "mi", "1609.344", "mile", "miles"),
        u("nmi", UnitCategory.LENGTH, "nmi", "1852", "mille marin", "milles marins", "nautical mile", "nautical miles"),
        // Masse (base : kilogramme)
        u("mg", UnitCategory.MASS, "mg", "0.000001", "milligramme", "milligrammes", "milligram", "milligrams"),
        u("g", UnitCategory.MASS, "g", "0.001", "gramme", "grammes", "gram", "grams", "gr"),
        u("kg", UnitCategory.MASS, "kg", "1", "kilo", "kilos", "kilogramme", "kilogrammes", "kilogram", "kilograms"),
        u("t", UnitCategory.MASS, "t", "1000", "tonne", "tonnes", "ton", "tons"),
        u("oz", UnitCategory.MASS, "oz", "0.028349523125", "once", "onces", "ounce", "ounces"),
        u("lb", UnitCategory.MASS, "lb", "0.45359237", "lbs", "livre", "livres", "pound", "pounds"),
        u("st", UnitCategory.MASS, "st", "6.35029318", "stone", "stones"),
        // Température (formule dédiée ; facteur inutilisé)
        u("c", UnitCategory.TEMPERATURE, "°C", "1", "°c", "celsius", "degre celsius", "degres celsius", "degree celsius", "degrees celsius", "degres", "degre"),
        u("f", UnitCategory.TEMPERATURE, "°F", "1", "°f", "fahrenheit", "degre fahrenheit", "degres fahrenheit", "degrees fahrenheit"),
        u("k", UnitCategory.TEMPERATURE, "K", "1", "kelvin", "kelvins"),
        // Volume (base : litre)
        u("ml", UnitCategory.VOLUME, "mL", "0.001", "millilitre", "millilitres", "milliliter", "milliliters"),
        u("cl", UnitCategory.VOLUME, "cL", "0.01", "centilitre", "centilitres", "centiliter", "centiliters"),
        u("dl", UnitCategory.VOLUME, "dL", "0.1", "decilitre", "decilitres", "deciliter", "deciliters"),
        u("l", UnitCategory.VOLUME, "L", "1", "litre", "litres", "liter", "liters"),
        u("m3", UnitCategory.VOLUME, "m³", "1000", "m³", "metre cube", "metres cubes", "cubic meter", "cubic meters"),
        u("tsp", UnitCategory.VOLUME, "tsp", "0.00492892159375", "cuillere a cafe", "cuilleres a cafe", "teaspoon", "teaspoons"),
        u("tbsp", UnitCategory.VOLUME, "tbsp", "0.01478676478125", "cuillere a soupe", "cuilleres a soupe", "tablespoon", "tablespoons"),
        u("cup", UnitCategory.VOLUME, "cup", "0.2365882365", "cups", "tasse", "tasses"),
        u("floz", UnitCategory.VOLUME, "fl oz", "0.0295735295625", "fl oz", "fluid ounce", "fluid ounces"),
        u("pt", UnitCategory.VOLUME, "pt", "0.473176473", "pint", "pints", "pinte", "pintes"),
        u("gal", UnitCategory.VOLUME, "gal", "3.785411784", "gallon", "gallons"),
        // Surface (base : m²)
        u("cm2", UnitCategory.AREA, "cm²", "0.0001", "cm²", "centimetre carre", "centimetres carres", "square centimeter", "square centimeters"),
        u("m2", UnitCategory.AREA, "m²", "1", "m²", "metre carre", "metres carres", "square meter", "square meters", "sqm"),
        u("km2", UnitCategory.AREA, "km²", "1000000", "km²", "kilometre carre", "kilometres carres", "square kilometer", "square kilometers"),
        u("ha", UnitCategory.AREA, "ha", "10000", "hectare", "hectares"),
        u("a", UnitCategory.AREA, "a", "100", "are", "ares"),
        u("ft2", UnitCategory.AREA, "ft²", "0.09290304", "ft²", "sq ft", "square foot", "square feet", "pied carre", "pieds carres"),
        u("ac", UnitCategory.AREA, "ac", "4046.8564224", "acre", "acres"),
        u("mi2", UnitCategory.AREA, "mi²", "2589988.110336", "mi²", "sq mi", "square mile", "square miles"),
        // Vitesse (base : m/s)
        u("m/s", UnitCategory.SPEED, "m/s", "1", "mps", "metre par seconde", "metres par seconde", "meters per second"),
        u("km/h", UnitCategory.SPEED, "km/h", "0.2777777777777777777777777777777778", "kmh", "kph", "km h", "kilometre heure", "kilometres heure", "kilometres par heure"),
        u("mph", UnitCategory.SPEED, "mph", "0.44704", "mi/h", "miles per hour", "miles par heure"),
        u("kn", UnitCategory.SPEED, "kn", "0.5144444444444444444444444444444444", "kt", "noeud", "noeuds", "knot", "knots"),
        // Données (base : octet ; préfixes décimaux, et binaires « Kio »/« KiB »)
        u("bit", UnitCategory.DATA, "bit", "0.125", "bits"),
        u("o", UnitCategory.DATA, "o", "1", "octet", "octets", "byte", "bytes", "b"),
        u("ko", UnitCategory.DATA, "Ko", "1000", "kb", "kilooctet", "kilooctets", "kilobyte", "kilobytes"),
        u("mo", UnitCategory.DATA, "Mo", "1000000", "mb", "megaoctet", "megaoctets", "megabyte", "megabytes"),
        u("go", UnitCategory.DATA, "Go", "1000000000", "gb", "gigaoctet", "gigaoctets", "gigabyte", "gigabytes", "giga"),
        u("to", UnitCategory.DATA, "To", "1000000000000", "tb", "teraoctet", "teraoctets", "terabyte", "terabytes"),
        u("kio", UnitCategory.DATA, "Kio", "1024", "kib", "kibioctet", "kibioctets", "kibibyte", "kibibytes"),
        u("mio", UnitCategory.DATA, "Mio", "1048576", "mib", "mebioctet", "mebioctets", "mebibyte", "mebibytes"),
        u("gio", UnitCategory.DATA, "Gio", "1073741824", "gib", "gibioctet", "gibioctets", "gibibyte", "gibibytes"),
        // Temps (base : seconde)
        u("ms", UnitCategory.TIME, "ms", "0.001", "milliseconde", "millisecondes", "millisecond", "milliseconds"),
        u("s", UnitCategory.TIME, "s", "1", "sec", "seconde", "secondes", "second", "seconds"),
        u("min", UnitCategory.TIME, "min", "60", "mn", "minute", "minutes", "mins"),
        u("h", UnitCategory.TIME, "h", "3600", "hr", "hrs", "heure", "heures", "hour", "hours"),
        u("d", UnitCategory.TIME, "j", "86400", "j", "jour", "jours", "day", "days"),
        u("wk", UnitCategory.TIME, "sem.", "604800", "sem", "semaine", "semaines", "week", "weeks"),
        u("mon", UnitCategory.TIME, "mois", "2629746", "mois", "month", "months"),
        u("yr", UnitCategory.TIME, "an", "31556952", "an", "ans", "annee", "annees", "year", "years", "y"),
    )

    private val byAlias: Map<String, MeasureUnit> = buildMap { for (unit in units) for (a in unit.aliases) putIfAbsent(a, unit) }

    /** Connecteurs « de → vers » (FR et EN). Les connecteurs-mots exigent des espaces autour. */
    internal val CONNECTOR = Regex("""(?<=\s)(?:en|in|to|vers|into)(?=\s)|→|->|=>|=""", RegexOption.IGNORE_CASE)

    private val NUMBER = Regex("""^([+\-−]?\d+(?:[.,]\d+)?|[+\-−]?[.,]\d+)\s*(.*)$""")

    /** Clé de recherche d'une unité : minuscules, sans accents, espaces compactés, « 2 » ↔ « ² ». */
    internal fun key(s: String): String = normalize(s.trim().trimEnd('.'))
        .replace("²", "2").replace("³", "3")
        .replace(Regex("\\s+"), " ")
        .removePrefix("degres ").let { if (it.startsWith("°") && it.length > 1) it.substring(1) else it }

    fun unitFor(text: String): MeasureUnit? {
        val k = key(text)
        return byAlias[k] ?: byAlias[key(text).removePrefix("°")]
    }

    /** Lit un nombre écrit à la virgule ou au point. */
    internal fun parseNumber(text: String): BigDecimal? =
        text.trim().replace('−', '-').replace(',', '.').toBigDecimalOrNull()

    /** Analyse « 5 km en miles ». null si ce n'est pas une conversion d'unités valide. */
    fun parse(input: String): UnitQuery? {
        val s = input.trim()
        if (s.isEmpty()) return null
        for (m in CONNECTOR.findAll(s)) {
            val left = s.substring(0, m.range.first).trim()
            val right = s.substring(m.range.last + 1).trim()
            if (left.isEmpty() || right.isEmpty()) continue
            val nm = NUMBER.matchEntire(left) ?: continue
            val amount = parseNumber(nm.groupValues[1]) ?: continue
            val from = unitFor(nm.groupValues[2]) ?: continue
            val to = unitFor(right) ?: continue
            if (from.category != to.category) continue
            return UnitQuery(amount, from, to)
        }
        return null
    }

    fun convert(query: UnitQuery): BigDecimal = convert(query.amount, query.from, query.to)

    fun convert(amount: BigDecimal, from: MeasureUnit, to: MeasureUnit): BigDecimal {
        require(from.category == to.category) { "Catégories différentes" }
        if (from.category == UnitCategory.TEMPERATURE) {
            val kelvin = when (from.id) {
                "c" -> amount.add(BigDecimal("273.15"))
                "f" -> amount.subtract(BigDecimal(32)).multiply(BigDecimal(5)).divide(BigDecimal(9), MC).add(BigDecimal("273.15"))
                else -> amount
            }
            return when (to.id) {
                "c" -> kelvin.subtract(BigDecimal("273.15"))
                "f" -> kelvin.subtract(BigDecimal("273.15")).multiply(BigDecimal(9)).divide(BigDecimal(5), MC).add(BigDecimal(32))
                else -> kelvin
            }.round(MC)
        }
        return amount.multiply(from.factor, MC).divide(to.factor, MC)
    }

    /** Analyse et convertit en une fois. */
    fun evaluate(input: String): UnitConversion? = parse(input)?.let { UnitConversion(it, convert(it)) }

    /** Arrondi d'affichage : 10 chiffres significatifs, zéros superflus retirés. */
    fun display(value: BigDecimal): BigDecimal =
        if (value.signum() == 0) BigDecimal.ZERO else value.round(MathContext(10, RoundingMode.HALF_EVEN)).stripTrailingZeros()
}
