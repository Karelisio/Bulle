package com.karelisio.bulle.modules.capture.memo

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un mémo enregistré (fichier .m4a du stockage privé). */
data class Memo(val file: File, val name: String, val modifiedAt: Long, val sizeBytes: Long)

/** Fichiers des mémos vocaux : stockage privé de l'appli (filesDir/memos), copie facultative dans Musique/Bulle. */
object MemoStore {
    private const val TAG = "MemoStore"
    const val DIR = "memos"
    const val EXT = ".m4a"

    /** Incrémenté à chaque modification : l'écran des mémos recharge sa liste. */
    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun dir(context: Context): File = File(context.filesDir, DIR).apply { mkdirs() }

    /** Nouveau fichier nommé d'après l'heure (« Mémo 2026-11-13 14.05.32.m4a »). */
    fun newFile(context: Context, prefix: String, now: Long = System.currentTimeMillis()): File =
        uniqueFile(dir(context), defaultName(prefix, now))

    fun defaultName(prefix: String, now: Long): String =
        prefix + " " + SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.ROOT).format(Date(now))

    /** Nom de fichier sûr : sans séparateurs ni caractères réservés, 1 à 80 caractères. */
    fun sanitizeName(input: String): String? {
        val cleaned = input.trim().replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim('.', ' ')
        return cleaned.take(80).takeIf { it.isNotEmpty() }
    }

    fun list(context: Context): List<Memo> =
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(EXT) }.orEmpty()
            .map { Memo(it, it.name.removeSuffix(EXT), it.lastModified(), it.length()) }
            .sortedByDescending { it.modifiedAt }

    fun rename(context: Context, memo: Memo, newName: String): Boolean {
        val safe = sanitizeName(newName) ?: return false
        if (safe == memo.name) return true
        val target = uniqueFile(dir(context), safe)
        val ok = memo.file.renameTo(target)
        if (ok) changed()
        return ok
    }

    fun delete(memo: Memo): Boolean = memo.file.delete().also { if (it) changed() }

    fun changed() {
        _version.value++
    }

    /** Durée en millisecondes (lecture des métadonnées, à appeler hors du fil principal). */
    fun durationMs(file: File): Long = runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)

    /** Copie le mémo dans Musique/Bulle (MediaStore, aucune permission requise sur Android 10+). */
    fun copyToMusic(context: Context, file: File): Boolean = runCatching {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Bulle")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: return@runCatching false
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        true
    }.onFailure { Log.w(TAG, "Copie dans Musique impossible", it) }.getOrDefault(false)

    private fun uniqueFile(dir: File, base: String): File {
        var f = File(dir, base + EXT)
        var i = 2
        while (f.exists()) f = File(dir, "$base ($i)$EXT").also { i++ }
        return f
    }
}
