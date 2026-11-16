package com.karelisio.bulle.modules.standby

import kotlinx.serialization.Serializable

/** Pages de Veille (balayer pour changer). */
@Serializable
enum class StandbyPage { CLOCKS, WIDGETS, MUSIC, PHOTOS, ECHO }

/** Styles d'horloge. */
@Serializable
enum class StandbyClock { DIGITAL, ANALOG, WORDS, FLIP }

/** Chargeurs qui déclenchent Veille. */
@Serializable
enum class StandbyCharger { ANY, WIRED, WIRELESS }

/** Mode nuit (rouge monochrome très sombre). */
@Serializable
enum class StandbyNightMode {
    /** Selon les horaires ou dans le noir (capteur de luminosité). */
    AUTO,
    /** Selon les horaires seulement. */
    SCHEDULE,
    /** Dans le noir seulement. */
    LIGHT,
    OFF,
}

/** Réglages de Veille (mode StandBy). */
@Serializable
data class StandbySettings(
    val enabled: Boolean = false,
    /** Délai avant activation, une fois branché et à l'horizontale (secondes). */
    val delaySeconds: Int = 5,
    /** Pages actives, dans l'ordre. */
    val pages: List<StandbyPage> = listOf(StandbyPage.CLOCKS, StandbyPage.WIDGETS, StandbyPage.MUSIC, StandbyPage.ECHO),
    /** Album du diaporama (identifiant de dossier MediaStore) et son nom. */
    val albumId: String? = null,
    val albumName: String? = null,
    val clock: StandbyClock = StandbyClock.DIGITAL,
    val nightMode: StandbyNightMode = StandbyNightMode.AUTO,
    /** Horaires du mode nuit (minutes depuis minuit). */
    val nightStart: Int = 22 * 60,
    val nightEnd: Int = 7 * 60,
    /** Seuil de luminosité ambiante (lux) sous lequel la pièce est « dans le noir ». */
    val darkLux: Float = 4f,
    val charger: StandbyCharger = StandbyCharger.ANY,
    /** Notifications en cartes discrètes. */
    val notifications: Boolean = true,
    /**
     * Activité de secours lancée d'elle-même (branché + horizontale). Coupée, Veille ne s'affiche
     * que par l'économiseur d'écran du système, un geste ou la tuile.
     */
    val autoLaunch: Boolean = true,
) {
    fun sanitized(): StandbySettings = copy(
        delaySeconds = delaySeconds.coerceIn(DELAY),
        pages = pages.distinct().ifEmpty { listOf(StandbyPage.CLOCKS) },
        nightStart = nightStart.coerceIn(0, 1439),
        nightEnd = nightEnd.coerceIn(0, 1439),
        darkLux = if (darkLux.isNaN()) 4f else darkLux.coerceIn(LUX),
    )

    companion object {
        val DELAY = 0..60
        val LUX = 0f..50f
    }
}
