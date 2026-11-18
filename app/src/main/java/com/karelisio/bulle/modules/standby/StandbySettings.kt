package com.karelisio.bulle.modules.standby

import kotlinx.serialization.Serializable

/** Pages de Veille (balayer pour changer). */
@Serializable
enum class StandbyPage { CLOCKS, WIDGETS, MUSIC, PHOTOS, ECHO, SCENE }

/** Widgets qu'un panneau de Veille peut afficher. */
@Serializable
enum class StandbyWidget { CLOCK, DATE, WEATHER, EVENT, BATTERY, LIVE, MUSIC, LYRICS, QUEUE, PHOTOS, ECHO, COUNTDOWN, SCENE }

/**
 * Composition d'une page : deux panneaux (gauche, droite), chacun une pile de widgets qu'on fait
 * défiler verticalement ; [leftWeight] = largeur du panneau gauche par rapport au droit.
 */
@Serializable
data class StandbyLayout(
    val left: List<StandbyWidget>,
    val right: List<StandbyWidget>,
    val leftWeight: Float = 1f,
)

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
    val delaySeconds: Int = 2,
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
    /** Compositions personnalisées des pages (absente = préréglage de la page). */
    val layouts: Map<StandbyPage, StandbyLayout> = emptyMap(),
    /** Horaire : en charge dans cette plage, Veille s'active même sans être à l'horizontale. */
    val scheduleEnabled: Boolean = false,
    val scheduleStart: Int = 22 * 60,
    val scheduleEnd: Int = 7 * 60,
    /** Scènes animées (page Scène et widget Scène). */
    val scene: SceneSettings = SceneSettings(),
) {
    fun sanitized(): StandbySettings = copy(
        delaySeconds = delaySeconds.coerceIn(DELAY),
        pages = pages.distinct().ifEmpty { listOf(StandbyPage.CLOCKS) },
        nightStart = nightStart.coerceIn(0, 1439),
        nightEnd = nightEnd.coerceIn(0, 1439),
        darkLux = if (darkLux.isNaN()) 4f else darkLux.coerceIn(LUX),
        scene = scene.sanitized(),
        layouts = layouts.mapValues { (_, l) ->
            l.copy(
                left = l.left.distinct().take(MAX_STACK),
                right = l.right.distinct().take(MAX_STACK),
                leftWeight = if (l.leftWeight.isNaN()) 1f else l.leftWeight.coerceIn(WEIGHT),
            )
        },
    )

    companion object {
        val DELAY = 0..60
        val LUX = 0f..50f
        val WEIGHT = 0.5f..2.5f
        const val MAX_STACK = 6
    }
}
