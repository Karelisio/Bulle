package com.karelisio.bulle.core.update

import android.content.Context
import android.util.Log
import com.karelisio.bulle.BuildConfig
import com.karelisio.bulle.core.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Une release GitHub publiée par la CI. */
data class ReleaseInfo(
    /** Version sans le « v » (ex. « 1.2.3 »). */
    val version: String,
    val tag: String,
    /** Corps de la release : changelog en markdown. */
    val changelog: String,
    val apkUrl: String,
    val apkSize: Long,
    val htmlUrl: String,
)

/** Résultat d'une vérification. */
sealed interface UpdateResult {
    data object UpToDate : UpdateResult
    data class Available(val release: ReleaseInfo) : UpdateResult
    data class Error(val message: String) : UpdateResult
}

/** Vérifie la dernière release GitHub (au plus toutes les 6 h hors vérification manuelle). */
object UpdateChecker {

    private const val TAG = "UpdateChecker"
    const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val TIMEOUT_MS = 15_000

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<ReleaseInfo?>(null)

    /** Release proposée à l'utilisateur (affichée par [UpdateHost]). */
    val available: StateFlow<ReleaseInfo?> = _available.asStateFlow()

    /** Ferme la feuille de mise à jour (« Plus tard »). */
    fun dismiss() {
        _available.value = null
    }

    /** Ignore définitivement cette version. */
    suspend fun skip(context: Context, release: ReleaseInfo) {
        SettingsRepository.update(context) { it.copy(update = it.update.copy(skippedVersion = release.version)) }
        dismiss()
    }

    /** Le délai depuis la dernière vérification est-il écoulé ? (pure) */
    fun isDue(lastCheck: Long, now: Long): Boolean =
        now - lastCheck >= CHECK_INTERVAL_MS || now < lastCheck

    /**
     * Vérification automatique : respecte le délai de 6 h et la version ignorée.
     * Renvoie null si aucune vérification n'a eu lieu.
     */
    suspend fun checkIfDue(context: Context, now: Long = System.currentTimeMillis()): UpdateResult? {
        val settings = SettingsRepository.current(context).update
        if (!isDue(settings.lastCheck, now)) return null
        val result = check(context, now)
        if (result is UpdateResult.Available) {
            if (result.release.version == settings.skippedVersion) return UpdateResult.UpToDate
            _available.value = result.release
        }
        return result
    }

    /** Vérification manuelle (bouton « Rechercher une mise à jour ») : ignore le délai et la version ignorée. */
    suspend fun checkNow(context: Context): UpdateResult {
        val result = check(context, System.currentTimeMillis())
        if (result is UpdateResult.Available) _available.value = result.release
        return result
    }

    private suspend fun check(context: Context, now: Long): UpdateResult {
        val result = try {
            val release = fetchLatest()
            if (release != null && SemVer.isNewer(release.version, BuildConfig.VERSION_NAME)) {
                UpdateResult.Available(release)
            } else {
                UpdateResult.UpToDate
            }
        } catch (e: IOException) {
            Log.w(TAG, "Vérification impossible", e)
            UpdateResult.Error(e.message ?: e.javaClass.simpleName)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Réponse GitHub illisible", e)
            UpdateResult.Error(e.message ?: e.javaClass.simpleName)
        }
        if (result !is UpdateResult.Error) {
            SettingsRepository.update(context) { it.copy(update = it.update.copy(lastCheck = now)) }
        }
        return result
    }

    /** GET releases/latest. */
    suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "Bulle")
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> parseRelease(conn.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null // aucune release publiée
                else -> throw IOException("HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Analyse la réponse de l'API GitHub (pure). Renvoie null si la release
     * n'a pas d'APK ou si son tag n'est pas une version.
     */
    fun parseRelease(body: String): ReleaseInfo? {
        val root: JsonObject = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("JSON invalide", e)
        }
        val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull ?: return null
        if (SemVer.parse(tag) == null) return null
        if (root["draft"]?.jsonPrimitive?.contentOrNull == "true") return null
        val assets = root["assets"]?.jsonArray.orEmpty().map { it.jsonObject }
        val apk = assets.firstOrNull { a ->
            a["name"]?.jsonPrimitive?.contentOrNull.orEmpty().endsWith(".apk", ignoreCase = true)
        } ?: return null
        val apkUrl = apk["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return null
        return ReleaseInfo(
            version = SemVer.clean(tag),
            tag = tag,
            changelog = root["body"]?.jsonPrimitive?.contentOrNull.orEmpty().replace("\r\n", "\n").trim(),
            apkUrl = apkUrl,
            apkSize = apk["size"]?.jsonPrimitive?.longOrNull ?: 0L,
            htmlUrl = root["html_url"]?.jsonPrimitive?.contentOrNull
                ?: "https://github.com/${BuildConfig.GITHUB_REPO}/releases",
        )
    }
}
