package com.karelisio.bulle.system

import android.content.Context
import com.karelisio.bulle.island.IslandLog
import java.io.File

/**
 * Dernier plantage, gardé localement (jamais envoyé) : la trace est écrite dans un fichier privé
 * au moment du plantage, puis recopiée dans le Journal de l'île au démarrage suivant
 * (Réglages › Fiabilité › Journal, bouton Copier).
 */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = error.stackTraceToString().lineSequence().take(60).joinToString("\n")
                File(app.filesDir, FILE).writeText("${System.currentTimeMillis()} [${thread.name}]\n$trace")
            }
            previous?.uncaughtException(thread, error)
        }
        runCatching {
            val f = File(app.filesDir, FILE)
            if (f.exists()) {
                val text = f.readText()
                val whenMs = text.substringBefore(' ').toLongOrNull()
                val date = whenMs?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)) } ?: "?"
                IslandLog.log("Plantage précédent ($date, version ${com.karelisio.bulle.BuildConfig.VERSION_NAME} au démarrage) :\n$text")
                // Montré une seule fois : renommé pour ne pas réapparaître à chaque démarrage.
                f.renameTo(File(app.filesDir, "$FILE.seen"))
            }
        }
    }

    /** Trace du dernier plantage, ou null. */
    fun last(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()
}
