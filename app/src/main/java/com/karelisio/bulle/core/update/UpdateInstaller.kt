package com.karelisio.bulle.core.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Étapes du téléchargement puis de l'installation. */
sealed interface InstallState {
    data object Idle : InstallState
    data class Downloading(val bytes: Long, val total: Long) : InstallState {
        /** Progression 0..1, ou null si la taille est inconnue. */
        val fraction: Float? get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else null
    }
    data object Installing : InstallState
    data class Failed(val message: String) : InstallState
}

/** Téléchargement de l'APK dans cacheDir puis installation via PackageInstaller. */
object UpdateInstaller {

    private const val TAG = "UpdateInstaller"
    const val ACTION_STATUS = "com.karelisio.bulle.action.INSTALL_STATUS"

    private val _state = MutableStateFlow<InstallState>(InstallState.Idle)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    fun reset() {
        _state.value = InstallState.Idle
    }

    internal fun fail(message: String) {
        _state.value = InstallState.Failed(message)
    }

    /** L'utilisateur a-t-il autorisé Bulle à installer des applis ? */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Page système « Installer des applis inconnues » pour Bulle. */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Télécharge puis installe. À lancer dans une coroutine liée à l'écran. */
    suspend fun downloadAndInstall(context: Context, release: ReleaseInfo) {
        val app = context.applicationContext
        try {
            val apk = download(app, release)
            _state.value = InstallState.Installing
            install(app, apk)
        } catch (e: CancellationException) {
            // Annulation (bouton, fermeture de la feuille, rotation) : sans ce retour à Idle,
            // une émission tardive du thread IO laissait l'état bloqué sur « Téléchargement ».
            _state.value = InstallState.Idle
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Téléchargement impossible", e)
            fail(e.message ?: e.javaClass.simpleName)
        } catch (e: SecurityException) {
            Log.w(TAG, "Installation refusée", e)
            fail(e.message ?: e.javaClass.simpleName)
        } catch (e: Exception) {
            // URL malformée, session PackageInstaller invalide (IllegalStateException)… :
            // ne doit pas faire planter l'appli via rememberCoroutineScope.
            Log.w(TAG, "Mise à jour impossible", e)
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun download(context: Context, release: ReleaseInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        // Un seul APK en cache : on supprime les anciens.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "Bulle-v${release.version}.apk")
        _state.value = InstallState.Downloading(0, release.apkSize)
        val conn = URL(release.apkUrl).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Bulle")
            conn.setRequestProperty("Accept", "application/octet-stream")
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            var done = 0L
            var lastEmit = 0L
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        // Mises à jour de l'état limitées (~tous les 64 Ko).
                        if (done - lastEmit >= 64 * 1024 || done == total) {
                            lastEmit = done
                            _state.value = InstallState.Downloading(done, total)
                        }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("Téléchargement incomplet ($done/$total)")
            _state.value = InstallState.Downloading(done, total)
            target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /** Session PackageInstaller : écrit l'APK puis commit vers [InstallStatusReceiver]. */
    private suspend fun install(context: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setSize(apk.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("bulle.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val intent = Intent(context, InstallStatusReceiver::class.java).setAction(ACTION_STATUS)
                // MUTABLE obligatoire : le système ajoute le statut dans les extras.
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }
}

/** Reçoit le statut de la session d'installation. */
class InstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != UpdateInstaller.ACTION_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Confirmation système : on lance l'écran fourni par PackageInstaller.
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (e: Exception) {
                        UpdateInstaller.fail(e.message ?: e.javaClass.simpleName)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> UpdateInstaller.reset() // l'appli redémarre
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status=$status"
                if (status == PackageInstaller.STATUS_FAILURE_ABORTED) UpdateInstaller.reset() else UpdateInstaller.fail(msg)
            }
        }
    }
}
