package com.karelisio.bulle.modules.volet.media

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Schéma de couleurs de la carte média, tiré de la pochette (couleurs ARGB, logique pure et testable).
 *
 * - [container] : fond de la carte (sous la pochette floutée) ;
 * - [onContainer] / [onContainerVariant] : titre / artiste, nom de l'appli, temps ;
 * - [accent] : barre de progression, fond du bouton lecture ; [onAccent] : icône posée dessus ;
 * - [track] : partie restante de la barre de progression.
 */
data class MediaScheme(
    val container: Int,
    val onContainer: Int,
    val onContainerVariant: Int,
    val accent: Int,
    val onAccent: Int,
    val track: Int,
)

/** Calcul du schéma tonal (clair/sombre) et garanties de contraste. */
object MediaColors {

    /** Contraste minimal du texte principal (WCAG AA, texte normal). */
    const val TEXT_CONTRAST = 4.5

    /** Contraste minimal du texte secondaire et des contrôles (WCAG AA, grands éléments). */
    const val UI_CONTRAST = 3.0

    /**
     * Part de la pochette floutée visible à travers le fond : le texte doit rester lisible quelle
     * que soit l'image (le contraste est vérifié contre le fond mêlé de noir et de blanc).
     */
    const val ARTWORK_ALPHA = 0.26f

    /**
     * Schéma tonal à partir d'une couleur de fond de la pochette ([seed], la teinte dominante)
     * et d'une couleur vive ([vivid], pour l'accent). Sans pochette : [fallback] (couleur du thème).
     */
    fun scheme(seed: Int?, vivid: Int?, dark: Boolean, fallback: Int): MediaScheme {
        val base = toHsl(seed ?: fallback)
        val accentSeed = toHsl(vivid ?: seed ?: fallback)
        // Fond : teinte de la pochette, saturation contenue, ton très sombre (sombre) ou très clair (clair).
        val sat = min(base[1], if (dark) 0.42f else 0.48f)
        val container = fromHsl(base[0], sat, if (dark) 0.17f else 0.89f)
        val backgrounds = backdrops(container)

        val onContainer = ensureContrast(fromHsl(base[0], min(sat, 0.25f), if (dark) 0.95f else 0.10f), backgrounds, TEXT_CONTRAST)
        val onVariant = ensureContrast(fromHsl(base[0], min(sat, 0.22f), if (dark) 0.80f else 0.28f), backgrounds, UI_CONTRAST)
        // Accent : couleur vive, saturation remontée si la pochette est colorée (une pochette grise reste grise).
        val accentSat = if (accentSeed[1] > 0.08f) accentSeed[1].coerceIn(0.5f, 0.85f) else accentSeed[1]
        val accent = ensureContrast(fromHsl(accentSeed[0], accentSat, if (dark) 0.74f else 0.38f), backgrounds, UI_CONTRAST)
        // Icône posée sur l'accent : très sombre ou très claire, la plus contrastée des deux.
        val onAccentDark = fromHsl(accentSeed[0], 0.4f, 0.10f)
        val onAccentLight = fromHsl(accentSeed[0], 0.2f, 0.98f)
        val onAccent = if (contrast(onAccentDark, accent) >= contrast(onAccentLight, accent)) onAccentDark else onAccentLight
        return MediaScheme(container, onContainer, onVariant, accent, onAccent, withAlpha(onContainer, 0.24f))
    }

    /** Fonds réellement vus sous le texte : le fond seul, ou mêlé à une pochette très sombre ou très claire. */
    fun backdrops(container: Int): List<Int> = listOf(
        container,
        blend(container, BLACK, ARTWORK_ALPHA),
        blend(container, WHITE, ARTWORK_ALPHA),
    )

    /**
     * Ajuste la luminosité de [fg] (vers le clair sur fond sombre, vers le sombre sur fond clair)
     * jusqu'à atteindre [min] contre chacun des [backgrounds]. Repli : blanc ou noir, le meilleur.
     */
    fun ensureContrast(fg: Int, backgrounds: List<Int>, min: Double): Int {
        fun worst(c: Int) = backgrounds.minOf { contrast(c, it) }
        if (worst(fg) >= min) return fg
        val hsl = toHsl(fg)
        val up = backgrounds.map { luminance(it) }.average() < 0.18
        var l = hsl[2]
        repeat(40) {
            l = (if (up) l + 0.025f else l - 0.025f).coerceIn(0f, 1f)
            val c = fromHsl(hsl[0], hsl[1], l)
            if (worst(c) >= min) return c
        }
        return if (worst(WHITE) >= worst(BLACK)) WHITE else BLACK
    }

    /** Luminance relative WCAG (0 – 1). */
    fun luminance(argb: Int): Double {
        fun ch(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch((argb shr 16) and 0xFF) + 0.7152 * ch((argb shr 8) and 0xFF) + 0.0722 * ch(argb and 0xFF)
    }

    /** Rapport de contraste WCAG entre deux couleurs opaques (1 – 21). */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** Mélange opaque : [top] posé sur [bottom] avec l'opacité [alpha]. */
    fun blend(bottom: Int, top: Int, alpha: Float): Int {
        fun mix(shift: Int): Int {
            val b = (bottom shr shift) and 0xFF
            val t = (top shr shift) and 0xFF
            return (b + (t - b) * alpha).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    fun withAlpha(argb: Int, alpha: Float): Int =
        (round(alpha.coerceIn(0f, 1f) * 255f).toInt() shl 24) or (argb and 0x00FFFFFF)

    /** RVB → TSL (teinte en degrés, saturation et luminosité 0 – 1). */
    fun toHsl(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d == 0f) return floatArrayOf(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(h, s.coerceIn(0f, 1f), l)
    }

    /** TSL → ARGB opaque. */
    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val hue = ((h % 360f) + 360f) % 360f
        val c = (1f - abs(2f * l - 1f)) * s
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = l - c / 2f
        val (r, g, b) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(v: Float) = round((v + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
