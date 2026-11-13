package com.karelisio.bulle.modules.focusmode

import android.content.Context
import com.karelisio.bulle.core.island.Priority
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

/** Comment un module est traité pendant le mode focus. */
@Serializable
enum class FocusModuleMode {
    /** Selon la priorité du contenu et le seuil. */
    AUTO,

    /** Toujours montré (essentiel). */
    ALWAYS,

    /** Toujours retenu pendant le focus. */
    NEVER,
}

/**
 * Créneau planifié : jours ISO (1 = lundi … 7 = dimanche) et heures [start, end[ en minutes.
 * Un créneau qui passe minuit appartient au jour de son début (lundi 22 h – 7 h couvre mardi 3 h).
 * start == end : toute la journée.
 */
@Serializable
data class FocusSlot(
    val days: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7),
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    val enabled: Boolean = true,
)

/**
 * Réglages du mode focus. Toujours des valeurs par défaut (compatibilité JSON) ; par défaut aucun
 * créneau ni appli : le focus ne s'active que si l'utilisateur le demande.
 */
@Serializable
data class FocusModeSettings(
    /** Priorité minimale d'un contenu « essentiel » (par défaut : alarmes et appels). */
    val minPriority: Int = Priority.ALARM.value,
    /** Exceptions par module (id → mode) ; absent = AUTO. */
    val modules: Map<String, FocusModuleMode> = DEFAULT_MODULES,
    val slots: List<FocusSlot> = emptyList(),
    /** Applis qui déclenchent le focus tant qu'elles sont au premier plan. */
    val apps: Set<String> = emptySet(),
    /** Petite lune dans l'île pendant le focus. */
    val showIndicator: Boolean = true,
) {
    fun modeOf(moduleId: String): FocusModuleMode = modules[moduleId] ?: FocusModuleMode.AUTO

    companion object {
        /** Minuteur / Pomodoro, indicateur de volume et appels : essentiels par défaut. */
        val DEFAULT_MODULES = mapOf(
            "focus" to FocusModuleMode.ALWAYS,
            "volume" to FocusModuleMode.ALWAYS,
            "call" to FocusModuleMode.ALWAYS,
        )

        /** Seuils proposés (valeurs de [Priority]), du plus strict au plus large. */
        val THRESHOLDS = listOf(Priority.CALL.value, Priority.ALARM.value, Priority.ALERT.value, Priority.LIVE.value)
    }
}

/**
 * Réglages du mode focus, gardés en mémoire et écrits dans un petit fichier privé
 * (même principe que les réglages des messages) : le mode focus reste autonome.
 */
object FocusModePrefs {
    const val FILE_NAME = "focusmode.json"

    private val _data = MutableStateFlow(FocusModeSettings())
    val data: StateFlow<FocusModeSettings> = _data.asStateFlow()

    @Volatile
    private var file: File? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun loaded(context: Context): StateFlow<FocusModeSettings> {
        ensureLoaded(context)
        return data
    }

    private fun ensureLoaded(context: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            _data.value = read(f)
            file = f
        }
    }

    fun update(context: Context, transform: (FocusModeSettings) -> FocusModeSettings) {
        ensureLoaded(context)
        val updated = _data.updateAndGet(transform)
        val f = file ?: return
        io.launch { write(f, updated) }
    }

    fun read(f: File): FocusModeSettings = runCatching {
        if (!f.exists()) FocusModeSettings() else BulleJson.decodeFromString(FocusModeSettings.serializer(), f.readText())
    }.getOrDefault(FocusModeSettings())

    fun write(f: File, settings: FocusModeSettings) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(BulleJson.encodeToString(FocusModeSettings.serializer(), settings))
            if (!tmp.renameTo(f)) f.writeText(tmp.readText())
        }
    }

    fun resetForTests() {
        synchronized(this) {
            file = null
            _data.value = FocusModeSettings()
        }
    }
}
