package com.karelisio.bulle.modules.volume

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat

/** Niveau d'une source listée dans la vue étendue. */
data class StreamLevel(
    val stream: VolumeStream,
    val level: Int,
    val min: Int,
    val max: Int,
    val muted: Boolean,
    /** Réglage refusé par Ne pas déranger (curseur désactivé, lien vers l'accès). */
    val blocked: Boolean,
    /** Volume fixe sur l'appareil ou la sortie (aucun réglage possible). */
    val fixed: Boolean,
) {
    val fraction: Float get() = if (muted) 0f else VolumeLogic.fraction(level, min, max)
    val adjustable: Boolean get() = !blocked && !fixed
}

/**
 * Lecture et réglage de toutes les sources de volume (vue étendue de l'île).
 * Contrairement aux touches, régler une source ici ne change pas le flux des touches.
 */
object VolumeMixer {

    private const val TAG = "BulleVolume"
    private const val VOLUME_CHANGED = "android.media.VOLUME_CHANGED_ACTION"
    private const val MUTE_CHANGED = "android.media.STREAM_MUTE_CHANGED_ACTION"

    /** Réglage AOSP (ancien) qui lie notifications et sonnerie ; absent → on garde les deux curseurs. */
    private const val LINK_NOTIFICATION = "volume_link_notification"

    /** Flux refusés par le système pendant cette session (SecurityException reçue). */
    private val denied = HashSet<VolumeStream>()

    fun context(context: Context, mediaPlaying: Boolean): MixerContext {
        val am = context.getSystemService(AudioManager::class.java)
        return MixerContext(
            mediaPlaying = mediaPlaying || am?.isMusicActive == true,
            communication = am != null && VolumeLogic.isCommunicationMode(am.mode),
            notificationLinked = notificationLinked(context),
            accessibilityActive = context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true,
        )
    }

    /** Sonnerie et notifications liées ? Seulement si le système le dit explicitement. */
    fun notificationLinked(context: Context): Boolean =
        runCatching { Settings.Secure.getInt(context.contentResolver, LINK_NOTIFICATION, 0) == 1 }.getOrDefault(false)

    fun read(context: Context, streams: List<VolumeStream>): List<StreamLevel> {
        val am = context.getSystemService(AudioManager::class.java) ?: return emptyList()
        val nm = context.getSystemService(NotificationManager::class.java)
        val dnd = nm != null && nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
        val access = nm?.isNotificationPolicyAccessGranted == true
        if (access || !dnd) denied.clear()
        val fixed = am.isVolumeFixed
        return streams.map { s ->
            val t = s.streamType
            StreamLevel(
                stream = s,
                level = runCatching { am.getStreamVolume(t) }.getOrDefault(0),
                min = runCatching { am.getStreamMinVolume(t) }.getOrDefault(0),
                max = runCatching { am.getStreamMaxVolume(t) }.getOrDefault(1),
                muted = runCatching { am.isStreamMute(t) }.getOrDefault(false),
                blocked = VolumeLogic.blockedByDnd(s, dnd, access) || s in denied,
                fixed = fixed,
            )
        }
    }

    /** Règle un flux sans panneau système (aucun FLAG_SHOW_UI). Faux si le système refuse. */
    fun setLevel(context: Context, stream: VolumeStream, level: Int): Boolean =
        change(context, stream) { it.setStreamVolume(stream.streamType, level, 0) }

    fun toggleMute(context: Context, stream: VolumeStream): Boolean =
        change(context, stream) { it.adjustStreamVolume(stream.streamType, AudioManager.ADJUST_TOGGLE_MUTE, 0) }

    private inline fun change(context: Context, stream: VolumeStream, block: (AudioManager) -> Unit): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val ok = try {
            block(am)
            true
        } catch (e: SecurityException) {
            // Ne pas déranger : le curseur passe en désactivé avec un lien vers l'accès.
            denied += stream
            Log.i(TAG, "Volume refusé (${stream.name}) : ${e.message}")
            false
        } catch (e: RuntimeException) {
            Log.w(TAG, "Volume non réglé (${stream.name})", e)
            false
        }
        // La jauge compacte suit si c'est le flux des touches.
        VolumeKeys.refresh(context)
        return ok
    }

    /** Ouvre l'accès « Ne pas déranger » de Bulle. */
    fun openDndAccess(context: Context, close: () -> Unit) {
        close()
        runCatching {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * Écoute les changements de volume faits ailleurs (touches, autre appli, panneau système) :
     * diffusions du système, plus les réglages enregistrés en secours. Retourne la désinscription.
     */
    fun watch(context: Context, onChange: () -> Unit): () -> Unit {
        val app = context.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) = onChange()
        }
        val filter = IntentFilter().apply {
            addAction(VOLUME_CHANGED)
            addAction(MUTE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED)
        }
        // Diffusions système uniquement : rien d'exploitable venant d'autres applis.
        val registered = runCatching { ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_EXPORTED) }.isSuccess
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = onChange()
        }
        val observed = runCatching { app.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer) }.isSuccess
        return {
            if (registered) runCatching { app.unregisterReceiver(receiver) }
            if (observed) runCatching { app.contentResolver.unregisterContentObserver(observer) }
        }
    }
}
