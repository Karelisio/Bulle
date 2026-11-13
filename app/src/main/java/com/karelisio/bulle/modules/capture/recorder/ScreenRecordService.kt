package com.karelisio.bulle.modules.capture.recorder

import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.WindowManager
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import com.karelisio.bulle.R
import com.karelisio.bulle.modules.capture.CaptureNotifications
import com.karelisio.bulle.modules.capture.CapturePrefs
import com.karelisio.bulle.modules.capture.CaptureState
import com.karelisio.bulle.modules.capture.RecorderEvent
import com.karelisio.bulle.modules.capture.VideoSize
import com.karelisio.bulle.modules.capture.VideoSizing
import com.karelisio.bulle.modules.capture.memo.MemoService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Enregistreur d'écran : service de premier plan de type « mediaProjection » (+ « microphone »
 * si l'audio est activé). Vidéo H.264 30 i/s dans Movies/Bulle (MediaStore, fichier « en attente »
 * jusqu'à la fin). Respecte Android 14+ : premier plan AVANT getMediaProjection, callback
 * enregistré avant createVirtualDisplay, arrêt sur onStop (puce système « Arrêter le partage »).
 */
class ScreenRecordService : Service() {

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var recorder: MediaRecorder? = null
    private var pfd: ParcelFileDescriptor? = null
    private var uri: Uri? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            main.post { finishRecording(save = true) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val data = IntentCompat.getParcelableExtra(intent, EXTRA_DATA, Intent::class.java)
                val code = intent.getIntExtra(EXTRA_CODE, 0)
                if (data == null || projection != null) {
                    if (projection == null) stopSelf()
                } else {
                    begin(code, data)
                }
            }
            ACTION_STOP -> finishRecording(save = true)
            else -> if (projection == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun begin(code: Int, data: Intent) {
        val withAudio = CapturePrefs.current(this).screenMicAudio && MemoService.hasPermission(this)
        val stopPi = PendingIntent.getService(this, 0, intent(this, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
            (if (withAudio) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0)
        try {
            ServiceCompat.startForeground(
                this,
                CaptureNotifications.ID_SCREEN,
                CaptureNotifications.ongoing(this, getString(R.string.capture_screen_recording), stopPi),
                type,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Premier plan refusé", e)
            fail()
            return
        }
        try {
            val mpm = getSystemService(MediaProjectionManager::class.java)
            val mp = mpm.getMediaProjection(code, data) ?: error("Jeton refusé")
            projection = mp
            mp.registerCallback(callback, main)

            val metrics = getSystemService(WindowManager::class.java).maximumWindowMetrics
            val bounds = metrics.bounds
            val dpi = resources.configuration.densityDpi
            val size = supportedSize(VideoSizing.fit(bounds.width(), bounds.height(), CapturePrefs.current(this).videoQuality.maxShortSide))

            val target = createMediaEntry() ?: error("MediaStore indisponible")
            uri = target
            val fd = contentResolver.openFileDescriptor(target, "rw") ?: error("Fichier indisponible")
            pfd = fd

            val r = MediaRecorder(this)
            if (withAudio) r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setOutputFile(fd.fileDescriptor)
            r.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withAudio) {
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioSamplingRate(44_100)
                r.setAudioEncodingBitRate(128_000)
            }
            r.setVideoSize(size.width, size.height)
            r.setVideoFrameRate(FPS)
            r.setVideoEncodingBitRate(VideoSizing.bitrate(size, FPS))
            r.prepare()
            recorder = r

            display = mp.createVirtualDisplay(
                "Bulle-ScreenRecord", size.width, size.height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, main,
            )
            r.start()
            CaptureState.screen(RecorderEvent.Started(SystemClock.elapsedRealtime(), withAudio))
        } catch (e: Exception) {
            Log.w(TAG, "Enregistrement d'écran impossible", e)
            release(save = false)
            fail()
        }
    }

    /** Première taille acceptée par l'encodeur H.264 (réduction par paliers sinon). */
    private fun supportedSize(start: VideoSize): VideoSize {
        val caps = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .firstOrNull { it.isEncoder && it.supportedTypes.any { t -> t.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
                ?.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC)?.videoCapabilities
        }.getOrNull() ?: return start
        var s = start
        repeat(8) {
            if (fits(caps, s)) return s
            s = VideoSizing.shrink(s)
        }
        return s
    }

    private fun fits(caps: MediaCodecInfo.VideoCapabilities, s: VideoSize): Boolean =
        runCatching { caps.areSizeAndRateSupported(s.width, s.height, FPS.toDouble()) }.getOrDefault(false)

    private fun createMediaEntry(): Uri? {
        val name = "Bulle " + SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Bulle")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        return contentResolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
    }

    private fun finishRecording(save: Boolean) {
        if (projection == null && recorder == null) {
            stopEverything()
            return
        }
        val saved = release(save)
        CaptureState.screen(RecorderEvent.Stopped)
        saved?.let { CaptureNotifications.screenDone(this, it) }
        stopEverything()
    }

    /** Libère tout ; renvoie l'URI du fichier finalisé (ou null s'il est supprimé). */
    private fun release(save: Boolean): Uri? {
        val r = recorder
        recorder = null
        val ok = r != null && runCatching { r.stop() }.isSuccess
        r?.release()
        display?.release()
        display = null
        projection?.let { p ->
            projection = null
            runCatching { p.unregisterCallback(callback) }
            runCatching { p.stop() }
        }
        runCatching { pfd?.close() }
        pfd = null
        val u = uri ?: return null
        uri = null
        return if (save && ok) {
            runCatching { contentResolver.update(u, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null) }
            u
        } else {
            runCatching { contentResolver.delete(u, null, null) }
            null
        }
    }

    private fun fail() {
        CaptureState.screen(RecorderEvent.Stopped)
        stopEverything()
    }

    private fun stopEverything() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (projection != null || recorder != null) finishRecording(save = true)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreenRecord"
        const val FPS = 30
        const val ACTION_START = "com.karelisio.bulle.capture.SCREEN_START"
        const val ACTION_STOP = "com.karelisio.bulle.capture.SCREEN_STOP"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        fun intent(context: Context, action: String): Intent = Intent(context, ScreenRecordService::class.java).setAction(action)

        /** Appelé par l'activité de consentement (premier plan) avec le jeton MediaProjection. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val i = intent(context, ACTION_START).putExtra(EXTRA_CODE, resultCode).putExtra(EXTRA_DATA, data)
            runCatching { context.startForegroundService(i) }.onFailure {
                Log.w(TAG, "Démarrage impossible", it)
                CaptureState.screen(RecorderEvent.Stopped)
            }
        }

        fun stop(context: Context) {
            runCatching { context.startService(intent(context, ACTION_STOP)) }
                .onFailure { Log.w(TAG, "Arrêt impossible", it) }
        }
    }
}
