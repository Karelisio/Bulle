package com.karelisio.bulle.modules.volume

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
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

    // Répétition en maintenant la touche : une fois l'appui intercepté, le système ne génère plus
    // ses propres répétitions ; on les recrée (délai initial puis pas réguliers jusqu'au relâchement).
    private val handler = Handler(Looper.getMainLooper())
    private var repeat: Runnable? = null
    private var repeatCode = 0

    /** Flux choisi dans la vue étendue : prioritaire tant que le curseur reste affiché. */
    @Volatile
    private var pinned: VolumeStream? = null

    /** Appelé par le service d'accessibilité pour chaque touche. Retourne vrai si la touche est consommée. */
    fun onKeyEvent(context: Context, event: KeyEvent): Boolean {
        val code = event.keyCode
        // Power : jamais consommé ; sert à reconnaître la capture d'écran (Power + volume).
        if (code == KeyEvent.KEYCODE_POWER) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) runCatching { onPower(context) }
            return false
        }
        if (!VolumeLogic.isVolumeKey(code)) return false
        return runCatching { handle(context, event, code) }
            .onFailure { Log.w(TAG, "Touche volume non gérée", it) }
            .getOrDefault(false)
    }

    private fun handle(context: Context, event: KeyEvent, code: Int): Boolean = when (event.action) {
        KeyEvent.ACTION_DOWN -> {
            if (event.repeatCount == 0) {
                // Power vient d'être pressé : combinaison du système (capture d'écran…), on n'y touche pas.
                val chord = VolumeLogic.isChord(SystemClock.uptimeMillis(), lastPowerDown)
                if (!chord && VolumeLogic.shouldIntercept(conditions(context))) consumedDown += code else consumedDown -= code
            }
            if (code in consumedDown) {
                val direction = if (code == KeyEvent.KEYCODE_VOLUME_UP) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
                if (event.repeatCount == 0) {
                    // Cran différé de quelques dizaines de ms : si Power suit (capture d'écran),
                    // le volume n'est pas touché.
                    rememberUndo(context)
                    lastVolumeDown = SystemClock.uptimeMillis()
                    pendingAdjust?.let(handler::removeCallbacks)
                    val pending = Runnable { pendingAdjust = null; runCatching { adjust(context, direction) } }
                    pendingAdjust = pending
                    handler.postDelayed(pending, VolumeLogic.DEFER_MS)
                    startRepeat(context, code, direction)
                } else if (repeat == null) {
                    // Répétitions système (si elles arrivent) : utilisées seulement sans la nôtre.
                    adjust(context, direction)
                }
                true
            } else {
                false
            }
        }
        KeyEvent.ACTION_UP -> {
            if (code == repeatCode) stopRepeat()
            consumedDown.remove(code)
        }
        else -> code in consumedDown
    }

    // --- Capture d'écran (Power + volume) ---

    @Volatile
    private var lastPowerDown = 0L

    @Volatile
    private var lastVolumeDown = 0L
    private var pendingAdjust: Runnable? = null

    /** Niveau avant le dernier appui (flux, niveau) : rétabli si l'appui faisait partie d'une capture. */
    private var undo: Pair<Int, Int>? = null

    private fun rememberUndo(context: Context) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        val stream = currentStream(context, am).streamType
        undo = stream to am.getStreamVolume(stream)
    }

    /**
     * Power pressé. Juste après un appui volume intercepté : c'était la combinaison de capture
     * d'écran. Le cran de volume est annulé (ou rétabli), le curseur retiré, et la capture est
     * prise par le service (le système n'a pas vu la touche volume, consommée par Bulle).
     */
    private fun onPower(context: Context) {
        val now = SystemClock.uptimeMillis()
        lastPowerDown = now
        if (!VolumeLogic.isChord(now, lastVolumeDown) || consumedDown.isEmpty()) return
        val wasPending = pendingAdjust != null
        pendingAdjust?.let(handler::removeCallbacks)
        pendingAdjust = null
        stopRepeat()
        if (!wasPending) {
            val am = context.getSystemService(AudioManager::class.java)
            undo?.let { (stream, level) -> runCatching { am?.setStreamVolume(stream, level, 0) } }
        }
        hide()
        com.karelisio.bulle.island.IslandLog.log("Volume : Power + volume → capture d'écran (volume inchangé)")
        val service = context as? android.accessibilityservice.AccessibilityService ?: return
        handler.postDelayed({ service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }, 250L)
    }

    private fun startRepeat(context: Context, code: Int, direction: Int) {
        stopRepeat()
        repeatCode = code
        var steps = 0
        val r = object : Runnable {
            override fun run() {
                // Garde-fou si le relâchement n'arrive jamais (service coupé…).
                if (++steps > MAX_REPEAT_STEPS || code !in consumedDown) { stopRepeat(); return }
                runCatching { adjust(context, direction) }
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        repeat = r
        handler.postDelayed(r, REPEAT_DELAY_MS)
    }

    private fun stopRepeat() {
        repeat?.let(handler::removeCallbacks)
        repeat = null
        repeatCode = 0
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
    /**
     * Affiche le panneau de volume du système (celui de MagicOS donne le volume par appli) :
     * l'île se referme puis le flux est « ajusté » sans changement avec l'interface système.
     */
    fun showSystemPanel(context: Context, stream: VolumeStream, close: () -> Unit) {
        com.karelisio.bulle.island.IslandLog.log("Volume : panneau du système demandé")
        close()
        _overlay.value = null
        val am = context.getSystemService(AudioManager::class.java) ?: return
        runCatching { am.adjustStreamVolume(stream.streamType, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI) }
            .onFailure { runCatching { am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI) } }
    }

    fun openOutputSwitcher(context: Context, close: () -> Unit) {
        com.karelisio.bulle.island.IslandLog.log("Sortie audio : bouton de la bulle Volume")
        runCatching { keepAlive(force = true) }
        val pkg = runCatching { playingPackage(context) }.getOrNull() ?: context.packageName
        MediaOutput.openFromIsland(context, pkg, close)
    }

    /** Appli qui joue en ce moment (via l'accès aux notifications), pour cibler son routage. */
    private fun playingPackage(context: Context): String? = runCatching {
        val msm = context.getSystemService(MediaSessionManager::class.java)
        val sessions = msm.getActiveSessions(NotificationRepository.component(context))
        (sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: sessions.firstOrNull())?.packageName
    }.getOrNull()

    /** Délai avant la première répétition, puis intervalle (proches du comportement système). */
    private const val REPEAT_DELAY_MS = 400L
    private const val REPEAT_INTERVAL_MS = 90L
    private const val MAX_REPEAT_STEPS = 120

}
