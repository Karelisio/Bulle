package com.karelisio.bulle.modules.countdown

import android.content.Context
import com.karelisio.bulle.core.settings.BulleJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import java.io.File

/**
 * Petit magasin de réglages privé d'un module : valeur gardée en mémoire (StateFlow) et écrite
 * dans un fichier JSON de l'appli (écriture atomique, sur un fil d'E/S unique). Même principe que
 * les réglages du module Messages : le module reste autonome, sans champ dans BulleSettings.
 */
class PrivateJsonStore<T>(
    private val fileName: String,
    private val serializer: KSerializer<T>,
    private val default: () -> T,
) {
    private val _data = MutableStateFlow(default())
    val data: StateFlow<T> = _data.asStateFlow()

    @Volatile
    private var file: File? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Valeur, chargée depuis le fichier au premier appel. */
    fun loaded(context: Context): StateFlow<T> {
        ensureLoaded(context)
        return data
    }

    private fun ensureLoaded(context: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, fileName)
            _data.value = read(f)
            file = f
        }
    }

    fun update(context: Context, transform: (T) -> T): T {
        ensureLoaded(context)
        val updated = _data.updateAndGet(transform)
        val f = file ?: return updated
        io.launch { write(f, updated) }
        return updated
    }

    private fun read(f: File): T = runCatching {
        if (!f.exists()) default() else BulleJson.decodeFromString(serializer, f.readText())
    }.getOrElse { default() }

    private fun write(f: File, value: T) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(BulleJson.encodeToString(serializer, value))
            if (!tmp.renameTo(f)) f.writeText(tmp.readText())
        }
    }

    fun resetForTests() {
        synchronized(this) {
            file = null
            _data.value = default()
        }
    }
}
