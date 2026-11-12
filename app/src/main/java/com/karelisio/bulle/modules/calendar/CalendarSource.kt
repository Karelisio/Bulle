package com.karelisio.bulle.modules.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** Lecture locale de l'agenda (CalendarContract), sans rien stocker ni envoyer. */
object CalendarSource {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private val projection = arrayOf(
        CalendarContract.Instances._ID,
        CalendarContract.Instances.EVENT_ID,
        CalendarContract.Instances.TITLE,
        CalendarContract.Instances.EVENT_LOCATION,
        CalendarContract.Instances.BEGIN,
        CalendarContract.Instances.END,
        CalendarContract.Instances.ALL_DAY,
        CalendarContract.Instances.DISPLAY_COLOR,
    )

    /**
     * Occurrences des agendas visibles qui chevauchent [from, to], hors « journée entière ».
     * Retourne une liste vide sans permission ou en cas d'erreur du fournisseur.
     */
    fun query(context: Context, from: Long, to: Long): List<CalendarEvent> {
        if (!hasPermission(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        return runCatching {
            context.contentResolver.query(
                uri,
                projection,
                "${CalendarContract.Instances.VISIBLE} = 1 AND ${CalendarContract.Instances.ALL_DAY} = 0",
                null,
                "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            CalendarEvent(
                                instanceId = c.getLong(0),
                                eventId = c.getLong(1),
                                title = c.getString(2)?.trim().orEmpty(),
                                location = c.getString(3)?.trim()?.takeIf { it.isNotEmpty() },
                                begin = c.getLong(4),
                                end = c.getLong(5),
                                allDay = c.getInt(6) != 0,
                                color = if (c.isNull(7)) 0 else c.getInt(7),
                            ),
                        )
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** Émet à chaque modification de l'agenda (ajout, édition, synchro). */
    fun changes(context: Context): Flow<Unit> = callbackFlow {
        trySend(Unit)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        val registered = runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }.isSuccess
        awaitClose { if (registered) context.contentResolver.unregisterContentObserver(observer) }
    }.conflate()
}
