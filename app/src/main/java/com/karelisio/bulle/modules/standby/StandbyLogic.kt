package com.karelisio.bulle.modules.standby

import com.karelisio.bulle.modules.battery.PowerSource
import kotlin.math.abs

/** Orientation physique déduite de la gravité (avec hystérésis : pas de bascule au moindre tremblement). */
enum class Posture { PORTRAIT, LANDSCAPE, FLAT, UNKNOWN }

/** Logique pure de Veille (testée). */
object StandbyLogic {

    /**
     * Posture à partir du vecteur gravité (m/s², repère de l'appareil). [previous] sert d'hystérésis :
     * il faut nettement basculer pour quitter l'état courant.
     */
    fun posture(gx: Float, gy: Float, gz: Float, previous: Posture = Posture.UNKNOWN): Posture {
        val ax = abs(gx)
        val ay = abs(gy)
        val az = abs(gz)
        if (az > 8.2f) return Posture.FLAT
        return when (previous) {
            Posture.LANDSCAPE -> if (ay > ax + 2.5f) Posture.PORTRAIT else if (az > 7.5f) Posture.FLAT else Posture.LANDSCAPE
            Posture.PORTRAIT -> if (ax > ay + 2.5f) Posture.LANDSCAPE else if (az > 7.5f) Posture.FLAT else Posture.PORTRAIT
            else -> when {
                ax > 5.5f && ax > ay + 1.5f -> Posture.LANDSCAPE
                ay > 5.5f && ay > ax + 1.5f -> Posture.PORTRAIT
                az > 7.5f -> Posture.FLAT
                else -> Posture.UNKNOWN
            }
        }
    }

    /** Le chargeur branché fait-il partie de ceux qui déclenchent Veille ? */
    fun chargerMatches(source: PowerSource, wanted: StandbyCharger): Boolean = when (source) {
        PowerSource.NONE -> false
        PowerSource.WIRELESS -> wanted != StandbyCharger.WIRED
        PowerSource.AC, PowerSource.USB, PowerSource.DOCK -> wanted != StandbyCharger.WIRELESS
    }

    /**
     * Conditions de Veille : en charge (bon chargeur), à l'horizontale, et téléphone « au repos »
     * (verrouillé, écran éteint ou sur l'écran d'accueil) — jamais par-dessus une vidéo regardée
     * en paysage pendant la charge.
     */
    fun conditions(
        enabled: Boolean,
        source: PowerSource,
        charger: StandbyCharger,
        posture: Posture,
        idle: Boolean,
    ): Boolean = enabled && chargerMatches(source, charger) && posture == Posture.LANDSCAPE && idle

    /** Dans la plage horaire [start, end[ (minutes), y compris à cheval sur minuit. */
    fun inWindow(minute: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

    /** Mode nuit actif ? [lux] null : capteur absent ou pas encore lu. */
    fun isNight(mode: StandbyNightMode, minute: Int, start: Int, end: Int, lux: Float?, darkLux: Float): Boolean {
        val scheduled = inWindow(minute, start, end)
        val dark = lux != null && lux <= darkLux
        return when (mode) {
            StandbyNightMode.AUTO -> scheduled || dark
            StandbyNightMode.SCHEDULE -> scheduled
            StandbyNightMode.LIGHT -> dark
            StandbyNightMode.OFF -> false
        }
    }

    /**
     * Décalage anti-marquage OLED (pixels) : le contenu se déplace d'une position à la suivante
     * toutes les [periodMs], sur un petit cercle de 8 positions de rayon [maxPx].
     */
    fun pixelShift(nowMs: Long, maxPx: Float, periodMs: Long = 60_000L): Pair<Float, Float> {
        val steps = arrayOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f, -1f to 1f, -1f to 0f, -1f to -1f, 0f to -1f)
        val (x, y) = steps[((nowMs / periodMs) % steps.size).toInt()]
        return x * maxPx to y * maxPx
    }

    /** Pages effectives : celles choisies, la page Photos seulement si un album est choisi. */
    fun effectivePages(s: StandbySettings): List<StandbyPage> =
        s.pages.distinct().filter { it != StandbyPage.PHOTOS || s.albumId != null }.ifEmpty { listOf(StandbyPage.CLOCKS) }

    /** Déplace une page dans la liste (réordonnancement des réglages). */
    fun move(pages: List<StandbyPage>, page: StandbyPage, by: Int): List<StandbyPage> {
        val i = pages.indexOf(page)
        if (i < 0) return pages
        val j = (i + by).coerceIn(0, pages.lastIndex)
        if (i == j) return pages
        return pages.toMutableList().apply { removeAt(i); add(j, page) }
    }
}

/** L'heure en toutes lettres, arrondie à 5 minutes (français et anglais). */
object ClockWords {
    private val frHours = listOf(
        "minuit", "une heure", "deux heures", "trois heures", "quatre heures", "cinq heures", "six heures",
        "sept heures", "huit heures", "neuf heures", "dix heures", "onze heures", "midi",
    )
    private val enHours = listOf(
        "twelve", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
    )
    private val frMinutes = mapOf(
        5 to "cinq", 10 to "dix", 15 to "et quart", 20 to "vingt", 25 to "vingt-cinq", 30 to "et demie",
    )

    private fun round(minute: Int): Int = ((minute + 2) / 5) * 5

    /** « Il est dix heures et quart », « Il est midi moins le quart ». */
    fun french(hour: Int, minute: Int): String {
        var h = hour % 24
        var m = round(minute)
        if (m == 60) { m = 0; h = (h + 1) % 24 }
        fun name(hh: Int): String = when (hh) {
            0 -> frHours[0]
            12 -> frHours[12]
            else -> frHours[hh % 12]
        }
        return when {
            m == 0 -> "Il est ${name(h)}"
            m <= 30 -> "Il est ${name(h)} ${frMinutes.getValue(m)}"
            else -> {
                val next = (h + 1) % 24
                val rest = 60 - m
                val tail = if (rest == 15) "moins le quart" else "moins ${frMinutes.getValue(rest)}"
                "Il est ${name(next)} $tail"
            }
        }
    }

    /** « It's quarter past ten », « It's twenty to five ». */
    fun english(hour: Int, minute: Int): String {
        var h = hour % 24
        var m = round(minute)
        if (m == 60) { m = 0; h = (h + 1) % 24 }
        fun name(hh: Int) = when (hh) {
            0 -> "midnight"
            12 -> "noon"
            else -> enHours[hh % 12]
        }
        val minutes = mapOf(5 to "five", 10 to "ten", 15 to "quarter", 20 to "twenty", 25 to "twenty-five", 30 to "half")
        return when {
            m == 0 -> if (h == 0 || h == 12) "It's ${name(h)}" else "It's ${name(h)} o'clock"
            m <= 30 -> "It's ${minutes.getValue(m)} past ${name(h)}"
            else -> "It's ${minutes.getValue(60 - m)} to ${name((h + 1) % 24)}"
        }
    }
}
