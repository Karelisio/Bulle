package com.karelisio.bulle.modules.halo

import kotlinx.serialization.Serializable

/** Forme de l'animation du halo. */
@Serializable
enum class HaloStyle {
    /** Animation signature : part de la caméra, deux traits se rejoignent en bas dans un éclat de bulles. */
    SIGNATURE,
    /** Un trait lumineux fait le tour de l'écran. */
    STROKE,
    /** Tout le bord s'allume et pulse. */
    PULSE,
    /** Une vague de lumière parcourt le bord. */
    WAVE,
    /** Dégradé qui tourne autour de l'écran. */
    GRADIENT,
    /** Étincelles qui scintillent le long du bord. */
    SPARKLES,
}

/** D'où vient la couleur du halo. */
@Serializable
enum class HaloColorMode {
    /** Couleur dominante de l'icône de l'appli. */
    APP_ICON,
    /** Couleurs dynamiques Material You (fond d'écran). */
    DYNAMIC,
    /** Une couleur fixe. */
    FIXED,
}

/** Intégration de la caméra dans le contour. */
@Serializable
enum class HaloCamera {
    /** Le trait s'interrompt à la caméra et en jaillit de chaque côté (le plus discret). */
    EMERGE,
    /** Le trait contourne la caméra au plus près. */
    AROUND,
    /** La caméra est ignorée (trait droit). */
    IGNORE,
}

/** Sens de parcours (styles Signature et Trait). */
@Serializable
enum class HaloDirection { BOTH, CLOCKWISE, COUNTER }

/** Point de départ du halo. */
@Serializable
enum class HaloStart { CAMERA, BOTTOM }

/** Applis qui déclenchent un halo. */
@Serializable
enum class HaloAppFilter { ALL, SELECTED }

/**
 * Réglages du module Halo (bords lumineux). Toutes les valeurs ont un défaut : on peut en ajouter
 * sans migration et la sauvegarde JSON reste compatible.
 */
@Serializable
data class HaloSettings(
    val enabled: Boolean = false,
    val style: HaloStyle = HaloStyle.SIGNATURE,
    /** Épaisseur du trait, en dp. */
    val thicknessDp: Float = 4f,
    /** Durée d'un passage, en millisecondes. */
    val durationMs: Int = 1800,
    /** Nombre de passages. */
    val passes: Int = 1,
    /** Intensité lumineuse (0,2 à 1). */
    val intensity: Float = 0.9f,
    /** Vitesse (0,5 = lent, 2 = rapide). */
    val speed: Float = 1f,
    val colorMode: HaloColorMode = HaloColorMode.APP_ICON,
    /** Couleur fixe (ARGB), utilisée en mode [HaloColorMode.FIXED]. */
    val fixedColor: Long = 0xFF7FD3FF,
    /** Couleur fixe par appli (paquet → ARGB), prioritaire sur le mode choisi. */
    val appColors: Map<String, Long> = emptyMap(),
    /** Notifications de l'appli qui joue de la musique : couleur de la pochette. */
    val artworkForMusic: Boolean = true,
    /** Halo à chaque nouveau morceau (couleur de la pochette). */
    val trackChange: Boolean = false,
    /** Écho : rose pulsé, synchronisé avec le cœur. */
    val echoHeart: Boolean = true,
    val appFilter: HaloAppFilter = HaloAppFilter.ALL,
    val selectedApps: Set<String> = emptySet(),
    /** Seulement les conversations (messageries). */
    val conversationsOnly: Boolean = false,
    val ignoreSilent: Boolean = true,
    val ignoreOngoing: Boolean = true,
    /** Aucun halo en mode Ne pas déranger. */
    val muteInDnd: Boolean = true,
    /** Écran éteint : halo sur fond noir pendant quelques secondes, puis l'écran se rééteint. */
    val screenOff: Boolean = false,
    /** Durée d'affichage écran éteint, en secondes. */
    val screenOffSeconds: Int = 4,
    /** Nombre maximal de réveils de l'écran par heure (batterie). */
    val screenOffMaxPerHour: Int = 6,
    /** Au branchement : halo vert qui monte le long des bords selon le pourcentage. */
    val chargingHalo: Boolean = false,
    /** Longueur de la traînée (fraction du tour). */
    val trail: Float = 0.22f,
    /** Force de la lueur autour du trait (0 = trait seul). */
    val glow: Float = 0.6f,
    val direction: HaloDirection = HaloDirection.BOTH,
    val start: HaloStart = HaloStart.CAMERA,
    val camera: HaloCamera = HaloCamera.EMERGE,
    /** Écart entre le trait et la caméra (dp). */
    val cameraGapDp: Float = 2f,
    /** Seconde couleur (dégradé le long du trait), ARGB ; null = une seule couleur. */
    val secondColor: Long? = null,
    /** Éclat de bulles quand les traits se rejoignent (Signature). */
    val burst: Boolean = true,
    /** Taille de l'éclat. */
    val burstSize: Float = 1f,
) {
    /** Valeurs ramenées dans leurs plages (sauvegarde modifiée à la main, version plus ancienne). */
    fun sanitized(): HaloSettings {
        val d = HaloSettings()
        return copy(
            thicknessDp = thicknessDp.orDefault(d.thicknessDp).coerceIn(THICKNESS),
            durationMs = durationMs.coerceIn(DURATION),
            passes = passes.coerceIn(PASSES),
            intensity = intensity.orDefault(d.intensity).coerceIn(INTENSITY),
            speed = speed.orDefault(d.speed).coerceIn(SPEED),
            fixedColor = fixedColor.takeIf { it in 0L..0xFFFFFFFFL } ?: d.fixedColor,
            appColors = appColors.filterValues { it in 0L..0xFFFFFFFFL },
            screenOffSeconds = screenOffSeconds.coerceIn(SCREEN_OFF_SECONDS),
            screenOffMaxPerHour = screenOffMaxPerHour.coerceIn(SCREEN_OFF_PER_HOUR),
            trail = trail.orDefault(d.trail).coerceIn(TRAIL),
            glow = glow.orDefault(d.glow).coerceIn(0f, 1f),
            cameraGapDp = cameraGapDp.orDefault(d.cameraGapDp).coerceIn(CAMERA_GAP),
            secondColor = secondColor?.takeIf { it in 0L..0xFFFFFFFFL },
            burstSize = burstSize.orDefault(d.burstSize).coerceIn(BURST_SIZE),
        )
    }

    companion object {
        val THICKNESS = 1f..12f
        val DURATION = 600..5000
        val PASSES = 1..5
        val INTENSITY = 0.2f..1f
        val SPEED = 0.5f..2f
        val SCREEN_OFF_SECONDS = 2..8
        val SCREEN_OFF_PER_HOUR = 1..20
        val TRAIL = 0.06f..0.6f
        val CAMERA_GAP = 0f..12f
        val BURST_SIZE = 0.5f..2f
    }
}

private fun Float.orDefault(d: Float) = if (isNaN()) d else this
