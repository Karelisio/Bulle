package com.karelisio.bulle.core.settings

import kotlinx.serialization.Serializable

/** Forme de l'île. Rayons exprimés en fraction de la demi-hauteur (1 = pilule). */
@Serializable
enum class IslandShape(val topRadius: Float, val bottomRadius: Float) {
    PILL(1f, 1f),
    DROP(0.42f, 1f),
    ROUNDED(0.5f, 0.5f),
}

@Serializable
enum class IslandColorMode { BLACK, DYNAMIC, CUSTOM }

/** Style d'affichage de la carte étendue. */
@Serializable
enum class CardStyle { FLOATING, WIDE }

/** Apparence de l'île. */
@Serializable
data class StyleSettings(
    val shape: IslandShape = IslandShape.PILL,
    /** Échelle de l'île au repos et compacte autour de la caméra (0.8 – 1.6). */
    val size: Float = 1f,
    val colorMode: IslandColorMode = IslandColorMode.BLACK,
    /** Couleur personnalisée (ARGB). */
    val customColor: Long = 0xFF1B1B1F,
    val outline: Boolean = false,
    val outlineWidthDp: Float = 1.2f,
    val shadow: Boolean = false,
    val cardStyle: CardStyle = CardStyle.FLOATING,
    /** Reflet irisé discret qui glisse sur la bulle au changement de contenu. */
    val shimmer: Boolean = true,
)
