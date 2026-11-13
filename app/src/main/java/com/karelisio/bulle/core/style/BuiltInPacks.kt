package com.karelisio.bulle.core.style

import com.karelisio.bulle.core.settings.CardStyle
import com.karelisio.bulle.core.settings.IslandColorMode
import com.karelisio.bulle.core.settings.IslandFont
import com.karelisio.bulle.core.settings.IslandShape
import com.karelisio.bulle.core.settings.MotionStyle

/** Un pack intégré : identifiant stable (libellés traduits côté interface) et son style. */
data class BuiltInPack(val id: String, val pack: StylePack)

/**
 * Packs de styles livrés avec Bulle. Tous restent dans les bornes des curseurs
 * (vérifié par les tests) et n'utilisent que des polices système.
 */
object BuiltInPacks {
    private fun entry(animation: EntryAnimation) = mapOf(EntryAnimations.DEFAULT_KEY to animation.name)

    val all: List<BuiltInPack> = listOf(
        // L'île d'origine : utile pour revenir en arrière après des essais.
        BuiltInPack("bulle", StylePack("Bulle")),
        // Noir pur, sans effet : l'île se confond avec la caméra.
        BuiltInPack(
            "ink",
            StylePack(
                "Encre",
                PackStyle(shimmer = false, cornerSmoothing = 0.35f),
                PackMotion(style = MotionStyle.SOBER, blurTransitions = false, entryAnimations = entry(EntryAnimation.FADE)),
            ),
        ),
        // Ardoise givrée, contour fin et angles en superellipse.
        BuiltInPack(
            "frost",
            StylePack(
                "Givre",
                PackStyle(
                    colorMode = IslandColorMode.CUSTOM, customColor = 0xFF27313D, outline = true, outlineWidthDp = 0.8f,
                    shadow = true, cornerSmoothing = 1f, font = IslandFont.ROUNDED,
                ),
                PackMotion(style = MotionStyle.SOFT, speed = 0.9f, entryAnimations = entry(EntryAnimation.SLIDE)),
            ),
        ),
        // Nuit profonde, contour d'accent épais et éclat à chaque arrivée.
        BuiltInPack(
            "neon",
            StylePack(
                "Néon",
                PackStyle(
                    colorMode = IslandColorMode.CUSTOM, customColor = 0xFF0B0820, outline = true, outlineWidthDp = 2f,
                    shadow = true, font = IslandFont.CONDENSED, textScale = 1.05f,
                ),
                PackMotion(style = MotionStyle.PLAYFUL, speed = 1.2f, entryAnimations = entry(EntryAnimation.RIPPLE)),
            ),
        ),
        // Teinte douce tirée du fond d'écran, police arrondie, rebonds tendres.
        BuiltInPack(
            "pastel",
            StylePack(
                "Pastel",
                PackStyle(colorMode = IslandColorMode.DYNAMIC, cornerSmoothing = 0.7f, font = IslandFont.ROUNDED, textScale = 1.05f),
                PackMotion(style = MotionStyle.SOFT, speed = 0.85f, entryAnimations = entry(EntryAnimation.ELASTIC)),
            ),
        ),
        // Coins francs, chasse fixe, ressorts secs : un terminal des années 80.
        BuiltInPack(
            "retro",
            StylePack(
                "Rétro",
                PackStyle(
                    shape = IslandShape.ROUNDED, colorMode = IslandColorMode.CUSTOM, customColor = 0xFF1E1A14,
                    outline = true, outlineWidthDp = 1.5f, shimmer = false, font = IslandFont.MONO,
                    textScale = 0.95f, cardStyle = CardStyle.WIDE,
                ),
                PackMotion(style = MotionStyle.SNAPPY, speed = 1.3f, blurTransitions = false, entryAnimations = entry(EntryAnimation.SLIDE)),
            ),
        ),
        // Le strict nécessaire : plus petite, plus étroite, sans effet.
        BuiltInPack(
            "minimal",
            StylePack(
                "Minimal",
                PackStyle(
                    size = 0.9f, shimmer = false, font = IslandFont.CONDENSED, textScale = 0.9f,
                    maxCompactWidth = 0.6f, maxExpandedWidth = 0.9f, cornerSmoothing = 0.5f,
                ),
                PackMotion(style = MotionStyle.SOBER, speed = 1.1f, blurTransitions = false, entryAnimations = entry(EntryAnimation.FADE)),
            ),
        ),
        // Goutte et métaballes : ressorts joueurs, contenus qui tombent de la caméra.
        BuiltInPack(
            "liquid",
            StylePack(
                "Liquide",
                PackStyle(shape = IslandShape.DROP, cornerSmoothing = 1f, shadow = true),
                PackMotion(style = MotionStyle.PLAYFUL, speed = 0.85f, entryAnimations = entry(EntryAnimation.DROP)),
            ),
        ),
    )

    fun byId(id: String): BuiltInPack? = all.firstOrNull { it.id == id }
}
