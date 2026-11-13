package com.karelisio.bulle.island.ui

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.karelisio.bulle.R
import com.karelisio.bulle.core.a11y.AnnouncementThrottle
import com.karelisio.bulle.core.a11y.IslandSpeech
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandGesture
import com.karelisio.bulle.core.island.IslandHost
import com.karelisio.bulle.core.island.IslandPanel
import com.karelisio.bulle.core.island.Priority
import com.karelisio.bulle.core.settings.IslandAnnouncements

/**
 * Ce que TalkBack doit savoir de l'île à un instant donné : résumé, état, actions, et s'il faut
 * annoncer le changement (région « live » polie, limitée par [AnnouncementThrottle]).
 */
internal class IslandA11yState(
    /** Résumé explicite (contenu ou panneau). null = les textes du contenu compact sont lus (fusion). */
    val label: String?,
    /** Texte annoncé / repli quand [label] est null. */
    val spoken: String?,
    val state: String?,
    val actions: List<CustomAccessibilityAction>,
    val announce: Boolean,
)

/** Libellé lu d'un panneau ouvert dans l'île (Deck, recherche, traduction, minuteur…). */
internal fun panelLabel(res: IslandSpeech.Res, panel: IslandPanel): String = when (panel.id) {
    "deck" -> res.str(R.string.a11y_panel_deck)
    "app_search" -> res.str(R.string.a11y_panel_search)
    "translate" -> res.str(R.string.a11y_panel_translate)
    "focus" -> res.str(R.string.a11y_panel_focus)
    "breathing" -> res.str(R.string.a11y_panel_breathing)
    else -> res.str(R.string.a11y_island_generic)
}

/** Contenu jugé important pour les annonces : alertes, messages, alarmes, appels. */
internal fun isImportant(content: IslandContent?): Boolean =
    content != null && content.priority >= Priority.ALERT

/** Résumé TalkBack d'un contenu, avec repli (fonction pure, testée). */
internal fun contentLabel(res: IslandSpeech.Res, content: IslandContent): String? =
    runCatching { content.describe(res) }.getOrNull()?.takeIf { it.isNotBlank() }

@Composable
internal fun rememberIslandA11y(
    primary: IslandContent?,
    panel: IslandPanel?,
    expanded: Boolean,
    hasSecondary: Boolean,
    level: IslandAnnouncements,
    host: IslandHost,
    onToggleExpand: () -> Unit,
    onSwap: () -> Unit,
): IslandA11yState {
    val context = LocalContext.current
    val res = remember(context) { IslandSpeech.res(context) }
    val throttle = remember { AnnouncementThrottle(clock = SystemClock::elapsedRealtime) }
    val label = when {
        panel != null -> IslandSpeech.summary(res.str(R.string.a11y_island_name), panelLabel(res, panel))
        primary != null -> contentLabel(res, primary)
        else -> null
    }
    val spoken = label ?: primary?.let { IslandSpeech.fallback(res, it.compactSubtitle) }
    val key = panel?.id?.let { "panel:$it" } ?: primary?.key
    // Décidé une seule fois par changement de résumé (pas à chaque recomposition).
    val announce = remember(key, spoken, level) {
        throttle.offer(key, spoken, important = isImportant(primary) && panel == null, level = level)
    }
    val canExpand = panel == null && primary?.expandable == true
    val state = when {
        expanded -> res.str(R.string.a11y_state_expanded)
        canExpand -> res.str(R.string.a11y_state_collapsed)
        else -> null
    }
    val actions = buildList {
        if (expanded) add(CustomAccessibilityAction(res.str(R.string.a11y_action_collapse)) { onToggleExpand(); true })
        else if (canExpand) add(CustomAccessibilityAction(res.str(R.string.a11y_action_expand)) { onToggleExpand(); true })
        if (panel == null && primary != null) {
            runCatching { primary.accessibilityActions(host, res) }.getOrDefault(emptyList()).forEach { a ->
                add(CustomAccessibilityAction(a.label) { a.run(); true })
            }
        }
        if (hasSecondary && !expanded) add(CustomAccessibilityAction(res.str(R.string.a11y_action_swap)) { onSwap(); true })
    }
    return IslandA11yState(label, spoken, state, actions, announce)
}

/**
 * Sémantique de la surface de l'île. Compact : un seul élément TalkBack (le contenu compact est
 * décoratif) ; étendu : résumé + actions sur la surface, les boutons du contenu restent parcourables.
 */
internal fun Modifier.islandSemantics(
    a11y: IslandA11yState,
    visible: Boolean,
    compact: Boolean,
    idleLabel: String?,
    onGesture: (IslandGesture) -> Unit,
): Modifier {
    if (!visible) return clearAndSetSemantics { }
    if (a11y.spoken == null && idleLabel == null) return clearAndSetSemantics { }
    return semantics(mergeDescendants = compact && a11y.label == null) {
        (a11y.label ?: if (a11y.spoken == null) idleLabel else null)?.let { contentDescription = it }
        a11y.state?.let { stateDescription = it }
        if (a11y.announce) liveRegion = LiveRegionMode.Polite
        if (a11y.actions.isNotEmpty()) customActions = a11y.actions
        onClick { onGesture(IslandGesture.TAP); true }
        onLongClick { onGesture(IslandGesture.LONG_PRESS); true }
    }
}

/** Bulle secondaire : son résumé, et le toucher qui échange les deux bulles. */
@Composable
internal fun Modifier.secondarySemantics(content: IslandContent, onSwap: () -> Unit): Modifier {
    val context = LocalContext.current
    val res = remember(context) { IslandSpeech.res(context) }
    val label = contentLabel(res, content) ?: IslandSpeech.fallback(res, content.compactSubtitle)
    val swap = res.str(R.string.a11y_action_swap)
    return clearAndSetSemantics {
        contentDescription = label
        onClick(label = swap) { onSwap(); true }
    }
}

/** Couleurs de l'île en contraste élevé : texte blanc pur, accents saturés et éclaircis. */
internal fun highContrastIslandScheme(base: androidx.compose.material3.ColorScheme): androidx.compose.material3.ColorScheme =
    base.copy(
        primary = saturate(base.primary),
        secondary = saturate(base.secondary),
        tertiary = saturate(base.tertiary),
        onSurface = Color.White,
        onSurfaceVariant = Color.White,
        onBackground = Color.White,
        outline = Color.White,
        outlineVariant = Color(0xFFE0E0E0),
    )

/** Accent plus saturé et plus lumineux, lisible sur fond noir. */
internal fun saturate(c: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV((c.red * 255).toInt(), (c.green * 255).toInt(), (c.blue * 255).toInt(), hsv)
    // Gris et blancs : pas de teinte à renforcer (sinon ils viraient au rouge).
    if (hsv[1] > 0.08f) hsv[1] = (hsv[1] * 1.6f).coerceIn(0.55f, 1f)
    hsv[2] = hsv[2].coerceAtLeast(0.9f)
    return Color(android.graphics.Color.HSVToColor(hsv))
}
