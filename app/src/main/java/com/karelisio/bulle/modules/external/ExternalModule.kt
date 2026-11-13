package com.karelisio.bulle.modules.external

import android.content.Context
import android.os.SystemClock
import androidx.core.content.edit
import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import com.karelisio.bulle.core.settings.BulleJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/** Appli qui a déjà envoyé du contenu : dernière activité (heure murale) et autorisation. */
@Serializable
data class ExternalAppInfo(
    val lastSeen: Long = 0L,
    val blocked: Boolean = false,
    val received: Int = 0,
)

@Serializable
private data class ExternalAppsFile(val apps: Map<String, ExternalAppInfo> = emptyMap())

/**
 * Module « Applis connectées » : affiche dans l'île les contenus poussés par d'autres applis
 * via l'API d'intégration ([ExternalReceiver]). Activé par défaut ; rien ne s'affiche tant
 * qu'aucune appli autorisée n'envoie de contenu.
 *
 * La liste des applis (autorisées / bloquées) est conservée dans ses propres préférences,
 * hors de BulleSettings : elle n'a pas à suivre la sauvegarde des réglages.
 */
object ExternalModule : IslandModule {
    const val ID = "external"
    private const val PREFS = "external_apps"
    private const val KEY_APPS = "apps"

    /** Délai minimal entre deux écritures de « dernière activité » pour une même appli. */
    private const val PERSIST_THROTTLE_MS = 60_000L

    override val id = ID
    override val titleRes = R.string.external_module_title

    private val _board = MutableStateFlow(ExternalBoard())
    /** Contenus reçus (y compris les exemples locaux). */
    val board: StateFlow<ExternalBoard> = _board.asStateFlow()

    private val _apps = MutableStateFlow<Map<String, ExternalAppInfo>>(emptyMap())
    /** Applis connues (paquet → infos). */
    val apps: StateFlow<Map<String, ExternalAppInfo>> = _apps.asStateFlow()

    @Volatile
    private var loaded = false
    private val persistedAt = HashMap<String, Long>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var demoJob: Job? = null

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> {
        ensureLoaded(env.context)
        return combine(_board, _apps) { board, apps ->
            board.items
                .filter { it.local || apps[it.packageName]?.blocked != true }
                .map { ExternalContent(it) }
        }
    }

    /** Charge la liste des applis connues (une seule fois, lecture courte). */
    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val raw = prefs(context).getString(KEY_APPS, null)
            val file = raw?.let { runCatching { BulleJson.decodeFromString(ExternalAppsFile.serializer(), it) }.getOrNull() }
            _apps.value = file?.apps.orEmpty()
            loaded = true
        }
    }

    /**
     * Applique une commande reçue. Retourne true si elle a été prise en compte
     * (false : invalide ou appli bloquée).
     */
    fun handle(
        context: Context,
        command: ExternalCommand,
        nowWall: Long = System.currentTimeMillis(),
        nowElapsed: Long = SystemClock.elapsedRealtime(),
    ): Boolean {
        ensureLoaded(context)
        return when (command) {
            is ExternalCommand.Invalid -> false
            is ExternalCommand.Dismiss -> {
                _board.update { it.dismiss(command.key) }
                true
            }
            is ExternalCommand.Show -> {
                val pkg = command.spec.packageName
                val known = _apps.value[pkg]
                record(context, pkg, nowWall)
                if (known?.blocked == true) return false
                _board.update { it.put(command.spec, nowElapsed) }
                true
            }
        }
    }

    /** Pousse un contenu local (exemples, retours visuels de l'écosystème) sans passer par une appli. */
    fun pushLocal(spec: ExternalSpec, nowElapsed: Long = SystemClock.elapsedRealtime()) {
        val local = spec.copy(local = true, receivedAt = nowElapsed)
        _board.update { it.copy(maxPerApp = ExternalApi.MAX_TOTAL).put(local, nowElapsed).copy(maxPerApp = it.maxPerApp) }
    }

    fun dismiss(key: String) = _board.update { it.dismiss(key) }

    /** Autorise ou bloque une appli ; bloquer retire aussitôt ses contenus. */
    fun setBlocked(context: Context, packageName: String, blocked: Boolean) {
        ensureLoaded(context)
        _apps.update { apps -> apps + (packageName to (apps[packageName] ?: ExternalAppInfo()).copy(blocked = blocked)) }
        if (blocked) _board.update { it.removeApp(packageName) }
        persist(context)
    }

    /** Oublie une appli (elle réapparaîtra à son prochain envoi). */
    fun forget(context: Context, packageName: String) {
        ensureLoaded(context)
        _apps.update { it - packageName }
        _board.update { it.removeApp(packageName) }
        persist(context)
    }

    /** Démonstration : chaque modèle s'affiche tour à tour, étendu quelques secondes. */
    fun runDemo(context: Context) {
        val app = context.applicationContext
        demoJob?.cancel()
        demoJob = scope.launch {
            ExternalSamples.all(app, System.currentTimeMillis()).forEach { sample ->
                pushLocal(sample.copy(timeoutMs = ExternalSamples.DEMO_STEP_MS - 300, autoExpand = true))
                delay(ExternalSamples.DEMO_STEP_MS)
            }
        }
    }

    private fun record(context: Context, pkg: String, nowWall: Long) {
        val before = _apps.value[pkg]
        _apps.update { apps ->
            val info = apps[pkg] ?: ExternalAppInfo()
            apps + (pkg to info.copy(lastSeen = nowWall, received = (info.received + 1).coerceAtMost(1_000_000)))
        }
        val last = persistedAt[pkg]
        if (before == null || last == null || nowWall - last >= PERSIST_THROTTLE_MS) {
            persistedAt[pkg] = nowWall
            persist(context)
        }
    }

    private fun persist(context: Context) {
        val json = BulleJson.encodeToString(ExternalAppsFile.serializer(), ExternalAppsFile(_apps.value))
        prefs(context).edit { putString(KEY_APPS, json) }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Remise à zéro (tests). */
    fun resetForTests(context: Context? = null) {
        demoJob?.cancel()
        _board.value = ExternalBoard()
        _apps.value = emptyMap()
        persistedAt.clear()
        context?.let { prefs(it).edit(commit = true) { clear() } }
        loaded = context != null
    }
}
