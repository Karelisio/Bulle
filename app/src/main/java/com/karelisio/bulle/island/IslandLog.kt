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
        scheduleSave()
    }

    // --- Conservation des dernières lignes après un redémarrage (fichier privé de l'appli) ---

    /** Nombre de lignes gardées sur le téléphone. */
    const val PERSISTED = 50
    private const val FILE = "island_log.txt"
    /** Préfixe des lignes de la session précédente. */
    const val PREVIOUS = "↩ "

    @Volatile
    private var file: java.io.File? = null
    private val writer = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "IslandLog").apply { isDaemon = true } }
    private val savePending = java.util.concurrent.atomic.AtomicBoolean(false)

    /** Relie le journal à son fichier : les lignes de la session précédente sont recopiées en tête. */
    fun attach(context: android.content.Context) {
        if (file != null) return
        val f = java.io.File(context.applicationContext.filesDir, FILE)
        file = f
        val previous = runCatching { if (f.exists()) parse(f.readText()) else emptyList() }.getOrDefault(emptyList())
        if (previous.isEmpty()) return
        synchronized(this) {
            val current = buffer.toList()
            buffer.clear()
            (previous + current).takeLast(MAX).forEach(buffer::addLast)
            _entries.value = buffer.toList()
        }
    }

    private fun scheduleSave() {
        if (file == null || !savePending.compareAndSet(false, true)) return
        writer.schedule({
            savePending.set(false)
            val f = file ?: return@schedule
            val lines = synchronized(this) { buffer.toList() }
            runCatching { f.writeText(serialize(lines)) }
        }, 2, java.util.concurrent.TimeUnit.SECONDS)
    }

    /** Dernières lignes sous forme de texte (heure, tabulation, message ; une ligne par entrée). */
    fun serialize(entries: List<Entry>): String =
        entries.filterNot { it.message.startsWith(PREVIOUS) }.takeLast(PERSISTED)
            .joinToString("\n") { "${it.wallTime}\t" + it.message.replace('\n', ' ').replace('\t', ' ') }

    /** Relecture : lignes marquées comme venant de la session précédente ; lignes invalides ignorées. */
    fun parse(text: String): List<Entry> = text.lineSequence().mapNotNull { line ->
        val tab = line.indexOf('\t')
        if (tab <= 0) return@mapNotNull null
        val time = line.substring(0, tab).toLongOrNull() ?: return@mapNotNull null
        Entry(time, 0L, PREVIOUS + line.substring(tab + 1))
    }.toList().takeLast(PERSISTED)

    @Synchronized
    fun clear() {
        file?.let { f -> writer.execute { runCatching { f.delete() } } }
        buffer.clear()
        _entries.value = emptyList()
    }

    /** Texte complet, pour copier-coller. */
    fun dump(): String = entries.value.joinToString("\n") { it.format() }

    /** Catégorie d'une ligne (filtres de l'écran Fiabilité). */
    enum class Category { ISLAND, VOLET, AUDIO, SHIZUKU }

    /** Catégorie déduite du début du message (logique pure, testée). */
    fun category(message: String): Category {
        val m = message.removePrefix(PREVIOUS)
        return when {
            m.startsWith("Sortie audio") || m.startsWith("Volume :") -> Category.AUDIO
            m.startsWith("Shizuku") || m.startsWith("Tuile") -> Category.SHIZUKU
            VOLET_PREFIXES.any { m.startsWith(it) } -> Category.VOLET
            else -> Category.ISLAND
        }
    }

    private val VOLET_PREFIXES = listOf(
        "Volet", "Effacement", "Notification retirée", "Refus du système", "Masquée dans Bulle",
    )

    /** Lignes de la catégorie [category] (toutes si null). */
    fun filter(entries: List<Entry>, category: Category?): List<Entry> =
        if (category == null) entries else entries.filter { category(it.message) == category }
}
