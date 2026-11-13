package com.karelisio.bulle.modules.messages

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

/**
 * Réglages du module « Messages ». Toujours des valeurs par défaut (compatibilité JSON).
 * L'activation du module passe, comme pour les autres, par `BulleSettings.modules["messages"]`.
 */
@Serializable
data class MessagesSettings(
    /** Durée d'affichage d'un nouveau message (ms). */
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    /** Ouvrir l'île toute seule à l'arrivée d'un message (désactivé par défaut). */
    val autoExpand: Boolean = false,
    /** Applis dont les messages ne s'affichent jamais dans l'île. */
    val excludedApps: Set<String> = emptySet(),
    /** Afficher le texte des messages (sinon « Nouveau message » seulement). */
    val showContent: Boolean = true,
) {
    companion object {
        const val DEFAULT_TIMEOUT_MS = 6_000L
        const val MIN_TIMEOUT_MS = 3_000L
        const val MAX_TIMEOUT_MS = 15_000L
    }
}

/**
 * Réglages du module, gardés en mémoire et écrits dans un petit fichier privé
 * (même principe que les réglages du volume) : le module reste autonome.
 */
object MessagesPrefs {
    const val FILE_NAME = "messages.json"

    private val _data = MutableStateFlow(MessagesSettings())
    val data: StateFlow<MessagesSettings> = _data.asStateFlow()

    @Volatile
    private var file: File? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    /** Réglages, chargés depuis le fichier au premier appel. */
    fun loaded(context: Context): StateFlow<MessagesSettings> {
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

    fun update(context: Context, transform: (MessagesSettings) -> MessagesSettings) {
        ensureLoaded(context)
        val updated = _data.updateAndGet(transform)
        val f = file ?: return
        io.launch { write(f, updated) }
    }

    fun read(f: File): MessagesSettings = runCatching {
        if (!f.exists()) MessagesSettings() else BulleJson.decodeFromString(MessagesSettings.serializer(), f.readText())
    }.getOrDefault(MessagesSettings())

    fun write(f: File, settings: MessagesSettings) {
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(BulleJson.encodeToString(MessagesSettings.serializer(), settings))
            if (!tmp.renameTo(f)) f.writeText(tmp.readText())
        }
    }

    fun resetForTests() {
        synchronized(this) {
            file = null
            _data.value = MessagesSettings()
        }
    }
}
