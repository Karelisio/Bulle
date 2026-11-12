package com.karelisio.bulle.modules.focus

import com.karelisio.bulle.core.settings.BulleJson
import kotlinx.serialization.Serializable
import java.io.File

/** Contenu persistant du module : réglages et minuteur en cours (null = aucun). */
@Serializable
data class FocusData(
    val settings: FocusSettings = FocusSettings(),
    val state: FocusState? = null,
)

/**
 * Petit fichier JSON privé du module (quelques centaines d'octets).
 * Écriture atomique (fichier temporaire puis renommage) : jamais de fichier à moitié écrit.
 */
class FocusStore(private val file: File) {

    fun load(): FocusData = runCatching {
        if (!file.exists()) return FocusData()
        BulleJson.decodeFromString(FocusData.serializer(), file.readText())
    }.getOrDefault(FocusData())

    fun save(data: FocusData) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(BulleJson.encodeToString(FocusData.serializer(), data))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    companion object {
        const val FILE_NAME = "focus.json"
    }
}
