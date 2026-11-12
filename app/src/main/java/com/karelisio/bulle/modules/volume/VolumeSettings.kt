package com.karelisio.bulle.modules.volume

import android.content.Context
import android.media.AudioManager
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
import kotlinx.serialization.Serializable
import java.io.File

/** Flux audio réglable depuis l'île. */
@Serializable
enum class VolumeStream(val streamType: Int) {
    MEDIA(AudioManager.STREAM_MUSIC),
    RING(AudioManager.STREAM_RING),
    NOTIFICATION(AudioManager.STREAM_NOTIFICATION),
    ALARM(AudioManager.STREAM_ALARM),
}

/** Flux réglé par les touches : automatique (média si lecture en cours, sinon sonnerie) ou fixé. */
@Serializable
enum class VolumeStreamChoice { AUTO, MEDIA, RING, NOTIFICATION, ALARM }

/** Réglages du volume sur l'île. Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class VolumeSettings(
    val stream: VolumeStreamChoice = VolumeStreamChoice.AUTO,
    /** Intercepter aussi les touches sur l'écran de verrouillage (sinon panneau système). */
    val interceptWhenLocked: Boolean = false,
    /** Durée d'affichage du curseur après le dernier appui (ms). */
    val timeoutMs: Long = 2_500L,
)

/**
 * Réglages du volume, gardés en mémoire (lus à chaque appui de touche, sur le fil principal)
 * et écrits dans un petit fichier privé du module.
 */
object VolumePrefs {
    const val FILE_NAME = "volume.json"

    private val _data = MutableStateFlow(VolumeSettings())
    val data: StateFlow<VolumeSettings> = _data.asStateFlow()

    @Volatile
    private var file: File? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun ensureLoaded(context: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            _data.value = read(f)
            file = f
        }
    }

    fun update(context: Context, transform: (VolumeSettings) -> VolumeSettings) {
        ensureLoaded(context)
        val updated = _data.updateAndGet(transform)
        val f = file ?: return
        io.launch { write(f, updated) }
    }

    fun read(f: File): VolumeSettings = runCatching {
        if (!f.exists()) VolumeSettings() else BulleJson.decodeFromString(VolumeSettings.serializer(), f.readText())
    }.getOrDefault(VolumeSettings())

    fun write(f: File, settings: VolumeSettings) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(BulleJson.encodeToString(VolumeSettings.serializer(), settings))
            if (!tmp.renameTo(f)) f.writeText(tmp.readText())
        }
    }

    fun resetForTests() {
        synchronized(this) {
            file = null
            _data.value = VolumeSettings()
        }
    }
}
