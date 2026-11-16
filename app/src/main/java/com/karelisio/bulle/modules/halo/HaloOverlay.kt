package com.karelisio.bulle.modules.halo

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.WindowManager.LayoutParams as LP
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import com.karelisio.bulle.island.overlay.OverlayWindow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Fenêtre d'overlay plein écran du halo (overlay du service d'accessibilité, jamais touchable).
 * Elle n'existe que le temps d'une animation, puis est retirée (aucun coût au repos).
 */
class HaloOverlay(private val context: Context) {

    private data class Show(val request: HaloRequest, val screen: HaloScreen, val look: HaloLook)

    private val window = OverlayWindow(context, "Bulle — halo", extraFlags = LP.FLAG_NOT_TOUCHABLE)
    private val current = MutableStateFlow<Show?>(null)
    private val main = Handler(Looper.getMainLooper())

    val showing: Boolean get() = current.value != null

    fun show(request: HaloRequest, screen: HaloScreen, look: HaloLook) {
        val p = window.params
        val w = screen.width.toInt().coerceAtLeast(1)
        val h = screen.height.toInt().coerceAtLeast(1)
        if (p.width != w || p.height != h || p.x != 0 || p.y != 0) {
            p.x = 0; p.y = 0; p.width = w; p.height = h
            window.commit()
        }
        current.value = Show(request, screen, look)
        if (!window.isShown) {
            window.showCompose {
                val s by current.collectAsState()
                s?.let { show ->
                    key(show.request.id) {
                        HaloCanvas(show.request, show.screen, show.look, onFinished = { finished(show) })
                    }
                }
            }
        }
    }

    private fun finished(show: Show) {
        if (current.value !== show) return
        current.value = null
        // Retirée après l'image en cours (pas pendant la composition).
        main.post { if (current.value == null) window.hide() }
    }

    fun destroy() {
        current.value = null
        window.destroy()
    }
}
