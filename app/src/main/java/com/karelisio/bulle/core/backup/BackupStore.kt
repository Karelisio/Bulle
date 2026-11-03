package com.karelisio.bulle.core.backup

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.bulle.BuildConfig
import com.karelisio.bulle.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Une sauvegarde automatique présente dans filesDir/backups. */
data class AutoBackup(val file: File, val createdAt: Long)

/** Sauvegardes automatiques dans le stockage privé + export/import via SAF. */
object BackupStore {

    const val KEEP = 7
    private const val PREFIX = "auto-"
    private const val SUFFIX = ".json"

    fun dir(context: Context): File = File(context.filesDir, "backups")

    /** Liste triée de la plus récente à la plus ancienne. */
    fun list(dir: File): List<AutoBackup> =
        dir.listFiles().orEmpty()
            .mapNotNull { f ->
                val ts = f.name.removePrefix(PREFIX).removeSuffix(SUFFIX).toLongOrNull()
                if (f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) && ts != null) AutoBackup(f, ts) else null
            }
            .sortedByDescending { it.createdAt }

    fun list(context: Context): List<AutoBackup> = list(dir(context))

    /** Écrit une sauvegarde puis ne garde que les [keep] plus récentes. */
    fun writeAuto(dir: File, content: String, now: Long, keep: Int = KEEP): File {
        dir.mkdirs()
        val file = File(dir, "$PREFIX$now$SUFFIX")
        val tmp = File(dir, "${file.name}.tmp")
        tmp.writeText(content)
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Écriture impossible : ${file.name}")
        }
        rotate(dir, keep)
        return file
    }

    /** Supprime les sauvegardes au-delà des [keep] plus récentes. */
    fun rotate(dir: File, keep: Int = KEEP) {
        list(dir).drop(keep).forEach { it.file.delete() }
    }

    /** Sauvegarde automatique immédiate des réglages courants. */
    suspend fun backupNow(context: Context, now: Long = System.currentTimeMillis()): File = withContext(Dispatchers.IO) {
        val settings = SettingsRepository.current(context)
        val file = writeAuto(dir(context), BackupCodec.encode(settings, BuildConfig.VERSION_NAME, now), now)
        SettingsRepository.update(context) { it.copy(backup = it.backup.copy(lastAutoBackup = now)) }
        file
    }

    /** Export SAF vers [uri]. */
    suspend fun exportTo(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val text = BackupCodec.encode(SettingsRepository.current(context), BuildConfig.VERSION_NAME, System.currentTimeMillis())
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: throw IOException("openOutputStream")
        out.use { it.write(text.encodeToByteArray()) }
    }

    /** Lecture (sans application) d'un fichier choisi via SAF. */
    suspend fun readFrom(context: Context, uri: Uri): DecodeResult = withContext(Dispatchers.IO) {
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("openInputStream")
        // Garde-fou : une sauvegarde fait quelques Ko.
        val bytes = input.use { it.readNBytesCompat(4 * 1024 * 1024) }
        BackupCodec.decode(bytes.decodeToString())
    }

    suspend fun readFrom(file: File): DecodeResult = withContext(Dispatchers.IO) {
        BackupCodec.decode(file.readText())
    }

    /** Applique une sauvegarde validée (écrase les réglages). */
    suspend fun apply(context: Context, envelope: BackupEnvelope) {
        SettingsRepository.update(context) { BackupCodec.merge(it, envelope.settings) }
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > limit) throw IOException("Fichier trop volumineux")
        }
        return out.toByteArray()
    }
}

/** Sauvegarde quotidienne, si activée dans les réglages. */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!SettingsRepository.current(applicationContext).backup.autoBackup) return Result.success()
        return try {
            BackupStore.backupNow(applicationContext)
            Result.success()
        } catch (e: IOException) {
            Result.retry()
        }
    }
}

/** Programmation de la sauvegarde automatique. */
object BackupScheduler {
    private const val WORK = "bulle-auto-backup"

    /** À appeler au démarrage : travail périodique quotidien, unique (KEEP). */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
