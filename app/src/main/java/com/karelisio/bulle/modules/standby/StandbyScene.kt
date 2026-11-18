package com.karelisio.bulle.modules.standby

import kotlinx.serialization.Serializable
import kotlin.math.exp
import kotlin.math.floor

/** Scènes animées de Veille (économiseur d'écran). */
@Serializable
enum class StandbyScene {
    /** Bulles liquides qui fusionnent et se séparent (lampe à lave). */
    LAVA,
    /** Bulles de savon irisées qui montent et éclatent. */
    BUBBLES,
    /** Aurore boréale : rideaux de lumière qui ondulent. */
    AURORA,
    /** Vagues qui suivent la musique (lentes sans musique). */
    WAVES,
    /** Constellation : points qui dérivent et se relient. */
    STARS,
    /** Horloge liquide : les chiffres se transforment en bulles à chaque minute. */
    LIQUID_CLOCK,
}

/** Palette d'une scène. */
@Serializable
enum class ScenePalette {
    /** Couleurs Material You (fond d'écran). */
    DYNAMIC,
    /** Couleur de la pochette en cours (Material You sans musique). */
    ARTWORK,
    OCEAN,
    SUNSET,
    FOREST,
    /** Blanc et gris, le plus discret. */
    MONO,
}

/** Réglages des scènes (page Scène plein écran et widget Scène en vue scindée). */
@Serializable
data class SceneSettings(
    val scene: StandbyScene = StandbyScene.LAVA,
    val palette: ScenePalette = ScenePalette.DYNAMIC,
    /** Vitesse de l'animation (1 = normale). */
    val speed: Float = 1f,
    /** Densité : nombre de bulles, d'étoiles, de vagues (1 = normale). */
    val density: Float = 1f,
    /** Horloge discrète par-dessus la scène (page plein écran). */
    val clock: Boolean = true,
    /** Une notification fait pulser la scène de la couleur de l'icône. */
    val react: Boolean = true,
    /** Change de scène toutes les N minutes (0 = jamais). */
    val cycleMinutes: Int = 0,
    /** 30 images/s et moins d'éléments en économie d'énergie. */
    val eco: Boolean = true,
) {
    fun sanitized(): SceneSettings = copy(
        speed = if (speed.isNaN()) 1f else speed.coerceIn(SPEED),
        density = if (density.isNaN()) 1f else density.coerceIn(DENSITY),
        cycleMinutes = cycleMinutes.coerceIn(CYCLE),
    )

    companion object {
        val SPEED = 0.25f..2f
        val DENSITY = 0.5f..2f
        val CYCLE = 0..60
    }
}

/** Logique pure des scènes (testée). */
object SceneLogic {

    /** Scène à afficher : la choisie, ou la rotation (toutes les [SceneSettings.cycleMinutes]). */
    fun sceneAt(s: SceneSettings, nowMs: Long): StandbyScene {
        if (s.cycleMinutes <= 0) return s.scene
        val all = StandbyScene.entries
        val step = (nowMs / (s.cycleMinutes * 60_000L)).toInt()
        return all[((all.indexOf(s.scene) + step) % all.size + all.size) % all.size]
    }

    /** Intervalle entre deux images (ms) : 60 i/s, 30 en économie, 1 sans animations système. */
    fun frameIntervalMs(powerSave: Boolean, eco: Boolean, reduceMotion: Boolean): Long = when {
        reduceMotion -> 1000L
        powerSave && eco -> 33L
        else -> 0L
    }

    /** Facteur de densité effectif (moins d'éléments en économie). */
    fun effectiveDensity(s: SceneSettings, powerSave: Boolean): Float =
        s.sanitized().density * if (powerSave && s.eco) 0.6f else 1f

    /** Nombre d'éléments d'une scène pour un [base] donné (au moins 1). */
    fun count(base: Int, density: Float): Int = (base * density).toInt().coerceAtLeast(1)

    /** Enveloppe de la pulsation d'une notification (montée rapide, décrue de 2,5 s) : 0 – 1. */
    fun pulse(elapsedMs: Long): Float = when {
        elapsedMs < 0 -> 0f
        elapsedMs < 180 -> elapsedMs / 180f
        elapsedMs < PULSE_MS -> exp(-(elapsedMs - 180) / 700f).toFloat()
        else -> 0f
    }

    const val PULSE_MS = 2_500L

