package com.karelisio.bulle.modules.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.karelisio.bulle.R

/** Canaux et notifications de la capture (service en cours, fin d'enregistrement). */
object CaptureNotifications {
    const val CHANNEL_ONGOING = "capture_ongoing"
    const val CHANNEL_DONE = "capture_done"
    const val ID_MEMO = 0x4361_01
    const val ID_SCREEN = 0x4361_02
    const val ID_SCREEN_DONE = 0x4361_03

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, context.getString(R.string.capture_channel_ongoing), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, context.getString(R.string.capture_channel_done), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    /** Notification du service de premier plan, avec un bouton « Arrêter ». */
    fun ongoing(context: Context, title: String, stop: PendingIntent): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_bulle)
            .setContentTitle(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.capture_stop), stop)
            .setContentIntent(stop)
            .build()
    }

    /** Fin d'enregistrement d'écran : « Ouvrir » / « Partager ». */
    fun screenDone(context: Context, uri: Uri) {
        ensureChannels(context)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val share = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val openPi = PendingIntent.getActivity(context, 1, view, flags)
        val sharePi = PendingIntent.getActivity(context, 2, share, flags)
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_bulle)
            .setContentTitle(context.getString(R.string.capture_screen_saved))
            .setContentText(context.getString(R.string.capture_screen_saved_text))
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, context.getString(R.string.capture_open), openPi)
            .addAction(0, context.getString(R.string.capture_share), sharePi)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_SCREEN_DONE, n) }
    }
}
