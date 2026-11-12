package com.karelisio.bulle.modules.volet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Retour haptique du Volet autorisé (réglage « Retour haptique » de Style → Mouvement).
 * Lu par les composants qui vibrent via View.performHapticFeedback (carte média).
 */
val LocalVoletHaptics = staticCompositionLocalOf { true }

/** Retour haptique neutre : aucune vibration. */
object NoHapticFeedback : HapticFeedback {
    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
}

/**
 * Applique le réglage haptique à tout le contenu : quand il est coupé, LocalHapticFeedback
 * devient neutre (tuiles, curseur, cartes…) et LocalVoletHaptics passe à false.
 */
@Composable
fun ProvideVoletHaptics(enabled: Boolean, content: @Composable () -> Unit) {
    if (enabled) {
        CompositionLocalProvider(LocalVoletHaptics provides true, content = content)
    } else {
        CompositionLocalProvider(
            LocalHapticFeedback provides NoHapticFeedback,
            LocalVoletHaptics provides false,
            content = content,
        )
    }
}
