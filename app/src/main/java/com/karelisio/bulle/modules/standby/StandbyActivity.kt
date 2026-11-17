package com.karelisio.bulle.modules.standby

import android.app.KeyguardManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.karelisio.bulle.core.settings.BulleSettings
import com.karelisio.bulle.core.settings.SettingsRepository
import com.karelisio.bulle.island.IslandAccessibilityService
import com.karelisio.bulle.island.IslandLog
import com.karelisio.bulle.island.IslandOcclusion
import com.karelisio.bulle.modules.standby.ui.StandbyInputs
import com.karelisio.bulle.modules.standby.ui.StandbyScreen
import com.karelisio.bulle.modules.standby.ui.rememberNow
import com.karelisio.bulle.system.OemSettings
import com.karelisio.bulle.ui.theme.BulleTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Veille, activité de secours (MagicOS peut masquer le réglage d'économiseur d'écran) :
 * plein écran au-dessus de l'écran de verrouillage, écran maintenu allumé. Lancée d'elle-même
 * (branché + horizontale), elle se ferme au débranchement ou au retour à la verticale ; lancée à
 * la main (geste, tuile, test), elle reste jusqu'à Retour.
 */
class StandbyActivity : ComponentActivity() {

    private val exiting = mutableStateOf(false)
    private val startedAt = android.os.SystemClock.elapsedRealtime()
    private val lux = MutableStateFlow<Float?>(null)
    private var sensors: SensorManager? = null

    private val light = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            lux.value = e.values.firstOrNull()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

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
        val auto = intent.getBooleanExtra(StandbyLauncher.EXTRA_AUTO, false)
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        // Vérification des autorisations MagicOS : Veille s'est bien affichée.
        if (auto) OemSettings.recordProof(this, OemSettings.Proof.BACKGROUND_POPUP)
        if (locked) OemSettings.recordProof(this, OemSettings.Proof.LOCKSCREEN)
        StandbyState.setActive(true)
        IslandOcclusion.set(OCCLUSION_TAG, true)
        IslandLog.log("Veille : affichée" + if (auto) " (automatique)" else "")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = exit()
        })
        if (auto) {
            // Débranché ou revenu à la verticale (stable 2 s) : Veille se referme. Une notification,
            // le verrouillage ou l'appli au premier plan ne la ferment jamais.
            lifecycleScope.launch {
                StandbyState.keep.collectLatest { keep ->
                    if (keep) return@collectLatest
                    kotlinx.coroutines.delay(2_000L)
                    if (!StandbyState.keep.value) {
                        IslandLog.log("Veille : fermée (débranchée ou verticale)")
                        exit()
                    }
                }
            }
        }
        sensors = getSystemService(SensorManager::class.java)
        setContent { BulleTheme(dark = true) { Content() } }
    }

    @Composable
    private fun Content() {
        val scope = rememberCoroutineScope()
        val settings by remember { SettingsRepository.flow(this) }.collectAsState(BulleSettings())
        val controller by IslandAccessibilityService.controller.collectAsState()
        val contents by remember(controller) { controller?.contents ?: flowOf(emptyList()) }.collectAsState(emptyList())
        val echo by StandbyState.echo.collectAsState()
        val light by lux.collectAsState()
        val now = rememberNow(seconds = false)
        val s = settings.standby
        val minute = Calendar.getInstance().apply { timeInMillis = now }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val night = StandbyLogic.isNight(s.nightMode, minute, s.nightStart, s.nightEnd, light, s.darkLux)
        LaunchedEffect(night) {
            window.attributes = window.attributes.apply {
                screenBrightness = if (night) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            IslandLog.log(if (night) "Veille : mode nuit" else "Veille : mode jour")
        }
        val islandRect = remember(controller) {
            controller?.state?.value?.geometry?.takeIf { it.width > 0 }?.let { g ->
                Rect(Offset((g.centerX - g.width / 2).toFloat(), g.top.toFloat()), Size(g.width.toFloat(), g.height.toFloat()))
            }
        }
        // Musique coupée à l'ouverture de Veille (lecteur qui se met en pause quand une activité
        // passe devant) : relancée une fois si personne n'a touché les commandes.
        val music = contents.filterIsInstance<com.karelisio.bulle.modules.music.MusicContent>().firstOrNull()
        val wasPlaying = remember(controller) { music?.playback?.isPlaying == true }
        var resumed by remember { mutableStateOf(false) }
        LaunchedEffect(music?.playback?.isPlaying) {
            val m = music ?: return@LaunchedEffect
            val now = android.os.SystemClock.elapsedRealtime()
            if (!resumed && StandbyLogic.shouldResume(wasPlaying, m.playback.isPlaying, startedAt, now, StandbyState.lastMediaTouch)) {
                resumed = true
                IslandLog.log("Veille : lecture reprise (coupée à l'ouverture par ${m.track.packageName})")
                runCatching { m.controller.transportControls.play() }
            }
        }
        val locked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
        StandbyScreen(
            StandbyInputs(s, night, contents, controller, echo, locked, islandRect),
            exiting = exiting.value,
            onExited = { finish() },
            onClockChange = { c ->
                if (c != s.clock) scope.launch { SettingsRepository.update(this@StandbyActivity) { it.copy(standby = it.standby.copy(clock = c)) } }
            },
        )
    }

    /** Sortie : l'animation inverse (l'écran redevient l'île), puis fermeture. */
    private fun exit() {
        if (exiting.value) return
        exiting.value = true
    }

    override fun onResume() {
        super.onResume()
        sensors?.getDefaultSensor(Sensor.TYPE_LIGHT)?.let { sensors?.registerListener(light, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    override fun onPause() {
        sensors?.unregisterListener(light)
        super.onPause()
    }

    override fun onDestroy() {
        StandbyState.setActive(false)
        IslandOcclusion.set(OCCLUSION_TAG, false)
        IslandLog.log("Veille : fermée")
        super.onDestroy()
    }

    companion object {
        const val OCCLUSION_TAG = "standby"
    }
}
