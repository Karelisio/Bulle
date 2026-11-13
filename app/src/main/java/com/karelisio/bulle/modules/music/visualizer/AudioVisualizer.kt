package com.karelisio.bulle.modules.music.visualizer

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.annotation.MainThread
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat

/**
 * Visualiseur audio réel (optionnel) : [Visualizer] sur la sortie globale (session audio 0).
 *
 * Confidentialité : seules des captures FFT (amplitudes par fréquence, pas de son) sont lues, en mémoire,
 * converties aussitôt en hauteurs de barres ; rien n'est enregistré ni envoyé.
 *
 * Sobriété : chaque vue musique affichée (égaliseur compact, carte étendue, vue karaoké) s'inscrit avec
 * un jeton ; le Visualizer n'est ouvert que si [VisualizerPolicy] le permet (lecture en cours, vue
 * visible, écran allumé…) et libéré sinon. Les captures arrivent à ~25 Hz sur le fil principal.
 * En cas d'échec (Visualizer indisponible, refusé, ou muet), [bands] reste null : les vues reviennent
 * à l'égaliseur simulé.
 */
@MainThread
object AudioVisualizer {
    private const val TAG = "BulleVisualizer"

    /** Délai de grâce avant libération : passer du compact à l'étendu ne recrée pas le Visualizer. */
    private const val RELEASE_GRACE_MS = 400L

    private class Consumer(val enabled: Boolean, val playing: Boolean)

    private val consumers = HashMap<Any, Consumer>()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var appContext: Context? = null
    private var visualizer: Visualizer? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var screenOn = true
    private var smoothed: FloatArray? = null
    private var silentCount = 0

    private val _bands = mutableStateOf<FloatArray?>(null)

    /** Bandes lissées (0..1, [SpectrumMath.BANDS] valeurs, graves d'abord) ; null = pas de son réel. */
    val bands: State<FloatArray?> get() = _bands

    private val _unavailable = mutableStateOf(false)

    /** Le Visualizer a échoué sur cet appareil (affiché dans les réglages). */
    val unavailable: State<Boolean> get() = _unavailable

    private val releaseNow = Runnable { release() }

    /** Inscrit ou met à jour la vue [token]. */
    fun update(context: Context, token: Any, enabled: Boolean, playing: Boolean) {
        if (appContext == null) appContext = context.applicationContext
        consumers[token] = Consumer(enabled, playing)
        ensureScreenReceiver()
        reevaluate()
    }

    /** Désinscrit la vue [token] (sortie de composition). */
    fun remove(token: Any) {
        if (consumers.remove(token) != null) reevaluate()
    }

    /** Oublie un échec précédent (l'utilisateur vient de réactiver l'option ou d'accorder le micro). */
    fun resetFailure() {
        _unavailable.value = false
        reevaluate()
    }

    /** RECORD_AUDIO accordée ? */
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun reevaluate() {
        val ctx = appContext
        val wanting = consumers.values.filter { it.enabled }
        val inputs = VisualizerPolicy.Inputs(
            settingEnabled = wanting.isNotEmpty(),
            permissionGranted = ctx != null && hasPermission(ctx),
            visibleConsumers = wanting.size,
            playing = wanting.any { it.playing },
            screenOn = screenOn,
            unavailable = _unavailable.value,
        )
        when (VisualizerPolicy.decide(inputs)) {
            VisualizerPolicy.Action.CAPTURE -> {
                handler.removeCallbacks(releaseNow)
                start()
            }
            VisualizerPolicy.Action.RELEASE -> {
                // Écran éteint, micro retiré ou échec : tout de suite ; sinon après un court délai de grâce.
                if (!inputs.screenOn || !inputs.permissionGranted || inputs.unavailable) {
                    handler.removeCallbacks(releaseNow)
                    release()
                } else if (visualizer != null) {
                    handler.removeCallbacks(releaseNow)
                    handler.postDelayed(releaseNow, RELEASE_GRACE_MS)
                }
            }
        }
        if (consumers.isEmpty()) dropScreenReceiver()
    }

    private val listener = object : Visualizer.OnDataCaptureListener {
        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
            if (fft == null || v !== visualizer) return
            if (SpectrumMath.isSilent(fft)) {
                // Capture muette (sortie déchargée vers le DSP, certains casques) : repli après ~2 s.
                if (++silentCount >= VisualizerPolicy.SILENT_CAPTURES_BEFORE_FALLBACK && _bands.value != null) {
                    smoothed = null
                    _bands.value = null
                }
                return
            }
            silentCount = 0
            val next = SpectrumMath.smooth(smoothed, SpectrumMath.toBands(fft))
            smoothed = next
            _bands.value = next
        }
    }

    private fun start() {
        if (visualizer != null) return
        try {
            val v = Visualizer(0)
            try {
                v.enabled = false // la taille de capture ne se change que Visualizer arrêté
                v.captureSize = VisualizerPolicy.captureSize(Visualizer.getCaptureSizeRange())
                v.scalingMode = Visualizer.SCALING_MODE_NORMALIZED // indépendant du volume
                val rate = VisualizerPolicy.captureRate(Visualizer.getMaxCaptureRate())
                check(v.setDataCaptureListener(listener, rate, false, true) == Visualizer.SUCCESS) { "listener" }
                check(v.setEnabled(true) == Visualizer.SUCCESS) { "enable" }
            } catch (t: Throwable) {
                runCatching { v.release() }
                throw t
            }
            visualizer = v
            silentCount = 0
            Log.d(TAG, "Visualizer ouvert")
        } catch (t: Throwable) {
            // Indisponible (session 0 refusée, effet absent, RuntimeException du framework…) : repli.
            Log.w(TAG, "Visualizer indisponible", t)
            _unavailable.value = true
            release()
        }
    }

    private fun release() {
        val v = visualizer ?: run {
            _bands.value = null
            return
        }
        visualizer = null
        runCatching { v.setEnabled(false) }
        runCatching { v.release() }
        smoothed = null
        silentCount = 0
        _bands.value = null
        Log.d(TAG, "Visualizer libéré")
    }

    private fun ensureScreenReceiver() {
        val ctx = appContext ?: return
        if (screenReceiver != null) return
        screenOn = ctx.getSystemService(PowerManager::class.java)?.isInteractive ?: true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                screenOn = intent.action != Intent.ACTION_SCREEN_OFF
                reevaluate()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        runCatching { ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
            .onSuccess { screenReceiver = receiver }
    }

    private fun dropScreenReceiver() {
        val r = screenReceiver ?: return
        screenReceiver = null
        runCatching { appContext?.unregisterReceiver(r) }
    }
}
