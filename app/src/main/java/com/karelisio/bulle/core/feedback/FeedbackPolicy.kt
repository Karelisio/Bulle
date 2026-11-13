package com.karelisio.bulle.core.feedback

import android.app.NotificationManager
import android.media.AudioManager

/** Mode de sonnerie du téléphone. */
enum class RingerState {
    NORMAL, VIBRATE, SILENT;

    companion object {
        fun fromAudioManager(mode: Int): RingerState = when (mode) {
            AudioManager.RINGER_MODE_SILENT -> SILENT
            AudioManager.RINGER_MODE_VIBRATE -> VIBRATE
            else -> NORMAL
        }
    }
}

/** État de « Ne pas déranger ». */
enum class DndState {
    OFF, PRIORITY, ALARMS, TOTAL;

    companion object {
        fun fromInterruptionFilter(filter: Int): DndState = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> PRIORITY
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> ALARMS
            NotificationManager.INTERRUPTION_FILTER_NONE -> TOTAL
            else -> OFF // ALL ou UNKNOWN
        }
    }
}

/** Ce qu'on a le droit de jouer. */
data class FeedbackDecision(val vibrate: Boolean, val sound: Boolean) {
    val any: Boolean get() = vibrate || sound
}

/** Règles de politesse : fonction pure, testée. */
object FeedbackPolicy {
    /**
     * - Vibration : réglage global « Retour haptique » actif, motif choisi, et pas de silence total
     *   (mode silencieux, ou Ne pas déranger « aucune interruption » / « alarmes seulement »).
     * - Son : son choisi, sonnerie en mode normal, Ne pas déranger désactivé et volume des
     *   notifications non nul.
     */
    fun decide(
        haptic: HapticPattern,
        sound: FeedbackSound,
        ringer: RingerState,
        dnd: DndState,
        hapticsEnabled: Boolean,
        notificationVolume: Int = 1,
    ): FeedbackDecision {
        val totalSilence = ringer == RingerState.SILENT || dnd == DndState.TOTAL || dnd == DndState.ALARMS
        val vibrate = hapticsEnabled && haptic != HapticPattern.NONE && !totalSilence
        val play = !sound.isNone && ringer == RingerState.NORMAL && dnd == DndState.OFF && notificationVolume > 0
        return FeedbackDecision(vibrate, play)
    }
}
