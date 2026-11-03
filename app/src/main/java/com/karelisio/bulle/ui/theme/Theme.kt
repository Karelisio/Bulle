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

@Composable
fun BulleTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = bulleColorScheme(dark, dynamic),
        motionScheme = MotionScheme.expressive(),
        typography = BulleTypography,
        content = content,
    )
}
