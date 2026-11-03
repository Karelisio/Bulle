package com.karelisio.bulle.core.island

import android.content.Context
import androidx.annotation.StringRes
import com.karelisio.bulle.core.settings.BulleSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Environnement fourni aux modules pendant que l'île tourne. */
class ModuleEnv(
    val context: Context,
    val scope: CoroutineScope,
    val settings: StateFlow<BulleSettings>,
)

/**
 * Un module = une source de contenus pour l'île (musique, appels, batterie…).
 * Il est indépendant : il observe sa source et émet la liste de ses contenus actifs.
 * Pour ajouter un module : l'implémenter puis l'enregistrer dans `Modules.registerAll()`.
 */
interface IslandModule {
    val id: String

    @get:StringRes
    val titleRes: Int

    /** Activé par défaut si l'utilisateur n'a rien choisi. */
    val enabledByDefault: Boolean get() = true

    fun isEnabled(settings: BulleSettings): Boolean = settings.modules[id] ?: enabledByDefault

    /** Flux des contenus actifs. Collecté seulement quand le module est activé. */
    fun contents(env: ModuleEnv): Flow<List<IslandContent>>
}

/** Registre central des modules. */
object ModuleRegistry {
    private val _modules = linkedMapOf<String, IslandModule>()
    val modules: List<IslandModule> get() = _modules.values.toList()

    fun register(module: IslandModule) {
        _modules[module.id] = module
    }

    operator fun get(id: String): IslandModule? = _modules[id]
}
