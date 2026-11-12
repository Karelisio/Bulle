package com.karelisio.bulle.modules.focus

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import java.io.File

/** Fin de phase signalée par l'île (alerte ponctuelle). */
data class FocusAlert(
    val finished: FocusPhase,
    /** Phase suivante (null = minuteur ou cycle terminé). */
    val next: FocusState?,
    /** Instant de l'alerte (elapsedRealtime). */
    val at: Long,
)

/**
 * Minuteur / Pomodoro : état partagé entre le panneau, le contenu de l'île et l'écran de réglages.
 * Chaque changement est écrit dans un petit fichier privé : le minuteur survit au redémarrage
 * du service (et du téléphone, la fin prévue étant une heure murale).
 */
object Focus {

    /** Au-delà de ce retard, une fin de phase manquée (service arrêté) est rattrapée sans alerte. */
    const val LATE_MS = 60_000L

    private val _data = MutableStateFlow(FocusData())
    val data: StateFlow<FocusData> = _data.asStateFlow()

    private val _alert = MutableStateFlow<FocusAlert?>(null)
    val alert: StateFlow<FocusAlert?> = _alert.asStateFlow()

    @Volatile
    private var store: FocusStore? = null

    // Écritures sérialisées, hors du fil principal.
    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Données, chargées depuis le fichier au premier appel. */
    fun loaded(context: Context): StateFlow<FocusData> {
        ensureLoaded(context)
        return data
    }

    /** Charge le fichier une seule fois par processus. */
    fun ensureLoaded(context: Context) {
        if (store != null) return
        synchronized(this) {
            if (store != null) return
            val s = FocusStore(File(context.applicationContext.filesDir, FocusStore.FILE_NAME))
            _data.value = s.load()
            store = s
        }
    }

    private fun mutate(context: Context, transform: (FocusData) -> FocusData): FocusData {
        ensureLoaded(context)
        val updated = _data.updateAndGet(transform)
        val s = store
        if (s != null) io.launch { s.save(_data.value) }
        return updated
    }

    private fun now() = System.currentTimeMillis()

    fun start(context: Context, preset: FocusPreset) {
        _alert.value = null
        mutate(context) { it.copy(state = FocusTimer.start(preset, it.settings, now())) }
    }

    fun pause(context: Context) = mutate(context) { d -> d.copy(state = d.state?.let { FocusTimer.pause(it, now()) }) }

    fun resume(context: Context) = mutate(context) { d -> d.copy(state = d.state?.let { FocusTimer.resume(it, now()) }) }

    fun stop(context: Context) {
        _alert.value = null
        mutate(context) { it.copy(state = null) }
    }

    /** Passe à la phase suivante sans attendre (Pomodoro), ou arrête un minuteur simple. */
    fun skip(context: Context) = mutate(context) { d ->
        d.copy(state = d.state?.let { FocusTimer.next(it, d.settings, now(), autoStart = true) })
    }

    fun updateSettings(context: Context, transform: (FocusSettings) -> FocusSettings) =
        mutate(context) { it.copy(settings = transform(it.settings).sanitized()) }

    /**
     * Termine la phase si son heure est passée : enchaîne (ou prépare) la suivante et signale la fin
     * (alerte de l'île, vibration, son). Retourne vrai si une phase a été terminée.
     */
    fun completeIfDue(context: Context, haptics: Boolean, now: Long = now()): Boolean {
        val current = data.value.state ?: return false
        if (!FocusTimer.isFinished(current, now)) return false
        val late = now - (current.endAtWall ?: now) > LATE_MS
        var done = false
        val after = mutate(context) { d ->
            // Revérifié dans la transformation : un autre appel a pu terminer la phase entre-temps.
            val st = d.state
            done = st == current
            if (st == null || !done) d else d.copy(state = FocusTimer.next(st, d.settings, now, d.settings.autoAdvance))
        }
        if (!done) return false
        if (!late) {
            _alert.value = FocusAlert(current.phase, after.state, SystemClock.elapsedRealtime())
            signal(context, after.settings, haptics)
        }
        return true
    }

    /** Vibration légère et son optionnel ; aucune erreur ne doit remonter jusqu'à l'île. */
    private fun signal(context: Context, settings: FocusSettings, haptics: Boolean) {
        if (haptics && settings.vibrate) {
            runCatching {
                val vibrator = context.getSystemService(VibratorManager::class.java)?.defaultVibrator
                if (vibrator?.hasVibrator() == true) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
            }
        }
        if (settings.sound) {
            runCatching {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                RingtoneManager.getRingtone(context, uri)?.apply {
                    audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    play()
                }
            }
        }
    }

    /** Remet l'objet à zéro (tests), éventuellement sur un fichier donné. */
    fun resetForTests(file: File? = null) {
        synchronized(this) {
            store = file?.let { FocusStore(it) }
            _data.value = store?.load() ?: FocusData()
            _alert.value = null
        }
    }

    /** Écrit immédiatement l'état courant (tests). */
    fun flushForTests() {
        store?.save(_data.value)
    }
}
