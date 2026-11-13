package com.karelisio.bulle.modules.capture

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
import kotlinx.serialization.Serializable
import java.io.File

/** Qualité de l'enregistrement d'écran (petite dimension maximale, 0 = native). */
@Serializable
enum class VideoQuality(val maxShortSide: Int) { NATIVE(0), HD(720), SD(480) }

/** Qualité des mémos vocaux (AAC mono). */
@Serializable
enum class MemoQuality(val sampleRate: Int, val bitRate: Int) { STANDARD(44_100, 64_000), HIGH(48_000, 128_000) }

/** Réglages de la capture (QR, mémos, écran). Toujours des valeurs par défaut (compatibilité JSON). */
@Serializable
data class CaptureSettings(
    /** Résolution de l'enregistrement d'écran. */
    val videoQuality: VideoQuality = VideoQuality.NATIVE,
    /** Enregistrer aussi le micro pendant l'enregistrement d'écran (désactivé par défaut). */
    val screenMicAudio: Boolean = false,
    /** Qualité des mémos vocaux. */
    val memoQuality: MemoQuality = MemoQuality.STANDARD,
    /** Copier aussi chaque mémo dans Musique/Bulle (visible des autres applis). */
    val memoToMusic: Boolean = false,
    /** Vibration à la détection d'un code QR (si le retour haptique de l'île est aussi actif). */
    val qrHaptics: Boolean = true,
)

/**
 * Réglages gardés en mémoire et écrits dans un petit fichier privé (même principe que les
 * réglages des messages) : le module reste autonome, sans champ dans BulleSettings.
 */
object CapturePrefs {
    const val FILE_NAME = "capture.json"

    private val _data = MutableStateFlow(CaptureSettings())
    val data: StateFlow<CaptureSettings> = _data.asStateFlow()

    @Volatile
    private var file: File? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun loaded(context: Context): StateFlow<CaptureSettings> {
        ensureLoaded(context)
        return data
    }

    fun current(context: Context): CaptureSettings = loaded(context).value

    private fun ensureLoaded(context: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            _data.value = read(f)
            file = f
        }
    }

    fun update(context: Context, transform: (CaptureSettings) -> CaptureSettings) {
        ensureLoaded(context)
        val updated = _data.updateAndGet(transform)
        val f = file ?: return
        io.launch { write(f, updated) }
    }

    fun read(f: File): CaptureSettings = runCatching {
        if (!f.exists()) CaptureSettings() else BulleJson.decodeFromString(CaptureSettings.serializer(), f.readText())
    }.getOrDefault(CaptureSettings())

    fun write(f: File, settings: CaptureSettings) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(BulleJson.encodeToString(CaptureSettings.serializer(), settings))
            if (!tmp.renameTo(f)) f.writeText(tmp.readText())
        }
    }

    fun resetForTests() {
        synchronized(this) {
            file = null
            _data.value = CaptureSettings()
        }
    }
}
