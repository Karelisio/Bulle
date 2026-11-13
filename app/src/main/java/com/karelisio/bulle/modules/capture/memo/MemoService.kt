package com.karelisio.bulle.modules.capture.memo

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.capture.CaptureNotifications
import com.karelisio.bulle.modules.capture.CapturePrefs
import com.karelisio.bulle.modules.capture.CaptureState
import com.karelisio.bulle.modules.capture.MemoEvent
import com.karelisio.bulle.modules.capture.MemoMachine
import com.karelisio.bulle.modules.capture.MemoPhase
import java.io.File

/**
 * Mémo vocal : service de premier plan de type « microphone » qui enregistre en AAC (.m4a)
 * dans le stockage privé. Le chrono et le niveau sonore sont publiés dans [CaptureState].
 *
 * Démarrage depuis l'arrière-plan (Android 12+ / 14+) : un service de premier plan « microphone »
 * ne peut démarrer que si l'appli est « visible » ou bénéficie d'une exemption. Le service
 * d'accessibilité de l'île, lié par le système, en est une (BFSL + accès micro « while-in-use ») :
 * le démarrage direct depuis un geste de l'île fonctionne donc. Si le démarrage est refusé malgré
 * tout (service d'accessibilité coupé, constructeur plus strict), [start] se rabat sur
 * [MemoStartActivity], activité transparente qui place l'appli au premier plan, demande
 * RECORD_AUDIO si besoin puis démarre le service.
 */
class MemoService : Service() {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private val main = Handler(Looper.getMainLooper())
    private val poll = object : Runnable {
        override fun run() {
            val r = recorder ?: return
            if (CaptureState.memo.value.phase == MemoPhase.RECORDING) {
                CaptureState.memoLevel(MemoMachine.level(runCatching { r.maxAmplitude }.getOrDefault(0)))
            }
            main.postDelayed(this, LEVEL_PERIOD_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> begin(fromActivity = intent.getBooleanExtra(EXTRA_FROM_ACTIVITY, false))
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_STOP -> finishRecording()
            else -> if (recorder == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(fromActivity: Boolean) {
        if (recorder != null) return
        val stopPi = PendingIntent.getService(this, 0, intent(this, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val notification = CaptureNotifications.ongoing(this, getString(R.string.capture_memo_recording), stopPi)
        try {
            ServiceCompat.startForeground(this, CaptureNotifications.ID_MEMO, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } catch (e: Exception) {
            // Démarrage refusé (arrière-plan sans exemption, permission absente) : repli sur l'activité.
            Log.w(TAG, "Premier plan refusé, repli sur l'activité", e)
            stopSelf()
            // Depuis l'activité (premier plan), pas de nouveau repli : on évite toute boucle.
            if (!fromActivity) MemoStartActivity.launch(this, fallback = true)
            return
        }
        val quality = CapturePrefs.current(this).memoQuality
        val target = MemoStore.newFile(this, getString(R.string.capture_memo_prefix))
        val r = MediaRecorder(this)
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(quality.sampleRate)
            r.setAudioEncodingBitRate(quality.bitRate)
            r.setOutputFile(target.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            Log.w(TAG, "Enregistrement impossible", e)
            r.release()
            target.delete()
            CaptureState.memo(MemoEvent.Stop(SystemClock.elapsedRealtime()))
            stopEverything()
            return
        }
        recorder = r
        file = target
        CaptureState.memo(MemoEvent.Start(SystemClock.elapsedRealtime()))
        main.post(poll)
    }

    private fun pause() {
        val r = recorder ?: return
        if (CaptureState.memo.value.phase != MemoPhase.RECORDING) return
        runCatching { r.pause() }.onSuccess { CaptureState.memo(MemoEvent.Pause(SystemClock.elapsedRealtime())) }
    }

    private fun resume() {
        val r = recorder ?: return
        if (CaptureState.memo.value.phase != MemoPhase.PAUSED) return
        runCatching { r.resume() }.onSuccess { CaptureState.memo(MemoEvent.Resume(SystemClock.elapsedRealtime())) }
    }

    private fun finishRecording() {
        val r = recorder
        val f = file
        recorder = null
        file = null
        main.removeCallbacks(poll)
        CaptureState.memo(MemoEvent.Stop(SystemClock.elapsedRealtime()))
        if (r != null) {
            val ok = runCatching { r.stop() }.isSuccess
            r.release()
            if (f != null) {
                if (!ok) {
                    f.delete() // Enregistrement trop court ou vide : fichier illisible.
                } else {
                    if (CapturePrefs.current(this).memoToMusic) {
                        val app = applicationContext
                        Thread { MemoStore.copyToMusic(app, f) }.start()
                    }
                }
                MemoStore.changed()
            }
        }
        stopEverything()
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorder != null) finishRecording()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MemoService"
        private const val LEVEL_PERIOD_MS = 80L
        const val ACTION_START = "com.karelisio.bulle.capture.MEMO_START"
        const val ACTION_PAUSE = "com.karelisio.bulle.capture.MEMO_PAUSE"
        const val ACTION_RESUME = "com.karelisio.bulle.capture.MEMO_RESUME"
        const val ACTION_STOP = "com.karelisio.bulle.capture.MEMO_STOP"
        const val EXTRA_FROM_ACTIVITY = "from_activity"

        fun intent(context: Context, action: String): Intent = Intent(context, MemoService::class.java).setAction(action)

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        /** Démarre un mémo : directement si possible, sinon via l'activité transparente. */
        fun start(context: Context) {
            if (!hasPermission(context)) {
                MemoStartActivity.launch(context, fallback = false)
                return
            }
            try {
                ContextCompat.startForegroundService(context, intent(context, ACTION_START))
            } catch (e: Exception) {
                Log.w(TAG, "Démarrage direct refusé", e)
                MemoStartActivity.launch(context, fallback = true)
            }
        }

        fun pause(context: Context) = command(context, ACTION_PAUSE)
        fun resume(context: Context) = command(context, ACTION_RESUME)
        fun stop(context: Context) = command(context, ACTION_STOP)

        /** Commande à un service déjà au premier plan (autorisé depuis l'arrière-plan). */
        private fun command(context: Context, action: String) {
            runCatching { context.startService(intent(context, action)) }
                .onFailure { Log.w(TAG, "Commande $action impossible", it) }
        }
    }
}
