package com.karelisio.bulle.island

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.view.accessibility.AccessibilityEvent
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.ui.IslandRoot
import com.karelisio.bulle.system.Reliability
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Cœur de Bulle : affiche l'île dans un overlay d'accessibilité
 * et observe l'appli au premier plan (nom de paquet uniquement).
 */
class IslandAccessibilityService : AccessibilityService() {

    private val scope = MainScope()
    private var window: IslandWindow? = null
    private val screenTick = MutableStateFlow(0)
    private val geometry = MutableStateFlow(IslandGeometry.Empty)

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) { screenTick.value++ }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        _running.value = true
        Reliability.ensureRunning(this)
        getSystemService(DisplayManager::class.java).registerDisplayListener(displayListener, null)

        val w = IslandWindow(this).also { window = it }
        scope.launch {
            combine(SettingsRepository.flow(this@IslandAccessibilityService), screenTick) { s, _ -> s }
                .collect { settings ->
                    if (!settings.islandEnabled) {
                        w.hide()
                        return@collect
                    }
                    val g = CutoutLocator.geometry(CutoutLocator.screenInfo(this@IslandAccessibilityService), settings.position)
                    geometry.value = g
                    w.setBounds(g.centerX, g.top, g.width, g.height)
                    w.show { IslandRoot(geometry) }
                }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        screenTick.value++
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ForegroundApp.onWindowChanged(event.packageName?.toString())
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    private fun teardown() {
        _running.value = false
        runCatching { getSystemService(DisplayManager::class.java).unregisterDisplayListener(displayListener) }
        window?.destroy()
        window = null
    }

    companion object {
        private val _running = MutableStateFlow(false)
        /** Vrai tant que le système fait réellement tourner le service. */
        val running: StateFlow<Boolean> = _running.asStateFlow()
    }
}
