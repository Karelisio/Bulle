package com.karelisio.bulle.modules.music.visualizer

/**
 * Décision d'ouvrir ou de libérer le [android.media.audiofx.Visualizer] (logique pure, testée).
 *
 * Le visualiseur ne tourne que si TOUT est réuni : option activée, micro autorisé, au moins une vue
 * musique affichée qui le demande, musique en lecture, écran allumé, et pas d'échec précédent
 * (Visualizer indisponible sur l'appareil). Sinon il est libéré immédiatement.
 */
object VisualizerPolicy {

    /** Ce qu'il faut faire du Visualizer. */
    enum class Action { CAPTURE, RELEASE }

    /** Entrées de la décision. */
    data class Inputs(
        /** Option « visualiseur audio réel » activée (réglage du module). */
        val settingEnabled: Boolean,
        /** RECORD_AUDIO accordée. */
        val permissionGranted: Boolean,
        /** Nombre de vues musique affichées (compacte, étendue, karaoké) qui demandent le son. */
        val visibleConsumers: Int,
        /** Musique en lecture. */
        val playing: Boolean,
        /** Écran allumé (interactif). */
        val screenOn: Boolean,
        /** Le Visualizer a échoué (indisponible, refusé par le système) : repli sur l'égaliseur simulé. */
        val unavailable: Boolean,
    )

    fun decide(inputs: Inputs): Action = with(inputs) {
        if (settingEnabled && permissionGranted && visibleConsumers > 0 && playing && screenOn && !unavailable) {
            Action.CAPTURE
        } else {
            Action.RELEASE
        }
    }

    /** Fréquence de capture visée (mHz), bornée par le maximum annoncé par le Visualizer. 25 Hz par défaut. */
    fun captureRate(maxRateMilliHz: Int, wantedMilliHz: Int = 25_000): Int =
        if (maxRateMilliHz <= 0) wantedMilliHz else minOf(maxRateMilliHz, wantedMilliHz)

    /** Taille de capture : 512 si possible (assez de raies pour 24 bandes), dans la plage annoncée. */
    fun captureSize(range: IntArray?, wanted: Int = 512): Int {
        if (range == null || range.size < 2) return wanted
        return wanted.coerceIn(range[0], range[1])
    }

    /** Nombre de captures muettes consécutives (≈ 2 s à 25 Hz) avant de repasser à l'égaliseur simulé. */
    const val SILENT_CAPTURES_BEFORE_FALLBACK = 50
}
