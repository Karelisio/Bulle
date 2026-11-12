package com.karelisio.bulle.island

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Journal de diagnostic de l'île, en mémoire uniquement (rien n'est écrit ni envoyé) :
 * apparitions / masquages et leur raison, mises à jour de la fenêtre, plein écran.
 * Sert à comprendre un scintillement sur un appareil précis (écran Fiabilité, ou `adb logcat -s BulleIsland`).
 */
object IslandLog {
    data class Entry(val wallTime: Long, val uptime: Long, val message: String) {
        fun format(): String = TIME.get()!!.format(Date(wallTime)) + "  " + message
    }

    private const val MAX = 300
    private const val TAG = "BulleIsland"
    private val TIME = ThreadLocal.withInitial { SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT) }
    private val buffer = ArrayDeque<Entry>(MAX)
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    @Synchronized
    fun log(message: String) {
        val e = Entry(System.currentTimeMillis(), SystemClock.uptimeMillis(), message)
        if (buffer.size == MAX) buffer.removeFirst()
        buffer.addLast(e)
        _entries.value = buffer.toList()
        Log.d(TAG, message)
    }

    @Synchronized
    fun clear() {
        buffer.clear()
        _entries.value = emptyList()
    }

    /** Texte complet, pour copier-coller. */
    fun dump(): String = entries.value.joinToString("\n") { it.format() }
}
