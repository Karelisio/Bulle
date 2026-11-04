package com.karelisio.bulle.notifications

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/**
 * Déclenche les PendingIntent des notifications des autres applis (actions, ouverture).
 * Depuis Android 14, l'émetteur doit autoriser explicitement le lancement d'activité
 * en arrière-plan : l'île est visible, on transmet donc notre autorisation.
 */
object NotificationIntents {

    /** [fillIn] : intent complémentaire (réponse directe RemoteInput). */
    fun send(context: Context, intent: PendingIntent?, fillIn: android.content.Intent? = null): Boolean {
        if (intent == null) return false
        return runCatching {
            intent.send(context, 0, fillIn, null, null, null, options())
            true
        }.getOrDefault(false)
    }

    @Suppress("DEPRECATION")
    private fun options() = when {
        Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_ALWAYS)
            .toBundle()
        Build.VERSION.SDK_INT >= 34 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
        else -> null
    }
}
