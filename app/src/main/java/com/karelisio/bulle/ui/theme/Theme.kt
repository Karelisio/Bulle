package com.karelisio.bulle.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.karelisio.bulle.core.a11y.rememberSystemHighContrast
import com.karelisio.bulle.core.settings.SettingsRepository
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Fallback « bleu irisé » quand les couleurs dynamiques ne sont pas disponibles.
private val IrisLight = lightColorScheme(
    primary = Color(0xFF2F5BD8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE2FF),
    onPrimaryContainer = Color(0xFF001550),
    secondary = Color(0xFF6A3FD0),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF22005D),
    tertiary = Color(0xFF00897B),
    tertiaryContainer = Color(0xFFB2F1E8),
    background = Color(0xFFF7F9FF),
    surface = Color(0xFFF7F9FF),
)

private val IrisDark = darkColorScheme(
    primary = Color(0xFFB6C4FF),
    onPrimary = Color(0xFF00287F),
    primaryContainer = Color(0xFF1B43B5),
    onPrimaryContainer = Color(0xFFDCE2FF),
    secondary = Color(0xFFD0BCFF),
    onSecondary = Color(0xFF3B0E95),
    secondaryContainer = Color(0xFF5227B7),
    onSecondaryContainer = Color(0xFFEADDFF),
    tertiary = Color(0xFF7FDBCE),
    tertiaryContainer = Color(0xFF00504A),
    background = Color(0xFF0E1220),
    surface = Color(0xFF0E1220),
)

@Composable
fun bulleColorScheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true): ColorScheme {
    val context = LocalContext.current
    return when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> IrisDark
        else -> IrisLight
    }
}

/** Contraste élevé actif dans l'appli (contours de cartes, focus visibles). */
val LocalHighContrast = staticCompositionLocalOf { false }

/**
 * Schéma à contraste renforcé : fonds purs, textes onSurface noir ou blanc purs, contours
 * nettement visibles. Les couleurs d'accent (primary…) sont conservées.
 */
fun highContrastScheme(base: ColorScheme, dark: Boolean): ColorScheme {
    val bg = if (dark) Color.Black else Color.White
    val fg = if (dark) Color.White else Color.Black
    return base.copy(
        background = bg,
        surface = bg,
        onBackground = fg,
        onSurface = fg,
        onSurfaceVariant = fg,
        surfaceContainerLowest = bg,
        surfaceContainerLow = if (dark) Color(0xFF0D0D0D) else Color(0xFFF7F7F7),
        surfaceContainer = if (dark) Color(0xFF141414) else Color(0xFFF0F0F0),
        surfaceContainerHigh = if (dark) Color(0xFF1A1A1A) else Color(0xFFEBEBEB),
        surfaceContainerHighest = if (dark) Color(0xFF222222) else Color(0xFFE3E3E3),
        outline = if (dark) Color(0xFFF0F0F0) else Color(0xFF1A1A1A),
        outlineVariant = if (dark) Color(0xFFBDBDBD) else Color(0xFF424242),
    )
}

/** Contraste élevé effectif : réglage de Bulle, ou « Texte à contraste élevé » du système si suivi. */
@Composable
fun rememberHighContrast(): Boolean {
    val context = LocalContext.current
    val settings = remember(context) {
        SettingsRepository.flow(context).map { it.accessibility }.distinctUntilChanged()
    }
    val a11y by settings.collectAsState(initial = null)
    val system by rememberSystemHighContrast()
    return a11y?.highContrastActive(system) ?: false
}

@Composable
fun BulleTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    /** null = suit le réglage d'accessibilité (contraste élevé). */
    highContrast: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val hc = highContrast ?: rememberHighContrast()
    val base = bulleColorScheme(dark, dynamic)
    val scheme = remember(base, hc, dark) { if (hc) highContrastScheme(base, dark) else base }
    CompositionLocalProvider(LocalHighContrast provides hc) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            typography = BulleTypography,
            content = content,
        )
    }
}
