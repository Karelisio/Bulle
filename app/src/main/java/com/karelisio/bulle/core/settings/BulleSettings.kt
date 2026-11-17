package com.karelisio.bulle.core.settings

import com.karelisio.bulle.core.actions.GestureSettings
import com.karelisio.bulle.core.backup.BackupSettings
import com.karelisio.bulle.core.update.UpdateSettings
import com.karelisio.bulle.modules.alerts.AlertSettings
import com.karelisio.bulle.modules.call.CallSettings
import com.karelisio.bulle.modules.deck.DeckSettings
import com.karelisio.bulle.modules.live.LiveSettings
import com.karelisio.bulle.modules.music.MusicSettings
import com.karelisio.bulle.modules.rules.RulesSettings
import com.karelisio.bulle.modules.volet.VoletSettings
import kotlinx.serialization.Serializable

/**
 * Tous les réglages de Bulle, sérialisés en JSON (DataStore).
 * Chaque champ a une valeur par défaut : on peut en ajouter sans migration
 * et la sauvegarde/restauration JSON reste compatible.
 */
@Serializable
data class BulleSettings(
    val islandEnabled: Boolean = true,
    /** Onboarding terminé (premier lancement). */
    val onboardingDone: Boolean = false,
    val position: PositionTweak = PositionTweak(),
    val visibility: VisibilitySettings = VisibilitySettings(),
    val motion: MotionSettings = MotionSettings(),
    val style: StyleSettings = StyleSettings(),
    val gestures: GestureSettings = GestureSettings(),
    val deck: DeckSettings = DeckSettings(),
    val rules: RulesSettings = RulesSettings(),
    val volet: VoletSettings = VoletSettings(),
    val calendar: com.karelisio.bulle.modules.calendar.CalendarSettings = com.karelisio.bulle.modules.calendar.CalendarSettings(),
    val weather: com.karelisio.bulle.modules.weather.WeatherSettings = com.karelisio.bulle.modules.weather.WeatherSettings(),
    val network: com.karelisio.bulle.modules.network.NetworkSettings = com.karelisio.bulle.modules.network.NetworkSettings(),
    val battery: com.karelisio.bulle.modules.battery.BatterySettings = com.karelisio.bulle.modules.battery.BatterySettings(),
    val update: UpdateSettings = UpdateSettings(),
    val backup: BackupSettings = BackupSettings(),
    /** Accessibilité : contraste élevé, annonces TalkBack de l'île. */
    val accessibility: AccessibilitySettings = AccessibilitySettings(),
    /** Activation par module (id → activé). Absent = valeur par défaut du module. */
    val modules: Map<String, Boolean> = emptyMap(),
    // Réglages propres à chaque module (un fichier par module).
    val music: MusicSettings = MusicSettings(),
    val live: LiveSettings = LiveSettings(),
    val calls: CallSettings = CallSettings(),
    val alerts: AlertSettings = AlertSettings(),
    /** Halo : bords lumineux à chaque notification. */
    val halo: com.karelisio.bulle.modules.halo.HaloSettings = com.karelisio.bulle.modules.halo.HaloSettings(),
    /** Bords : gestes sur les bords de l'écran. */
    val edges: com.karelisio.bulle.modules.edges.EdgeSettings = com.karelisio.bulle.modules.edges.EdgeSettings(),
    /** Veille : mode StandBy en charge à l'horizontale. */
    val standby: com.karelisio.bulle.modules.standby.StandbySettings = com.karelisio.bulle.modules.standby.StandbySettings(),
    /** Profils (Travail, Nuit, Jeu…) qui changent plusieurs réglages d'un coup. */
    val profiles: com.karelisio.bulle.modules.profiles.ProfilesSettings = com.karelisio.bulle.modules.profiles.ProfilesSettings(),
)

/** Réglage fin manuel, en dp, appliqué par-dessus la position calée sur le cutout. */
@Serializable
data class PositionTweak(
    val offsetXDp: Float = 0f,
    val offsetYDp: Float = 0f,
    val extraWidthDp: Float = 0f,
    val extraHeightDp: Float = 0f,
    /** Sens dans lequel l'île s'agrandit depuis la caméra (centré par défaut). */
    val openDirection: OpenDirection = OpenDirection.CENTER,
)

/**
 * Sens d'ouverture de l'île : centré sur la caméra, ou bord côté caméra fixe et
 * agrandissement vers la gauche / la droite (pratique pour un trou de caméra décentré).
 */
@Serializable
enum class OpenDirection { CENTER, LEFT, RIGHT }

/** Quand masquer l'île automatiquement. */
@Serializable
data class VisibilitySettings(
    val hideInFullscreen: Boolean = true,
    val hideInLandscape: Boolean = true,
    /** Mode paysage natif : en paysage, l'île reste active (même en plein écran), en haut au centre. */
    val landscapeNative: Boolean = true,
    val hideOnLockscreen: Boolean = false,
    val hiddenApps: Set<String> = emptySet(),
    /** Applis où le plein écran ne masque jamais l'île (les autres conditions restent valables). */
    val fullscreenIgnoredApps: Set<String> = emptySet(),
    /**
     * Sur l'écran d'accueil, jamais de masquage plein écran : beaucoup de launchers (dont celui
     * de MagicOS) masquent la barre d'état ou trompent la détection.
     */
    val ignoreFullscreenOnLauncher: Boolean = true,
    /** Paysage (île verticale invisible) : marge ajoutée autour de la caméra pour la zone tactile, en dp. */
    val landscapeTouchMarginDp: Float = 0f,
    /** Paysage : un appel ouvre la carte de lui-même. */
    val landscapeCalls: Boolean = true,
)

@Serializable
enum class MotionStyle {
    SOFT, SNAPPY, SOBER,
    /** Accentué : dépassement marqué, plus joueur. Ajouté en dernier (valeur sérialisée). */
    PLAYFUL,
}

/** Style et vitesse des animations de l'île. */
@Serializable
data class MotionSettings(
    val style: MotionStyle = MotionStyle.SOFT,
    /** Multiplicateur de vitesse (0.5 = lent, 2 = rapide). */
    val speed: Float = 1f,
    /** Flou du contenu étendu pendant son apparition. */
    val blurTransitions: Boolean = true,
    /** Retours haptiques de l'île (ouverture, fermeture). */
    val haptics: Boolean = true,
    /**
     * Animation d'entrée par type de contenu : moduleId (ou "default") → nom d'EntryAnimation
     * (core.style). Stocké en texte pour qu'une valeur inconnue (version plus récente) soit
     * simplement ignorée au lieu de rendre tout le fichier de réglages illisible.
     * Vide = gonflement actuel partout.
     */
    val entryAnimations: Map<String, String> = emptyMap(),
)
