package com.karelisio.bulle.modules.volume

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.modules.volet.media.MediaOutput
import com.karelisio.bulle.notifications.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Niveau affiché par l'île pour un flux. [shownAt] (elapsedRealtime) repousse la disparition. */
data class VolumeOverlay(
    val stream: VolumeStream,
    val level: Int,
    val min: Int,
    val max: Int,
    val muted: Boolean,
    val shownAt: Long,
    /** Doigt posé sur un curseur : le contenu n'expire pas. */
    val holding: Boolean = false,
    /** Vue étendue ouverte : délai plus long. */
    val expanded: Boolean = false,
) {
    val fraction: Float get() = if (muted) 0f else VolumeLogic.fraction(level, min, max)
}

/**
 * Touches volume interceptées par le service d'accessibilité (onKeyEvent) : l'île affiche
 * son propre curseur au lieu du panneau système. Ne consomme jamais une touche si le module
 * est désactivé, si l'île est masquée, pendant un appel, ou écran éteint / verrouillé (selon réglage).
 */
object VolumeKeys {

    private const val TAG = "BulleVolume"

    private val _overlay = MutableStateFlow<VolumeOverlay?>(null)
    val overlay: StateFlow<VolumeOverlay?> = _overlay.asStateFlow()

    /** Touches dont l'appui a été consommé : leur relâchement doit l'être aussi. */
    private val consumedDown = HashSet<Int>()

    /** Flux choisi dans la vue étendue : prioritaire tant que le curseur reste affiché. */
    @Volatile
    private var pinned: VolumeStream? = null

    /** Appelé par le service d'accessibilité pour chaque touche. Retourne vrai si la touche est consommée. */
    fun onKeyEvent(context: Context, event: KeyEvent): Boolean {
        val code = event.keyCode
        if (!VolumeLogic.isVolumeKey(code)) return false
        return runCatching { handle(context, event, code) }
            .onFailure { Log.w(TAG, "Touche volume non gérée", it) }
            .getOrDefault(false)
    }

