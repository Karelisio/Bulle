package com.karelisio.bulle.island

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.accessibility.AccessibilityEvent
import com.karelisio.bulle.island.ui.IslandScene
import com.karelisio.bulle.modules.volet.VoletController
import com.karelisio.bulle.system.Reliability
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Cœur de Bulle : affiche l'île dans un overlay d'accessibilité
 * et observe l'appli au premier plan (nom de paquet uniquement).
 */
class IslandAccessibilityService : AccessibilityService() {

    // Une portée par connexion : annulée à chaque déconnexion (onUnbind), recréée à la reconnexion.
    private var scope = MainScope()
    private var window: IslandWindow? = null
    private var controller: IslandController? = null
    private var volet: com.karelisio.bulle.modules.volet.VoletController? = null
    private val screenTick = MutableStateFlow(0)
    private var windowListWorks = false
    private var fullscreenJob: kotlinx.coroutines.Job? = null
    private val fullscreenFlips = ArrayDeque<Long>()
    private var fullscreenUnstable = false

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) { screenTick.value++ }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshLock()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Reconnexion sans déconnexion préalable : on libère d'abord l'ancienne île
        // (sinon fenêtre orpheline, receiver et coroutines en double).
        teardown()
        if (!scope.isActive) scope = MainScope()
        IslandLog.log("Service connecté")
        fullscreenFlips.clear()
        fullscreenUnstable = false
        windowListWorks = false
        _running.value = true
        Reliability.ensureRunning(this)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, null)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_USER_PRESENT)
            },
        )

        val w = IslandWindow(this).also { window = it }
        val c = IslandController(this, scope, w).also { controller = it }
        _controller.value = c
        w.onOutsideTouch = c::onOutsideTouch
        w.onBack = c::onBack
        // Signal de secours (insets) seulement si la liste des fenêtres est indisponible.
        w.onStatusBarVisibility = { visible -> if (!windowListWorks) c.updateScreen { it.copy(statusBarVisible = visible) } }

        // Géométrie : recalculée à chaque rotation / changement d'écran / réglage fin.
        scope.launch {
            combine(
                c.settings.map { it.position to it.style.size }.distinctUntilChanged(),
                screenTick,
            ) { p, _ -> p }
                .collect { (tweak, size) ->
                    val info = CutoutLocator.screenInfo(this@IslandAccessibilityService)
                    val g = CutoutLocator.geometry(info, tweak, size)
                    c.updateScreen { it.copy(geometry = g, landscape = info.width > info.height) }
                }
        }
        refreshLock()
        ForegroundApp.refreshInputMethods(this)
        w.show { IslandScene(c) }
        // Volet : créé après l'île ; ne pose aucune fenêtre tant qu'il est désactivé.
        volet = VoletController(this, scope, c).also { it.start(); VoletController.register(it) }
        // Historique des notifications (si activé dans les réglages du Volet).
        com.karelisio.bulle.modules.volet.history.NotificationHistory.start(this, scope, c.storedSettings.map { it.volet })
    }

    /**
     * Plein écran : présence de la fenêtre de barre d'état dans la liste des fenêtres.
     * Hystérésis : affichage immédiat, masquage seulement si la barre reste absente sur deux
     * contrôles espacés. Garde-fou : si la détection oscille, elle est coupée pour cette session
     * (l'île reste affichée plutôt que de scintiller).
     */
    private fun refreshFullscreen() {
        val c = controller ?: return
        if (fullscreenUnstable) return
        val visible = detectStatusBar() ?: return
        if (visible) windowListWorks = true
        // Tant que la barre d'état n'a jamais été repérée, le signal n'est pas fiable sur cet appareil.
        if (!windowListWorks) return
        if (visible) {
            fullscreenJob?.cancel()
            fullscreenJob = null
            setStatusBarVisible(c, true)
        } else if (fullscreenJob?.isActive != true && c.screenEnv.value.statusBarVisible) {
            // Anti-rebond : pendant les transitions, la barre peut manquer un instant.
            fullscreenJob = scope.launch {
                delay(600)
                if (detectStatusBar() != false) return@launch
                delay(600)
                if (detectStatusBar() == false) setStatusBarVisible(c, false)
            }
        }
    }

    private fun setStatusBarVisible(c: IslandController, visible: Boolean) {
        if (c.screenEnv.value.statusBarVisible == visible) return
        val now = android.os.SystemClock.uptimeMillis()
        fullscreenFlips.addLast(now)
        while (fullscreenFlips.isNotEmpty() && now - fullscreenFlips.first() > FLAP_WINDOW_MS) fullscreenFlips.removeFirst()
        if (fullscreenFlips.size >= FLAP_LIMIT) {
            fullscreenUnstable = true
            fullscreenJob?.cancel()
            IslandLog.log("Plein écran : détection instable (${fullscreenFlips.size} bascules en 1 min), désactivée pour cette session")
            c.updateScreen { it.copy(statusBarVisible = true) }
            return
        }
        IslandLog.log(if (visible) "Barre d'état visible" else "Plein écran détecté")
        c.updateScreen { it.copy(statusBarVisible = visible) }
    }

    private fun detectStatusBar(): Boolean? {
        val info = CutoutLocator.screenInfo(this)
        return FullscreenDetector.statusBarVisible(this, info.width, info.statusBarHeight)
    }

    private fun refreshLock() {
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        controller?.updateScreen { it.copy(locked = locked) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screenTick.value++
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                ForegroundApp.onWindowChanged(event.packageName?.toString())
                refreshLock()
                refreshFullscreen()
            }
            // Les changements de nos propres fenêtres (animations de l'île, volet) ne disent rien
            // de la barre d'état : les ignorer évite une boucle masquage ↔ affichage.
            AccessibilityEvent.TYPE_WINDOWS_CHANGED ->
                if (!FullscreenDetector.isOwnOverlay(this, event.windowId)) refreshFullscreen()
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    private fun teardown() {
        if (window == null && controller == null) return
        IslandLog.log("Service arrêté")
        // Ne touche à l'état global que s'il décrit encore cette instance du service.
        if (_controller.value === controller) {
            _running.value = false
            _controller.value = null
        }
        fullscreenJob?.cancel()
        fullscreenJob = null
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener) }
        runCatching { unregisterReceiver(screenReceiver) }
        if (VoletController.current.value === volet) VoletController.register(null)
        volet?.destroy()
        volet = null
        controller?.release()
        window?.destroy()
        window = null
        controller = null
        // Arrête modules, règles et historique liés à cette connexion (sinon ils tournent
        // jusqu'à onDestroy, ou en double après une reconnexion).
        scope.cancel()
    }

    companion object {
        private const val FLAP_WINDOW_MS = 60_000L
        private const val FLAP_LIMIT = 6

        private val _running = MutableStateFlow(false)
        /** Vrai tant que le système fait réellement tourner le service. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _controller = MutableStateFlow<IslandController?>(null)
        /** Contrôleur de l'île en cours (null si le service ne tourne pas). */
        val controller: StateFlow<IslandController?> = _controller.asStateFlow()
    }
}
