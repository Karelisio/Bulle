package com.karelisio.bulle.core.backup

import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.island.ui.IslandTextScaleRange
import com.karelisio.bulle.modules.alerts.AlertStyle
import com.karelisio.bulle.modules.deck.DeckSettings
import com.karelisio.bulle.modules.deck.DeckTab

/**
 * Bornes des réglages numériques, alignées sur les curseurs de l'appli.
 * Une sauvegarde modifiée à la main (ou corrompue) ne doit jamais produire une île
 * géante, invisible ou hors de l'écran.
 */
object SettingsBounds {
    val SIZE = 0.5f..1.6f
    val SPEED = 0.5f..2f
    val OUTLINE_WIDTH_DP = 0.5f..3f
    val CORNER_SMOOTHING = 0f..1f
    val MAX_COMPACT_WIDTH = 0.3f..1f
    val MAX_EXPANDED_WIDTH = 0.5f..1f

    val OFFSET_X_DP = -60f..60f
    val OFFSET_Y_DP = -20f..40f
    val EXTRA_WIDTH_DP = -30f..120f
    val EXTRA_HEIGHT_DP = -12f..30f

    /** Hauteur des zones du Volet : 0 = auto, sinon 12 – 64 dp. */
    const val VOLET_TRIGGER_MIN = 12f
    const val VOLET_TRIGGER_MAX = 64f
    val VOLET_EDGE_DP = 0f..64f
    val VOLET_BLUR_DP = 0f..80f
    val VOLET_SCRIM = 0.2f..0.95f
    val VOLET_CORNER_DP = 8f..40f
    val VOLET_TILES = 4..6

    /** Seuils de batterie faible acceptés (en %). */
    val BATTERY_LEVEL = 1..99
    const val MAX_BATTERY_LEVELS = 5
    val PAUSED_HIDE_MINUTES = 1..240
    val MINUTE_OF_DAY = 0..1439
    const val MAX_ARGB = 0xFFFFFFFFL
}

/** Valeur bornée ; une valeur non numérique (NaN) reprend la valeur par défaut. */
private fun Float.clamp(range: ClosedFloatingPointRange<Float>, default: Float): Float =
    if (isNaN()) default else coerceIn(range)

private fun Long.isArgb() = this in 0L..SettingsBounds.MAX_ARGB

/**
 * Réglages ramenés dans leurs plages valides (fonction pure, sans contexte Android).
 * Les valeurs déjà valides sont laissées telles quelles.
 */
