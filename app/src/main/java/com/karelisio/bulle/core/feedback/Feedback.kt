package com.karelisio.bulle.core.feedback

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.VibratorManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Point d'entrée public des retours haptiques et sonores de Bulle.
 *
 * Utilisation depuis n'importe quel module (Alertes, Messages, Appels…) :
 * `Feedback.play(context, HapticPattern.DOUBLE_TAP, FeedbackSound(SoundKind.CHIME), hapticsEnabled = settings.motion.haptics)`.
 * Les règles de [FeedbackPolicy] (silencieux, vibreur, Ne pas déranger) sont toujours appliquées ;
 * aucune erreur ne remonte à l'appelant.
 */
object Feedback {
    private val pcmCache = ConcurrentHashMap<SoundKind, ShortArray>()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    private val soundAttributes: AudioAttributes
        get() = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

    /** Joue [haptic] et [sound] si l'état du téléphone le permet. Retourne ce qui a été joué. */
    fun play(
        context: Context,
        haptic: HapticPattern,
        sound: FeedbackSound,
        hapticsEnabled: Boolean = true,
    ): FeedbackDecision {
        if (haptic == HapticPattern.NONE && sound.isNone) return FeedbackDecision(vibrate = false, sound = false)
        val ctx = context.applicationContext ?: context
        val decision = runCatching {
            val audio = ctx.getSystemService(AudioManager::class.java)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            FeedbackPolicy.decide(
                haptic = haptic,
                sound = sound,
                ringer = RingerState.fromAudioManager(audio?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL),
                dnd = DndState.fromInterruptionFilter(nm?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL),
                hapticsEnabled = hapticsEnabled,
                notificationVolume = audio?.getStreamVolume(AudioManager.STREAM_NOTIFICATION) ?: 1,
            )
        }.getOrDefault(FeedbackDecision(vibrate = false, sound = false))
        if (decision.vibrate) vibrate(ctx, haptic)
        if (decision.sound) playSound(ctx, sound)
        return decision
    }

    /** Aperçu dans les réglages : mêmes règles, mais sans l'interrupteur global (l'utilisateur vient de toucher). */
    fun preview(context: Context, haptic: HapticPattern, sound: FeedbackSound): FeedbackDecision =
        play(context, haptic, sound, hapticsEnabled = true)

    private fun vibrate(ctx: Context, pattern: HapticPattern) {
        runCatching {
            val vibrator = ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator ?: return
            HapticEngine.play(pattern, AndroidVibratorPort(vibrator))
        }
    }

    private fun playSound(ctx: Context, sound: FeedbackSound) {
        runCatching {
            when (sound.kind) {
                SoundKind.NONE -> Unit
                SoundKind.SYSTEM -> playSystem(ctx, sound.uri)
                else -> playPcm(sound.kind)
            }
        }
    }

    private fun playSystem(ctx: Context, uri: String?) {
        val u = uri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        RingtoneManager.getRingtone(ctx, u)?.apply {
            audioAttributes = soundAttributes
            play()
        }
    }

    /** Son synthétisé, joué une fois en mode statique puis libéré. */
    private fun playPcm(kind: SoundKind) {
        val pcm = pcmCache.getOrPut(kind) { SoundSynth.render(kind) }
        if (pcm.isEmpty()) return
        val track = AudioTrack.Builder()
            .setAudioAttributes(soundAttributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SoundSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        track.play()
        val releaseAfter = SoundSynth.durationMs(kind) + 300L
        main.postDelayed({ runCatching { track.stop() }; track.release() }, releaseAfter)
    }
}
