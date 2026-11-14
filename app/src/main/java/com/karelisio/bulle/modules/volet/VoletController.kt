package com.karelisio.bulle.modules.volet

import android.accessibilityservice.AccessibilityService
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.karelisio.bulle.core.actions.ActionType
import com.karelisio.bulle.core.actions.GestureAction
import com.karelisio.bulle.island.CutoutLocator
import com.karelisio.bulle.island.ForegroundApp
import com.karelisio.bulle.island.IslandController
import com.karelisio.bulle.modules.volet.ui.VoletPanel
import com.karelisio.bulle.notifications.NotificationIntents
import com.karelisio.bulle.ui.components.isReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Contrôleur du Volet : zones de déclenchement, fenêtre du panneau, ouverture au doigt
 * (0 fermé, 1 à demi, 2 complet) et ressorts au relâchement.
 * Vit dans le service d'accessibilité, à côté de l'île.
 */
class VoletController(
    private val service: AccessibilityService,
    private val scope: CoroutineScope,
    private val island: IslandController,
) : VoletHost, StripListener {

    override val context: Context get() = service
    private val density = service.resources.displayMetrics.density

    private val settingsFlow: StateFlow<VoletSettings> =
        island.storedSettings.map { it.volet }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, VoletSettings())

    private val anim = Animatable(0f)
    override val expansion: Float get() = anim.value
    override var zone by mutableStateOf(VoletZone.NOTIFICATIONS)
        private set
    override var settings by mutableStateOf(VoletSettings())
        private set
    override var locked by mutableStateOf(false)
        private set
    override var focusKey by mutableStateOf<String?>(null)
        private set
    override var editingTiles by mutableStateOf(false)
    override var haptics by mutableStateOf(true)
        private set

    /** Progression du geste Retour prédictif (0 – 1). */
    var backProgress by mutableFloatStateOf(0f)
        private set

    /** Distance du doigt pour passer de fermé à demi, puis de demi à complet. */
    private val halfDistance = 320f * density
    private val fullDistance = 360f * density

    private var panel: VoletPanelWindow? = null
    private val strips = TriggerStrips(service, this)
    private var dragLimit = 2f
    private var settleJob: Job? = null
    private var blurJob: Job? = null
    private val refreshStrips = MutableStateFlow(0)

    /** Collectes lancées par [start], arrêtées par [destroy] (sinon les zones seraient recréées après coup). */
    private val jobs = mutableListOf<Job>()

    fun start() {
        jobs += scope.launch { settingsFlow.collect { settings = it; if (!it.enabled) closeNow() } }
        jobs += scope.launch {
            island.storedSettings.map { it.motion.haptics }.distinctUntilChanged().collect { haptics = it }
        }
        // Appareil verrouillé pendant que le volet est ouvert (écran éteint…) : on le referme,
        // comme le volet du système, plutôt que de le laisser ouvert par-dessus l'écran de verrouillage.
        jobs += scope.launch {
            island.screenEnv.map { it.locked }.distinctUntilChanged().collect { nowLocked ->
                if (nowLocked && panel != null) closeNow()
            }
        }
        jobs += scope.launch {
            combine(
                settingsFlow,
                island.screenEnv,
                island.state.map { Triple(it.expanded, it.visible, it.primary != null) }.distinctUntilChanged(),
                ForegroundApp.packageName,
                combine(previewFlow, refreshStrips, VoletStrips.islandEnvelope) { p, _, e -> p to e },
            ) { s, env, islandState, fg, previewAndEnvelope ->
                val (islandExpanded, islandVisible, hasContent) = islandState
                val (preview, published) = previewAndEnvelope
                locked = env.locked
                if (!s.enabled) return@combine null
                val active = !islandExpanded &&
                    env.statusBarVisible &&
                    (!env.locked || s.showOnLockscreen) &&
                    (fg == null || fg !in s.excludedApps)
                // Île étendue : zones inactives ; l'estimation stable évite de replacer les fenêtres
                // à chaque image de l'animation d'extension.
                val envelope = if (islandExpanded) null else published
                Triple(stripZones(s, env.geometry, islandVisible, hasContent, envelope), active, preview)
            }.distinctUntilChanged().collect { r ->
                if (r == null) strips.destroy()
                else strips.update(r.first, touchable = r.second, preview = r.third, previewColor = PREVIEW_COLOR)
            }
        }
    }

    fun destroy() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        closeNow()
        strips.destroy()
    }

    /**
     * Zones : toute la largeur, sur au moins la hauteur de la barre d'état, bordant exactement
     * l'enveloppe tactile de l'île (publiée par l'île, sinon estimée). L'île masquée ou vide
     * invisible (non touchable) n'est pas épargnée : aucun trou au centre.
     */
    private fun stripZones(
        s: VoletSettings,
        g: com.karelisio.bulle.island.IslandGeometry,
        islandVisible: Boolean,
        hasContent: Boolean,
        published: IslandEnvelope?,
    ): List<VoletStrips.Zone> {
        val info = CutoutLocator.screenInfo(service)
        val w = info.width
        val trigger = if (s.triggerHeightDp > 0f) (s.triggerHeightDp * density).roundToInt() else 0
        val statusBar = info.statusBarHeight.coerceAtLeast((24 * density).roundToInt())
        val centerX = if (g.width > 0) g.centerX else w / 2
        val envelope = when {
            !islandVisible -> null
            published != null -> published
            // Sans enveloppe publiée : île au repos exacte ; avec un contenu compact, marge prudente
            // pour ne pas recouvrir ses ailes (les zones sont au-dessus de la fenêtre tactile de l'île).
            else -> VoletStrips.estimate(
                centerX, g.top, g.width, g.height, visible = true,
                contentMarginPx = if (hasContent) (52 * density).roundToInt() else 0,
            )
        }
        return VoletStrips.zones(w, statusBar, trigger, (s.edgeInsetDp * density).roundToInt(), centerX, envelope)
    }

    private fun zoneForSide(side: Int): VoletZone = when (settings.zones) {
        VoletZones.SPLIT -> if (side == 0) VoletZone.NOTIFICATIONS else VoletZone.QUICK_SETTINGS
        VoletZones.SPLIT_REVERSED -> if (side == 0) VoletZone.QUICK_SETTINGS else VoletZone.NOTIFICATIONS
        VoletZones.SINGLE -> VoletZone.NOTIFICATIONS
    }

    // --- Glissement depuis une zone de déclenchement ---

    override fun onStripDragStart(side: Int) {
        zone = zoneForSide(side)
        focusKey = null
        // Depuis les notifications, le premier glissement s'arrête à demi.
        dragLimit = if (zone == VoletZone.QUICK_SETTINGS) 2f else 1f
        if (!ensurePanel()) {
            openSystemShade()
            return
        }
        settleJob?.cancel()
        if (haptics) panel?.view?.performHapticFeedback(HapticFeedbackConstants.GESTURE_START)
    }

    override fun onStripDrag(dyPx: Float) {
        // Pas de panneau (repli sur le volet système, volet désactivé pendant le geste) :
        // le doigt ne doit pas faire bouger l'ouverture, sinon elle resterait non nulle
        // et la prochaine ouverture repartirait de cette valeur.
        if (panel == null) return
        moveBy(dyPx)
    }

    override fun onStripDragEnd(velocityPxPerSec: Float) {
        settle(velocityPxPerSec)
        dragLimit = 2f
    }

    // --- VoletHost ---

    override fun dragBy(dyPx: Float) {
        if (panel == null) return
        dragLimit = 2f
        moveBy(dyPx)
    }

    private fun moveBy(dyPx: Float) {
        settleJob?.cancel()
        val cur = anim.value
        val dist = if (cur < 1f || (cur == 1f && dyPx < 0)) halfDistance else fullDistance
        var next = cur + dyPx / dist
        // Élastique au-delà de la limite.
        if (next > dragLimit) next = dragLimit + (next - dragLimit) * 0.25f
        next = next.coerceIn(0f, dragLimit + 0.12f)
        snap(next)
    }

    override fun settle(velocityPxPerSec: Float) {
        if (panel == null) return
        val cur = anim.value
        val v = velocityPxPerSec / (if (cur < 1f) halfDistance else fullDistance)
        animateTo(VoletMotion.settleTarget(cur, v, zone, dragLimit), v)
    }

    override fun close() = animateTo(0f, 0f)
    override fun expandFull() = animateTo(2f, 0f)
    override fun collapseToHalf() = animateTo(1f, 0f)

    /** Ouvre le volet sur une notification (depuis l'île). */
    fun openFor(notificationKey: String?) {
        if (!settings.enabled || !ensurePanel()) return
        zone = VoletZone.NOTIFICATIONS
        focusKey = notificationKey
        animateTo(1f, 0f)
    }

    override fun send(intent: PendingIntent?, fillIn: Intent?, closeAfter: Boolean): Boolean {
        val ok = NotificationIntents.send(service, intent, fillIn)
        if (ok && closeAfter) close()
        return ok
    }

    override fun startActivity(intent: Intent) {
        close()
        runCatching { service.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    override fun runAction(action: GestureAction) {
        // La capture et le verrouillage se font volet fermé.
        if (action.type == ActionType.SCREENSHOT || action.type == ActionType.LOCK_SCREEN) {
            closeNow()
            scope.launch { delay(250); island.runAction(action) }
        } else {
            island.runAction(action)
        }
    }

    override fun requestKeyboard(show: Boolean) {
        panel?.setFocusable(true, showKeyboard = show)
    }

    override fun openSystemShade() {
        closeNow()
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    // --- Panneau ---

    private fun ensurePanel(): Boolean {
        if (panel != null) return true
        val p = VoletPanelWindow(service)
        p.onBack = {
            if (anim.value > 1.05f) collapseToHalf() else close()
            true
        }
        p.onBackProgress = { backProgress = it }
        if (!p.show { VoletPanel(this) }) {
            p.destroy()
            return false
        }
        // Focusable pendant l'ouverture : touche Retour et saisie (réponse directe).
        p.setFocusable(true)
        panel = p
        blurJob = scope.launch {
            var lastStep = 0
            snapshotFlow { anim.value }.collect { e ->
                p.setBlur((settings.blurRadiusDp * density * min(e, 1f)).roundToInt())
                // Petit cran haptique au passage de la demi-ouverture et de l'ouverture complète.
                val step = when {
                    e >= 1.98f -> 2
                    e >= 0.98f -> 1
                    else -> 0
                }
                if (step > lastStep && haptics) p.view?.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
                lastStep = step
            }
        }
        return true
    }

    private fun snap(value: Float) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { anim.snapTo(value) }
    }

    private fun animateTo(target: Float, velocity: Float) {
        if (panel == null) return
        settleJob?.cancel()
        val reduce = isReduceMotion(service)
        // Horloge d'images indispensable à Animatable hors composition : AndroidUiDispatcher.Main la fournit
        // (sans elle : IllegalStateException « MonotonicFrameClock is not available », plantage à l'ouverture).
        settleJob = scope.launch(androidx.compose.ui.platform.AndroidUiDispatcher.Main) {
            if (reduce) anim.snapTo(target)
            else anim.animateTo(target, spring(dampingRatio = 0.82f, stiffness = 380f, visibilityThreshold = 0.001f), velocity)
            if (target == 0f) closeNow()
        }
    }

    /** Retire immédiatement la fenêtre du volet. */
    private fun closeNow() {
        settleJob?.cancel()
        blurJob?.cancel()
        blurJob = null
        panel?.destroy()
        panel = null
        editingTiles = false
        focusKey = null
        backProgress = 0f
        scope.launch(start = CoroutineStart.UNDISPATCHED) { anim.snapTo(0f) }
        refreshStrips.value++
    }

    companion object {
        private const val PREVIEW_COLOR = 0x5582A0FF

        /** Aperçu des zones pendant leur réglage (écran de réglages du module). */
        val previewFlow = MutableStateFlow(false)

        private val _current = MutableStateFlow<VoletController?>(null)
        val current: StateFlow<VoletController?> = _current.asStateFlow()

        internal fun register(c: VoletController?) {
            _current.value = c
        }
    }
}


/** Logique pure du relâchement (testable). */
object VoletMotion {
    /**
     * Position d'arrivée après relâchement : 0 fermé, 1 à demi, 2 complet.
     * [velocity] est exprimée en « expansions par seconde » (positive vers le bas).
     */
    fun settleTarget(cur: Float, velocity: Float, zone: VoletZone, dragLimit: Float): Float {
        var target = when {
            velocity > 1.2f -> if (cur >= 1f) 2f else 1f
            velocity < -1.2f -> if (cur > 1.02f) 1f else 0f
            cur < 0.4f -> 0f
            cur < 1.45f -> 1f
            else -> 2f
        }
        // Depuis la zone des réglages rapides, un lancer vers le bas ouvre directement la grille.
        if (zone == VoletZone.QUICK_SETTINGS && dragLimit >= 2f && cur in 0.15f..1f && velocity > 1.2f) target = 2f
        return target.coerceAtMost(dragLimit.coerceAtLeast(1f))
    }
}
