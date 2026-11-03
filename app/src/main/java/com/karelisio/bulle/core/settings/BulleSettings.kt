package com.karelisio.bulle.core.settings

import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.call.CallSettings
import com.karelisio.bulle.modules.live.LiveSettings
import com.karelisio.bulle.modules.music.MusicSettings
import kotlinx.serialization.Serializable

/**
 * Tous les réglages de Bulle, sérialisés en JSON (DataStore).
 * Chaque champ a une valeur par défaut : on peut en ajouter sans migration
 * et la sauvegarde/restauration JSON reste compatible.
 */
@Serializable
data class BulleSettings(
    val islandEnabled: Boolean = true,
    val position: PositionTweak = PositionTweak(),
    val visibility: VisibilitySettings = VisibilitySettings(),
    val motion: MotionSettings = MotionSettings(),
    /** Activation par module (id → activé). Absent = valeur par défaut du module. */
    val modules: Map<String, Boolean> = emptyMap(),
    // Réglages propres à chaque module (un fichier par module).
    val music: MusicSettings = MusicSettings(),
    val live: LiveSettings = LiveSettings(),
    val calls: CallSettings = CallSettings(),
    val alerts: AlertSettings = AlertSettings(),
)

/** Réglage fin manuel, en dp, appliqué par-dessus la position calée sur le cutout. */
@Serializable
data class PositionTweak(
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val extraWidthDp: Float = 0f,
    val extraHeightDp: Float = 0f,
)

/** Quand masquer l'île automatiquement. */
@Serializable
data class VisibilitySettings(
    val hideInFullscreen: Boolean = true,
    val hideInLandscape: Boolean = true,
    val hideOnLockscreen: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
)

@Serializable
enum class MotionStyle { SOFT, SNAPPY, SOBER }

/** Style et vitesse des animations de l'île. */
@Serializable
data class MotionSettings(
    val style: MotionStyle = MotionStyle.SOFT,
    /** Multiplicateur de vitesse (0.5 = lent, 2 = rapide). */
    val speed: Float = 1f,
)
