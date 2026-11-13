package com.karelisio.bulle.modules.music.karaoke

import android.graphics.Color
import androidx.core.graphics.drawable.toDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.karelisio.bulle.ui.theme.BulleTheme

/**
 * Paroles plein écran « karaoké », ouvertes depuis la carte musique de l'île.
 * Plein écran immersif (barres système masquées, révélées d'un glissement), écran gardé allumé
 * tant que la vue est affichée ; fermeture par Retour ou glisser vers le bas.
 */
class KaraokeActivity : ComponentActivity() {

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val finishIfEmpty = Runnable { if (KaraokeBridge.content.value == null) finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Fond noir immédiat (pas de flash du thème de l'appli) et écran gardé allumé.
        window.setBackgroundDrawable(Color.BLACK.toDrawable())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            BulleTheme(dark = true) {
                val content by KaraokeBridge.content.collectAsStateWithLifecycle()
                val lifecycle by lifecycle.currentStateAsState()
                val current = content
                if (current != null) {
                    KaraokeScreen(
                        content = current,
                        onClose = ::finish,
                        active = lifecycle.isAtLeast(Lifecycle.State.STARTED),
                    )
                } else {
                    // Plus de session média (ou processus recréé) : fermeture après un court délai de
                    // grâce (une session qui se reconnecte aussitôt ne ferme pas la vue).
                    DisposableEffect(Unit) {
                        handler.postDelayed(finishIfEmpty, EMPTY_GRACE_MS)
                        onDispose { handler.removeCallbacks(finishIfEmpty) }
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        // Sortie en fondu (le geste de fermeture a déjà fait glisser le contenu vers le bas).
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    private companion object {
        const val EMPTY_GRACE_MS = 600L
    }
}
