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
import com.karelisio.bulle.system.Reliability
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
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

    private val scope = MainScope()
    private var window: IslandWindow? = null
    private var controller: IslandController? = null
    private val screenTick = MutableStateFlow(0)

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
        w.onStatusBarVisibility = { visible -> c.updateScreen { it.copy(statusBarVisible = visible) } }

        // Géométrie : recalculée à chaque rotation / changement d'écran / réglage fin.
        scope.launch {
            combine(c.settings.map { it.position }.distinctUntilChanged(), screenTick) { tweak, _ -> tweak }
                .collect { tweak ->
                    val info = CutoutLocator.screenInfo(this@IslandAccessibilityService)
                    val g = CutoutLocator.geometry(info, tweak)
                    c.updateScreen { it.copy(geometry = g, landscape = info.width > info.height) }
                }
        }
        refreshLock()
        w.show { IslandScene(c) }
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
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ForegroundApp.onWindowChanged(event.packageName?.toString())
            refreshLock()
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
        if (!_running.value && window == null) return
        _running.value = false
        _controller.value = null
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener) }
        runCatching { unregisterReceiver(screenReceiver) }
        window?.destroy()
        window = null
        controller = null
    }

    companion object {
        private val _running = MutableStateFlow(false)
        /** Vrai tant que le système fait réellement tourner le service. */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _controller = MutableStateFlow<IslandController?>(null)
        /** Contrôleur de l'île en cours (null si le service ne tourne pas). */
        val controller: StateFlow<IslandController?> = _controller.asStateFlow()
    }
}
