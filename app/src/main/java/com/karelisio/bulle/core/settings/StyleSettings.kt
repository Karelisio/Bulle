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

/** Police du texte de l'île (polices système uniquement, rien n'est téléchargé). */
@Serializable
enum class IslandFont { DEFAULT, ROUNDED, CONDENSED, SERIF, MONO }

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
    /** Lissage des angles : 0 = arcs de cercle, 1 = « squircle » (superellipse). */
    val cornerSmoothing: Float = 0f,
    /** Police du texte de l'île. */
    val font: IslandFont = IslandFont.DEFAULT,
    /** Échelle du texte de l'île (0.85 – 1.25). */
    val textScale: Float = 1f,
    /** Les textes trop longs des emplacements compacts défilent. */
    val marquee: Boolean = false,
    /** Mode compact sur deux lignes (si le contenu fournit un sous-titre et que la hauteur le permet). */
    val twoLineCompact: Boolean = false,
    /** Largeur maximale en mode compact, en fraction de la largeur d'écran (1 = sans limite). */
    val maxCompactWidth: Float = 1f,
    /** Largeur maximale de la carte étendue, en fraction de la largeur d'écran (1 = sans limite). */
    val maxExpandedWidth: Float = 1f,
    /**
     * Rendu stable : l'île est dessinée dans une fenêtre fixe qui n'est jamais redimensionnée
     * (une petite fenêtre invisible suit l'île pour les touchers). Évite le scintillement.
     */
    val stableWindow: Boolean = true,
    /** Île vide (rien à afficher) : invisible, mais toujours touchable pour les gestes (Deck…). */
    val hideWhenIdle: Boolean = true,
)