    /**
     * Position de l'horloge par-dessus la scène (fractions de l'écran, centre du texte) : elle change
     * de coin toutes les [periodMs] pour ne pas marquer l'écran OLED.
     */
    fun clockAnchor(nowMs: Long, periodMs: Long = 5 * 60_000L): Pair<Float, Float> {
        val anchors = arrayOf(0.22f to 0.80f, 0.78f to 0.80f, 0.78f to 0.22f, 0.22f to 0.22f, 0.5f to 0.82f, 0.5f to 0.18f)
        return anchors[((nowMs / periodMs) % anchors.size).toInt()]
    }

    /** Durée du déplacement de l'horloge d'un coin à l'autre (ms). */
    const val CLOCK_MOVE_MS = 2_400L

    /** Avancement 0 – 1 du déplacement de l'horloge, [elapsedMs] après son début. */
    fun clockTransition(elapsedMs: Long, durationMs: Long = CLOCK_MOVE_MS): Float =
        (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)

    /** Phase de disparition (0 – 1) de l'ancienne horloge, pendant les 45 premiers % du déplacement. */
    fun clockOut(p: Float): Float = (p / 0.45f).coerceIn(0f, 1f)

    /** Phase d'apparition (0 – 1) de la nouvelle horloge, à partir de 40 % du déplacement. */
    fun clockIn(p: Float): Float = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)

    /**
     * Coin haut-gauche du bloc horloge ([blockW] × [blockH]) centré sur [anchor] (fractions de
     * l'écran [w] × [h]), sans sortir de l'écran ni de la marge [margin].
     */
    fun clockOrigin(anchor: Pair<Float, Float>, blockW: Float, blockH: Float, w: Float, h: Float, margin: Float): Pair<Float, Float> {
        val x = (w * anchor.first - blockW / 2).coerceIn(margin, (w - blockW - margin).coerceAtLeast(margin))
        val y = (h * anchor.second - blockH / 2).coerceIn(margin, (h - blockH - margin).coerceAtLeast(margin))
        return x to y
    }

    /** Pas d'échantillonnage des chiffres (particules) pour en garder au plus [max] environ. */
    fun glyphStep(w: Int, h: Int, max: Int = 700): Int =
        kotlin.math.ceil(kotlin.math.sqrt(w.toFloat() * h * 0.3f / max)).toInt().coerceAtLeast(2)

    /** Courbe « retour élastique » (dépasse un peu 1 avant de s'y poser). */
    fun easeOutBack(x: Float): Float {
        val c = 1.6f
        val u = x - 1f
        return 1f + (c + 1f) * u * u * u + c * u * u
    }

    /** Courbe douce en S. */
    fun easeInOut(x: Float): Float = x * x * (3f - 2f * x)

    /** Pseudo-aléatoire stable dans [0, 1[ pour l'élément [i] (graine [salt]). */
    fun rand(i: Int, salt: Int): Float {
        var x = i * 374_761_393 + salt * 668_265_263
        x = (x xor (x ushr 13)) * 1_274_126_177
        x = x xor (x ushr 16)
        return (x.toLong() and 0xFFFFFF).toFloat() / 0x1000000
    }

    /** Partie fractionnaire positive. */
    fun frac(x: Float): Float = x - floor(x)

    /**
     * Cycle de vie d'une bulle de savon [i] à l'instant [t] (s) : progression 0 – 1 de sa montée,
     * et éclatement (0 – 1) passé le point où elle éclate (null = pas encore).
     */
    fun bubbleLife(i: Int, t: Float): Pair<Float, Float?> {
        val period = 9f + 8f * rand(i, 3)
        val life = frac(t / period + rand(i, 4))
        val popAt = 0.6f + 0.35f * rand(i, 5)
        return if (life < popAt) (life / popAt) to null else 1f to ((life - popAt) / (1f - popAt)).coerceIn(0f, 1f)
    }

    /** Couleurs fixes des palettes (ARGB), hors Material You et pochette. */
    fun fixedPalette(p: ScenePalette): List<Long>? = when (p) {
        ScenePalette.OCEAN -> listOf(0xFF1E88E5, 0xFF00ACC1, 0xFF26C6DA, 0xFF3949AB)
        ScenePalette.SUNSET -> listOf(0xFFFF7043, 0xFFEC407A, 0xFFFFB300, 0xFFAB47BC)
        ScenePalette.FOREST -> listOf(0xFF43A047, 0xFF00897B, 0xFF9CCC65, 0xFF26A69A)
        ScenePalette.MONO -> listOf(0xFFE0E0E0, 0xFF9E9E9E, 0xFFBDBDBD, 0xFF757575)
        ScenePalette.DYNAMIC, ScenePalette.ARTWORK -> null
    }
}