    private fun handle(context: Context, event: KeyEvent, code: Int): Boolean = when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            if (event.repeatCount == 0) {
                if (VolumeLogic.shouldIntercept(conditions(context))) consumedDown += code else consumedDown -= code
            }
            if (code in consumedDown) {
                adjust(context, if (code == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER)
                true
            } else {
                false
            }
        }
        KeyEvent.ACTION_UP -> consumedDown.remove(code)
        else -> code in consumedDown
    }

    /** État courant (service, île, appel, écran). Sans île en marche : aucune interception. */
    fun conditions(context: Context): KeyConditions {
        val controller = IslandAccessibilityService.controller.value
        val am = context.getSystemService(AudioManager::class.java)
        VolumePrefs.ensureLoaded(context)
        return KeyConditions(
            moduleEnabled = controller != null && VolumeModule.isEnabled(controller.settings.value),
            islandVisible = controller?.state?.value?.visible == true,
            inCall = am == null || am.mode != AudioManager.MODE_NORMAL,
            screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive != false,
            locked = context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true,
            allowWhenLocked = VolumePrefs.data.value.interceptWhenLocked,
        )
    }

    private fun currentStream(context: Context, am: AudioManager): VolumeStream {
        val shown = _overlay.value
        // Le flux choisi à la main n'est gardé que tant que le curseur est à l'écran.
        val expires = shown?.let { VolumeLogic.expiresAt(it.shownAt, VolumePrefs.data.value.timeoutMs, it.holding, it.expanded) }
        if (shown == null || (expires != null && SystemClock.elapsedRealtime() > expires)) pinned = null
        VolumePrefs.ensureLoaded(context)
        return VolumeLogic.pickStream(VolumePrefs.data.value.stream, am.isMusicActive, pinned)
    }

    /** Monte / baisse d'un cran sans afficher le panneau système (aucun FLAG_SHOW_UI). */
    fun adjust(context: Context, direction: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val stream = currentStream(context, am)
        // Ne pas déranger peut refuser le changement de sonnerie : on affiche quand même le niveau.
        runCatching { am.adjustStreamVolume(stream.streamType, direction, 0) }
            .onFailure { Log.i(TAG, "Réglage du volume refusé : ${it.message}") }
        publish(am, stream)
    }

    fun setLevel(context: Context, stream: VolumeStream, level: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        pinned = stream
        runCatching { am.setStreamVolume(stream.streamType, level, 0) }
        publish(am, stream)
    }

    fun toggleMute(context: Context, stream: VolumeStream) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        pinned = stream
        runCatching { am.adjustStreamVolume(stream.streamType, AudioManager.ADJUST_TOGGLE_MUTE, 0) }
        publish(am, stream)
    }

    /** Choix d'un autre flux dans la vue étendue. */
    fun select(context: Context, stream: VolumeStream) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        pinned = stream
        publish(am, stream)
    }

    /**
     * Relit le niveau du flux affiché (réglé depuis la liste des sources), sans changer de flux.
     * Niveau inchangé : simple prolongation regroupée (pas de recréation à chaque image).
     */
    fun refresh(context: Context) {
        val shown = _overlay.value ?: return
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val type = shown.stream.streamType
        val same = am.getStreamVolume(type) == shown.level &&
            runCatching { am.isStreamMute(type) }.getOrDefault(false) == shown.muted
        if (same) keepAlive() else publish(am, shown.stream)
    }

    /** Dernière prolongation (elapsedRealtime), pour le regroupement. */
    @Volatile
    private var lastBumpAt = 0L

    /**
     * Repousse la disparition du curseur (toucher, glissement, changement détecté).
     * Regroupé : au plus toutes les 400 ms, sauf [force] (début / fin de geste, bouton).
     */
    fun keepAlive(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (_overlay.value == null || !VolumeLogic.shouldKeepAlive(now, lastBumpAt, force)) return
        lastBumpAt = now
        _overlay.update { it?.copy(shownAt = now) }
    }

    /** Doigt posé / levé sur un curseur de l'île : suspend puis relance le délai. */
    fun setHolding(holding: Boolean) {
        val now = SystemClock.elapsedRealtime()
        lastBumpAt = now
        _overlay.update { it?.copy(holding = holding, shownAt = now) }
    }

    /** Vue étendue ouverte / fermée (ouverture = interaction). */
    fun setExpanded(expanded: Boolean) {
        val now = SystemClock.elapsedRealtime()
        _overlay.update { o ->
            when {
                o == null || o.expanded == expanded -> o
                expanded -> o.copy(expanded = true, shownAt = now).also { lastBumpAt = now }
                else -> o.copy(expanded = false, holding = false)
            }
        }
    }

    fun hide() {
        _overlay.value = null
        pinned = null
    }

    private fun publish(am: AudioManager, stream: VolumeStream) {
        val type = stream.streamType
        val prev = _overlay.value
        val now = SystemClock.elapsedRealtime()
        lastBumpAt = now
        _overlay.value = VolumeOverlay(
            stream = stream,
            level = am.getStreamVolume(type),
            min = runCatching { am.getStreamMinVolume(type) }.getOrDefault(0),
            max = am.getStreamMaxVolume(type),
            muted = runCatching { am.isStreamMute(type) }.getOrDefault(false),
            shownAt = now,
            // Geste et vue étendue conservés : une relecture ne relance pas l'expiration.
            holding = prev?.holding == true,
            expanded = prev?.expanded == true,
        )
    }

    /** Publie un niveau déjà connu (tests, aperçu). */
    fun showForTests(overlay: VolumeOverlay?) {
        _overlay.value = overlay
    }

    /**
     * Ouvre le choix de la sortie audio, comme la carte média du Volet : sélecteur de Paramètres
     * (Android 14+), sinon sélecteur de MediaRouter2, sinon réglages Bluetooth.
     */
    fun openOutputSwitcher(context: Context, close: () -> Unit) {
        val pkg = playingPackage(context) ?: context.packageName
        MediaOutput.openSwitcher(context, pkg, close) { intent ->
            close()
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }

    /** Appli qui joue en ce moment (via l'accès aux notifications), pour cibler son routage. */
    private fun playingPackage(context: Context): String? = runCatching {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val sessions = msm.getActiveSessions(NotificationRepository.component(context))
        (sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull())?.packageName
    }.getOrNull()
}
