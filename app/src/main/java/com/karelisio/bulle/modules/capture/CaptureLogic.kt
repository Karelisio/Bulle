package com.karelisio.bulle.modules.capture

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Formatage du chrono d'enregistrement : « 0:07 », « 12:34 », « 1:02:03 ». */
object CaptureFormat {
    fun chrono(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}

// ---- Mémo vocal : machine d'état pure -----------------------------------------------------------

enum class MemoPhase { IDLE, RECORDING, PAUSED }

/**
 * État d'un mémo. Les instants sont en `elapsedRealtime` (insensibles au changement d'heure).
 * @param accumulatedMs durée enregistrée avant la dernière reprise.
 * @param resumedAt instant de la dernière reprise (ou du démarrage), valable en [MemoPhase.RECORDING].
 */
data class MemoSession(
    val phase: MemoPhase = MemoPhase.IDLE,
    val startedAt: Long = 0L,
    val accumulatedMs: Long = 0L,
    val resumedAt: Long = 0L,
) {
    val active: Boolean get() = phase != MemoPhase.IDLE

    fun elapsed(now: Long): Long = when (phase) {
        MemoPhase.IDLE -> 0L
        MemoPhase.PAUSED -> accumulatedMs
        MemoPhase.RECORDING -> accumulatedMs + (now - resumedAt).coerceAtLeast(0)
    }
}

sealed interface MemoEvent {
    val now: Long
    data class Start(override val now: Long) : MemoEvent
    data class Pause(override val now: Long) : MemoEvent
    data class Resume(override val now: Long) : MemoEvent
    data class Stop(override val now: Long) : MemoEvent
}

object MemoMachine {
    /** Transition ; un événement sans objet dans l'état courant ne change rien. */
    fun reduce(s: MemoSession, e: MemoEvent): MemoSession = when (e) {
        is MemoEvent.Start -> if (s.phase == MemoPhase.IDLE) MemoSession(MemoPhase.RECORDING, e.now, 0L, e.now) else s
        is MemoEvent.Pause -> if (s.phase == MemoPhase.RECORDING) {
            s.copy(phase = MemoPhase.PAUSED, accumulatedMs = s.elapsed(e.now))
        } else {
            s
        }
        is MemoEvent.Resume -> if (s.phase == MemoPhase.PAUSED) s.copy(phase = MemoPhase.RECORDING, resumedAt = e.now) else s
        is MemoEvent.Stop -> MemoSession()
    }

    /** Niveau sonore normalisé 0..1 à partir de `MediaRecorder.getMaxAmplitude()` (0..32767), échelle en dB. */
    fun level(maxAmplitude: Int): Float {
        if (maxAmplitude <= 0) return 0f
        val db = 20.0 * kotlin.math.log10(maxAmplitude / 32767.0) // -90..0
        return ((db + 50.0) / 50.0).toFloat().coerceIn(0f, 1f)
    }
}

// ---- Enregistreur d'écran : machine d'état pure -------------------------------------------------

enum class RecorderPhase { IDLE, REQUESTING, RECORDING }

data class RecorderSession(
    val phase: RecorderPhase = RecorderPhase.IDLE,
    val startedAt: Long = 0L,
    val withAudio: Boolean = false,
) {
    val active: Boolean get() = phase == RecorderPhase.RECORDING
    fun elapsed(now: Long): Long = if (phase == RecorderPhase.RECORDING) (now - startedAt).coerceAtLeast(0) else 0L
}

sealed interface RecorderEvent {
    /** L'utilisateur demande un enregistrement : écran de consentement affiché. */
    data object Request : RecorderEvent
    /** Consentement accordé et enregistrement démarré. */
    data class Started(val now: Long, val withAudio: Boolean) : RecorderEvent
    /** Consentement refusé ou échec au démarrage. */
    data object Denied : RecorderEvent
    /** Arrêt demandé par l'utilisateur ou par le système (callback onStop de MediaProjection). */
    data object Stopped : RecorderEvent
}

object RecorderMachine {
    fun reduce(s: RecorderSession, e: RecorderEvent): RecorderSession = when (e) {
        RecorderEvent.Request -> if (s.phase == RecorderPhase.IDLE) s.copy(phase = RecorderPhase.REQUESTING) else s
        is RecorderEvent.Started -> if (s.phase != RecorderPhase.RECORDING) RecorderSession(RecorderPhase.RECORDING, e.now, e.withAudio) else s
        RecorderEvent.Denied -> if (s.phase == RecorderPhase.REQUESTING) RecorderSession() else s
        RecorderEvent.Stopped -> RecorderSession()
    }

    /** Action à mener sur la commande « basculer l'enregistrement d'écran ». */
    fun toggle(s: RecorderSession): RecorderToggle = when (s.phase) {
        RecorderPhase.IDLE -> RecorderToggle.REQUEST_CONSENT
        RecorderPhase.REQUESTING -> RecorderToggle.NOTHING
        RecorderPhase.RECORDING -> RecorderToggle.STOP
    }
}

enum class RecorderToggle { REQUEST_CONSENT, STOP, NOTHING }

// ---- Vidéo : taille et débit ------------------------------------------------------------------

data class VideoSize(val width: Int, val height: Int)

object VideoSizing {
    /**
     * Taille d'encodage : la petite dimension est ramenée à [maxShortSide] (0 = native),
     * proportions conservées, dimensions arrondies au multiple de 16 inférieur (exigé par
     * de nombreux encodeurs matériels).
     */
    fun fit(screenW: Int, screenH: Int, maxShortSide: Int): VideoSize {
        val shortSide = min(screenW, screenH).coerceAtLeast(16)
        val scale = if (maxShortSide in 1 until shortSide) maxShortSide.toFloat() / shortSide else 1f
        return VideoSize(align16(screenW * scale), align16(screenH * scale))
    }

    /** Réduction d'un cran (≈ 80 %) quand l'encodeur refuse la taille demandée. */
    fun shrink(size: VideoSize): VideoSize = VideoSize(align16(size.width * 0.8f), align16(size.height * 0.8f))

    /** Débit vidéo indicatif (≈ 0,1 bit par pixel et par image), borné à 2–20 Mbit/s. */
    fun bitrate(size: VideoSize, fps: Int): Int =
        (size.width.toLong() * size.height * fps / 10).toInt().coerceIn(2_000_000, 20_000_000)

    private fun align16(v: Float): Int = max(16, (v.roundToInt() / 16) * 16)
}
