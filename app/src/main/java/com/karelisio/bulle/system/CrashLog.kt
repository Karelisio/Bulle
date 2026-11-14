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
                IslandLog.log("Plantage précédent :\n" + f.readText())
            }
        }
    }

    /** Trace du dernier plantage, ou null. */
    fun last(context: Context): String? = runCatching {
        File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
    }.getOrNull()
}
