package com.karelisio.bulle.island.ui

import android.graphics.Typeface
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.karelisio.bulle.core.settings.IslandFont

/** Bornes de l'échelle du texte de l'île. */
val IslandTextScaleRange = 0.85f..1.25f

/** Polices système de l'île. Aucune police n'est téléchargée. */
object IslandFonts {

    /** Famille système nommée, ou null si l'appareil ne la connaît pas (Android renvoie alors la police par défaut). */
    private fun systemFamily(vararg names: String): FontFamily? = names.firstNotNullOfOrNull { name ->
        runCatching { Typeface.create(name, Typeface.NORMAL) }.getOrNull()
            ?.takeIf { it != Typeface.DEFAULT && it != Typeface.SANS_SERIF }
            ?.let { FontFamily(it) }
    }

    /** Police arrondie, quand le constructeur en fournit une. */
    val rounded: FontFamily? by lazy { systemFamily("sans-serif-rounded", "rounded") }

    /** Police étroite (présente sur la plupart des appareils). */
    val condensed: FontFamily? by lazy { systemFamily("sans-serif-condensed") }

    /** Polices proposées sur cet appareil (les indisponibles sont masquées dans les réglages). */
    fun available(): List<IslandFont> = IslandFont.entries.filter { it == IslandFont.DEFAULT || family(it) != null }

    /** Famille à appliquer ; null = police du thème (aucun changement). */
    fun family(font: IslandFont): FontFamily? = when (font) {
        IslandFont.DEFAULT -> null
        IslandFont.ROUNDED -> rounded
        IslandFont.CONDENSED -> condensed
        IslandFont.SERIF -> FontFamily.Serif
        IslandFont.MONO -> FontFamily.Monospace
    }
}

/**
 * Typographie locale de l'île : police et taille appliquées à tous les styles Material.
 * Police par défaut et échelle 1 : on rend [base] tel quel.
 */
fun islandTypography(base: Typography, family: FontFamily?, scale: Float): Typography {
    val sc = scale.coerceIn(IslandTextScaleRange)
    if (family == null && sc == 1f) return base
    fun TextStyle.adapt(): TextStyle = copy(
        fontFamily = family ?: fontFamily,
        fontSize = if (fontSize.isSp) fontSize * sc else fontSize,
        lineHeight = if (lineHeight.isSp) lineHeight * sc else lineHeight,
    )
    return base.copy(
        displayLarge = base.displayLarge.adapt(),
        displayMedium = base.displayMedium.adapt(),
        displaySmall = base.displaySmall.adapt(),
        headlineLarge = base.headlineLarge.adapt(),
        headlineMedium = base.headlineMedium.adapt(),
        headlineSmall = base.headlineSmall.adapt(),
        titleLarge = base.titleLarge.adapt(),
        titleMedium = base.titleMedium.adapt(),
        titleSmall = base.titleSmall.adapt(),
        bodyLarge = base.bodyLarge.adapt(),
        bodyMedium = base.bodyMedium.adapt(),
        bodySmall = base.bodySmall.adapt(),
        labelLarge = base.labelLarge.adapt(),
        labelMedium = base.labelMedium.adapt(),
        labelSmall = base.labelSmall.adapt(),
    )
}