fun BulleSettings.sanitized(): BulleSettings {
    val d = BulleSettings()
    val b = SettingsBounds
    return copy(
        position = position.copy(
            offsetXDp = position.offsetXDp.clamp(b.OFFSET_X_DP, d.position.offsetXDp),
            offsetYDp = position.offsetYDp.clamp(b.OFFSET_Y_DP, d.position.offsetYDp),
            extraWidthDp = position.extraWidthDp.clamp(b.EXTRA_WIDTH_DP, d.position.extraWidthDp),
            extraHeightDp = position.extraHeightDp.clamp(b.EXTRA_HEIGHT_DP, d.position.extraHeightDp),
        ),
        motion = motion.copy(speed = motion.speed.clamp(b.SPEED, d.motion.speed)),
        style = style.copy(
            size = style.size.clamp(b.SIZE, d.style.size),
            customColor = if (style.customColor.isArgb()) style.customColor else d.style.customColor,
            outlineWidthDp = style.outlineWidthDp.clamp(b.OUTLINE_WIDTH_DP, d.style.outlineWidthDp),
            cornerSmoothing = style.cornerSmoothing.clamp(b.CORNER_SMOOTHING, d.style.cornerSmoothing),
            textScale = style.textScale.clamp(IslandTextScaleRange, d.style.textScale),
            maxCompactWidth = style.maxCompactWidth.clamp(b.MAX_COMPACT_WIDTH, d.style.maxCompactWidth),
            maxExpandedWidth = style.maxExpandedWidth.clamp(b.MAX_EXPANDED_WIDTH, d.style.maxExpandedWidth),
        ),
        volet = volet.copy(
            triggerHeightDp = volet.triggerHeightDp.let { h ->
                when {
                    h.isNaN() || h < b.VOLET_TRIGGER_MIN -> 0f
                    else -> h.coerceAtMost(b.VOLET_TRIGGER_MAX)
                }
            },
            edgeInsetDp = volet.edgeInsetDp.clamp(b.VOLET_EDGE_DP, d.volet.edgeInsetDp),
            blurRadiusDp = volet.blurRadiusDp.clamp(b.VOLET_BLUR_DP, d.volet.blurRadiusDp),
            scrimOpacity = volet.scrimOpacity.clamp(b.VOLET_SCRIM, d.volet.scrimOpacity),
            cornerRadiusDp = volet.cornerRadiusDp.clamp(b.VOLET_CORNER_DP, d.volet.cornerRadiusDp),
            collapsedTileCount = volet.collapsedTileCount.coerceIn(b.VOLET_TILES),
            tiles = volet.tiles.distinct(),
        ),
        alerts = alerts.copy(
            lowBatteryLevels = alerts.lowBatteryLevels
                .filter { it in b.BATTERY_LEVEL }
                .distinct()
                .sortedDescending()
                .take(b.MAX_BATTERY_LEVELS),
            styles = alerts.styles
                .mapValues { (_, s) -> s.sanitized() }
                .filterValues { !it.isDefault },
        ),
        music = music.copy(pausedHideMinutes = music.pausedHideMinutes.coerceIn(b.PAUSED_HIDE_MINUTES)),
        rules = rules.copy(rules = rules.rules.map { it.sanitized() }),
        deck = deck.sanitized(),
    )
}

private fun AlertStyle.sanitized() = copy(
    accent = accent?.takeIf { it.isArgb() },
    durationSec = durationSec?.coerceIn(AlertStyle.MIN_SECONDS, AlertStyle.MAX_SECONDS),
)

/** Au moins un onglet, identifiants d'onglets uniques, widgets sans doublon (un widget n'a qu'une place). */
private fun DeckSettings.sanitized(): DeckSettings {
    val seenTabs = HashSet<String>()
    val seenWidgets = HashSet<Int>()
    val cleaned = tabs.map { tab ->
        val id = if (tab.id.isNotBlank() && seenTabs.add(tab.id)) tab.id else DeckTab().id.also { seenTabs.add(it) }
        tab.copy(
            id = id,
            favorites = tab.favorites.distinct(),
            widgets = tab.widgets.filter { it > 0 && seenWidgets.add(it) },
        )
    }
    return copy(tabs = cleaned.ifEmpty { DeckSettings().tabs })
}

/**
 * Retire des réglages ce qui n'existe pas sur cet appareil (fonction pure : les tests
 * d'existence sont fournis par l'appelant).
 * - favoris du Deck dont l'activité n'est pas installée ;
 * - widgets du Deck non liés sur cet appareil (identifiants d'un autre téléphone).
 * Les listes d'applis exclues sont gardées : sans effet si l'appli est absente, utiles si
 * elle est installée plus tard.
 */
fun BulleSettings.forDevice(
    activityExists: (String) -> Boolean,
    widgetBound: (Int) -> Boolean,
): BulleSettings = copy(
    deck = deck.copy(
        tabs = deck.tabs.map { tab ->
            tab.copy(
                favorites = tab.favorites.filter(activityExists),
                widgets = tab.widgets.filter(widgetBound),
            )
        },
    ),
)

/** Tous les identifiants de widgets du Deck. */
fun BulleSettings.deckWidgetIds(): Set<Int> = deck.tabs.flatMapTo(LinkedHashSet()) { it.widgets }
