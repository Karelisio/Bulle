package com.karelisio.bulle.core.style

import com.karelisio.bulle.core.backup.SettingsBounds
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.CardStyle
import com.karelisio.bulle.core.settings.IslandColorMode
import com.karelisio.bulle.core.settings.IslandFont
import com.karelisio.bulle.core.settings.IslandShape
import com.karelisio.bulle.core.settings.MotionSettings
import com.karelisio.bulle.core.settings.MotionStyle
import com.karelisio.bulle.core.settings.StyleSettings
import com.karelisio.bulle.island.ui.IslandTextScaleRange
import kotlinx.serialization.Serializable

/**
 * Partie « apparence » d'un style : sous-ensemble de [StyleSettings].
 * Exclus volontairement : rendu stable et masquage au repos (propres à l'appareil),
 * ainsi que la position (jamais dans un style).
 */
@Serializable
data class PackStyle(
    val shape: IslandShape = IslandShape.PILL,
    val size: Float = 1f,
    val colorMode: IslandColorMode = IslandColorMode.BLACK,
    val customColor: Long = 0xFF1B1B1F,
    val outline: Boolean = false,
    val outlineWidthDp: Float = 1.2f,
    val shadow: Boolean = false,
    val cardStyle: CardStyle = CardStyle.FLOATING,
    val shimmer: Boolean = true,
    val cornerSmoothing: Float = 0f,
    val font: IslandFont = IslandFont.DEFAULT,
    val textScale: Float = 1f,
    val marquee: Boolean = false,
    val twoLineCompact: Boolean = false,
    val maxCompactWidth: Float = 1f,
    val maxExpandedWidth: Float = 1f,
)

/** Partie « mouvement » d'un style : sous-ensemble de [MotionSettings] (sans le retour haptique). */
@Serializable
data class PackMotion(
    val style: MotionStyle = MotionStyle.SOFT,
    val speed: Float = 1f,
    val blurTransitions: Boolean = true,
    /** moduleId (ou "default") → nom d'[EntryAnimation]. */
    val entryAnimations: Map<String, String> = emptyMap(),
)

/** Un style complet, nommé, partageable. */
@Serializable
data class StylePack(
    val name: String = "",
    val style: PackStyle = PackStyle(),
    val motion: PackMotion = PackMotion(),
) {
    /** Réglages avec ce style appliqué : tout le reste (position, visibilité, modules…) est intact. */
    fun applyTo(settings: BulleSettings): BulleSettings = settings.copy(
        style = settings.style.copy(
            shape = style.shape,
            size = style.size,
            colorMode = style.colorMode,
            customColor = style.customColor,
            outline = style.outline,
            outlineWidthDp = style.outlineWidthDp,
            shadow = style.shadow,
            cardStyle = style.cardStyle,
            shimmer = style.shimmer,
            cornerSmoothing = style.cornerSmoothing,
            font = style.font,
            textScale = style.textScale,
            marquee = style.marquee,
            twoLineCompact = style.twoLineCompact,
            maxCompactWidth = style.maxCompactWidth,
            maxExpandedWidth = style.maxExpandedWidth,
        ),
        motion = settings.motion.copy(
            style = motion.style,
            speed = motion.speed,
            blurTransitions = motion.blurTransitions,
            entryAnimations = EntryAnimations.sanitize(motion.entryAnimations),
        ),
    )

    /** Mêmes réglages visuels (le nom n'entre pas en compte). */
    fun sameLookAs(other: StylePack): Boolean = style == other.style && motion == other.motion

    companion object {
        const val MAX_NAME_LENGTH = 40

        /** Style actuel de l'île, sous le nom [name]. */
        fun capture(settings: BulleSettings, name: String = ""): StylePack {
            val s = settings.style
            val m = settings.motion
            return StylePack(
                name = name,
                style = PackStyle(
                    shape = s.shape, size = s.size, colorMode = s.colorMode, customColor = s.customColor,
                    outline = s.outline, outlineWidthDp = s.outlineWidthDp, shadow = s.shadow,
                    cardStyle = s.cardStyle, shimmer = s.shimmer, cornerSmoothing = s.cornerSmoothing,
                    font = s.font, textScale = s.textScale, marquee = s.marquee,
                    twoLineCompact = s.twoLineCompact, maxCompactWidth = s.maxCompactWidth,
                    maxExpandedWidth = s.maxExpandedWidth,
                ),
                motion = PackMotion(
                    style = m.style, speed = m.speed, blurTransitions = m.blurTransitions,
                    entryAnimations = EntryAnimations.sanitize(m.entryAnimations),
                ),
            )
        }

        /** Nom nettoyé (espaces, caractères de contrôle, longueur) ; null s'il est vide. */
        fun cleanName(raw: String): String? = raw
            .filterNot { it.isISOControl() }
            .trim()
            .take(MAX_NAME_LENGTH)
            .takeIf { it.isNotBlank() }
    }
}

/**
 * Validation stricte d'un style importé : toutes les valeurs numériques dans les plages
 * des curseurs, nom raisonnable, animations d'entrée bien formées. Retourne la liste des
 * champs fautifs (vide = valide).
 */
fun StylePack.problems(): List<String> {
    val b = SettingsBounds
    val p = mutableListOf<String>()
    fun Float.check(range: ClosedFloatingPointRange<Float>, field: String) {
        if (isNaN() || this !in range) p += field
    }
    style.size.check(b.SIZE, "style.size")
    style.outlineWidthDp.check(b.OUTLINE_WIDTH_DP, "style.outlineWidthDp")
    style.cornerSmoothing.check(b.CORNER_SMOOTHING, "style.cornerSmoothing")
    style.textScale.check(IslandTextScaleRange, "style.textScale")
    style.maxCompactWidth.check(b.MAX_COMPACT_WIDTH, "style.maxCompactWidth")
    style.maxExpandedWidth.check(b.MAX_EXPANDED_WIDTH, "style.maxExpandedWidth")
    if (style.customColor !in 0L..b.MAX_ARGB) p += "style.customColor"
    motion.speed.check(b.SPEED, "motion.speed")
    if (name.length > StylePack.MAX_NAME_LENGTH || name.any { it.isISOControl() }) p += "name"
    val entries = motion.entryAnimations
    if (entries.size > EntryAnimations.MAX_ENTRIES || entries.keys.any { !EntryAnimations.KeyPattern.matches(it) }) {
        p += "motion.entryAnimations"
    }
    return p
}

fun StylePack.isValid(): Boolean = problems().isEmpty()
