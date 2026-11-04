package com.karelisio.bulle.island

import android.content.Context
import android.view.WindowInsets
import android.view.WindowManager.LayoutParams as LP
import androidx.compose.runtime.Composable
import com.karelisio.bulle.island.overlay.OverlayWindow

/**
 * Fenêtre d'overlay qui héberge l'île.
 *
 * Ancrée en haut et centrée horizontalement sur la caméra, elle est dimensionnée
 * au plus juste (enveloppe de l'île) : elle ne capte jamais les touchers en dehors.
 * Non focusable, sauf pendant une saisie (recherche d'applis).
 */
class IslandWindow(context: Context) : OverlayWindow(context, "Bulle") {

    var onStatusBarVisibility: (Boolean) -> Unit = {}
    private var statusBarSignalReliable = false

    init {
        // La fenêtre d'accessibilité est au-dessus de la barre d'état : sur certains systèmes
        // celle-ci n'est jamais signalée visible. On n'utilise le signal « masquée » que si
        // on l'a déjà vue visible au moins une fois (sinon l'île serait masquée en permanence).
        onInsets = { insets ->
            val visible = insets.isVisible(WindowInsets.Type.statusBars())
            if (visible) statusBarSignalReliable = true
            onStatusBarVisibility(visible || !statusBarSignalReliable)
        }
    }

    fun show(content: @Composable () -> Unit) {
        showCompose(content)
    }

    /**
     * Place la fenêtre (centrée horizontalement sur [centerX], bord haut à [top]) et
     * règle sa réactivité aux touchers, en une seule mise à jour du WindowManager.
     */
    fun update(centerX: Int, top: Int, width: Int, height: Int, touchable: Boolean, watchOutside: Boolean) {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        var flags = params.flags
        flags = if (touchable) flags and LP.FLAG_NOT_TOUCHABLE.inv() else flags or LP.FLAG_NOT_TOUCHABLE
        flags = if (watchOutside) flags or LP.FLAG_WATCH_OUTSIDE_TOUCH else flags and LP.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        val x = centerX - w / 2
        if (params.x == x && params.y == top && params.width == w && params.height == h && params.flags == flags) return
        params.x = x
        params.y = top
        params.width = w
        params.height = h
        params.flags = flags
        commit()
    }
}
