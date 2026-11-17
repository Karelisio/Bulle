package com.karelisio.bulle.system

import android.content.Context
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import com.karelisio.bulle.core.settings.BulleJson
import com.karelisio.bulle.island.AddonEnv
import com.karelisio.bulle.island.IslandAddon
import com.karelisio.bulle.modules.battery.BatteryReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Une mesure : mémoire de Bulle, temps processeur cumulé, batterie du téléphone. */
@Serializable
data class PerfSample(
    /** Heure (epoch ms). */
    val at: Long,
    /** Mémoire utilisée par Bulle (PSS, Ko). */
    val pssKb: Long,
    /** Temps processeur cumulé du processus (ms) — repart de 0 à chaque redémarrage. */
    val cpuMs: Long,
    /** Démarrage du processus (epoch ms) : distingue deux vies du processus. */
    val processStart: Long,
    val battery: Int,
    val charging: Boolean,
)

/** Bilan des dernières 24 h (logique pure, testée). */
data class PerfSummary(
    val samples: Int,
    val hours: Float,
    val memAvgMb: Float,
    val memMaxMb: Float,
    /** Temps processeur consommé par Bulle sur la période (minutes). */
    val cpuMinutes: Float,
    /** Baisse moyenne de la batterie du téléphone hors charge (%/h), pour comparer. */
    val drainPerHour: Float?,
    /** Mémoire de chaque mesure (Mo), pour la courbe. */
    val memSeries: List<Float>,
) {
    companion object {
        fun of(all: List<PerfSample>, now: Long, windowMs: Long = DAY_MS): PerfSummary? {
            val s = all.filter { now - it.at in 0..windowMs }.sortedBy { it.at }
            if (s.size < 2) return null
            val mem = s.map { it.pssKb / 1024f }
            var cpu = 0L
            var drop = 0
            var dischargingMs = 0L
            for (i in 1 until s.size) {
                val a = s[i - 1]
                val b = s[i]
                if (a.processStart == b.processStart && b.cpuMs >= a.cpuMs) cpu += b.cpuMs - a.cpuMs
                if (!a.charging && !b.charging && b.battery <= a.battery) {
                    drop += a.battery - b.battery
                    dischargingMs += b.at - a.at
                }
            }
            return PerfSummary(
                samples = s.size,
                hours = (s.last().at - s.first().at) / 3_600_000f,
                memAvgMb = mem.average().toFloat(),
                memMaxMb = mem.max(),
                cpuMinutes = cpu / 60_000f,
                drainPerHour = if (dischargingMs >= 30 * 60_000L) drop / (dischargingMs / 3_600_000f) else null,
                memSeries = mem,
            )
        }

        const val DAY_MS = 24 * 3_600_000L
    }
}

/**
 * Mesures de performance de Bulle sur 24 h (Fiabilité) : une mesure toutes les 15 min dans le
 * service de l'île (mémoire, temps processeur, batterie), gardée dans un fichier privé.
 */
object PerfMonitor : IslandAddon {
    override val id = "perf"
    const val INTERVAL_MS = 15 * 60_000L
    private const val FILE = "perf.json"
    private const val KEEP = 120
    private val serializer = ListSerializer(PerfSample.serializer())

    private val _samples = MutableStateFlow<List<PerfSample>>(emptyList())
    val samples: StateFlow<List<PerfSample>> = _samples.asStateFlow()

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE)

    suspend fun load(context: Context): List<PerfSample> = withContext(Dispatchers.IO) {
        runCatching { BulleJson.decodeFromString(serializer, file(context).readText()) }.getOrDefault(emptyList())
            .also { _samples.value = it }
    }

    fun sample(context: Context, now: Long = System.currentTimeMillis()): PerfSample {
        val b = BatteryReader.current(context)
        val start = now - (SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())
        return PerfSample(now, Debug.getPss(), Process.getElapsedCpuTime(), start / 60_000L * 60_000L, b?.level ?: -1, b?.plugged == true)
    }

    override fun start(env: AddonEnv): AutoCloseable {
        val job = env.scope.launch {
            val ctx = env.service
            var list = load(ctx)
            while (true) {
                list = (list + withContext(Dispatchers.Default) { sample(ctx) }).takeLast(KEEP)
                _samples.value = list
                withContext(Dispatchers.IO) { runCatching { file(ctx).writeText(BulleJson.encodeToString(serializer, list)) } }
                delay(INTERVAL_MS)
            }
        }
        return AutoCloseable { job.cancel() }
    }
}
