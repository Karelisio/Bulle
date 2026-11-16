package com.karelisio.bulle.modules.halo

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandLog
import kotlinx.coroutines.delay

/**
 * Halo écran éteint : allume l'écran au-dessus de l'écran de verrouillage, dessine le halo sur un
 * noir pur pendant quelques secondes (sur un écran OLED, seuls les pixels du bord s'allument),
 * puis demande au service de rééteindre l'écran. Un toucher ferme tout de suite, sans rééteindre.
 */
class HaloNightActivity : ComponentActivity() {

    private var touched = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val request = requestFrom(intent)
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        // Vérification des autorisations MagicOS : affiché depuis l'arrière-plan (et sur l'écran verrouillé).
        com.karelisio.bulle.system.OemSettings.recordProof(this, com.karelisio.bulle.system.OemSettings.Proof.BACKGROUND_POPUP)
        if (locked) com.karelisio.bulle.system.OemSettings.recordProof(this, com.karelisio.bulle.system.OemSettings.Proof.LOCKSCREEN)
        setContent {
            var look by remember { mutableStateOf<HaloLook?>(null) }
            var seconds by remember { mutableStateOf(4) }
            LaunchedEffect(Unit) {
                val s = SettingsRepository.current(this@HaloNightActivity).halo
                seconds = s.screenOffSeconds.coerceIn(HaloSettings.SCREEN_OFF_SECONDS)
                look = HaloLook.of(s, resources.displayMetrics.density)
            }
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                look?.let { l ->
                    // Passages enchaînés pendant la durée choisie.
                    HaloCanvas(request, HaloDevice.screen(this@HaloNightActivity), l, loop = true)
                    LaunchedEffect(l) {
                        delay(seconds * 1000L)
                        finishAndRelock(locked)
                    }
                }
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            touched = true
            finish()
        }
        return true
    }

    private fun finishAndRelock(wasLocked: Boolean) {
        if (isFinishing) return
        finish()
        // L'écran était éteint : on le rééteint (l'utilisateur ne s'en servait pas).
        if (!touched && wasLocked) HaloBus.requestRelock()
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_COLOR = "color"
        private const val EXTRA_STYLE = "style"

        fun launch(context: Context, request: HaloRequest) {
            val intent = Intent(context, HaloNightActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .putExtra(EXTRA_KIND, request.kind.name)
                .putExtra(EXTRA_COLOR, request.color)
                .putExtra(EXTRA_STYLE, request.style.name)
            runCatching { context.startActivity(intent) }
                .onFailure { IslandLog.log("Halo écran éteint : lancement refusé (${it.javaClass.simpleName})") }
        }

        internal fun requestFrom(intent: Intent?): HaloRequest = HaloRequest(
            kind = intent?.getStringExtra(EXTRA_KIND)?.let { runCatching { HaloKind.valueOf(it) }.getOrNull() } ?: HaloKind.NOTIFICATION,
            color = intent?.getIntExtra(EXTRA_COLOR, HaloPalette.DEFAULT) ?: HaloPalette.DEFAULT,
            style = intent?.getStringExtra(EXTRA_STYLE)?.let { runCatching { HaloStyle.valueOf(it) }.getOrNull() } ?: HaloStyle.SIGNATURE,
        )
    }
}
