package com.karelisio.bulle.modules.calendar

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.net.toUri

/** Ouvertures depuis l'île (contexte du service : nouvelle tâche obligatoire). Jamais de plantage. */
object CalendarIntents {

    /** URI « geo » d'un lieu en texte libre (encodé). */
    fun directionsUri(location: String): Uri = ("geo:0,0?q=" + Uri.encode(location)).toUri()

    fun eventUri(eventId: Long): Uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)

    fun openDirections(context: Context, location: String): Boolean = start(context, Intent(Intent.ACTION_VIEW, directionsUri(location)))

    fun openEvent(context: Context, eventId: Long): Boolean = start(context, Intent(Intent.ACTION_VIEW, eventUri(eventId)))

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
