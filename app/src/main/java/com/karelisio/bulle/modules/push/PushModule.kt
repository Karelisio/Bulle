package com.karelisio.bulle.modules.push

import com.karelisio.bulle.R
import com.karelisio.bulle.core.island.IslandContent
import com.karelisio.bulle.core.island.IslandModule
import com.karelisio.bulle.core.island.ModuleEnv
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Module générique de contenus « poussés » depuis le processus de Bulle
 * (test de l'île, et plus tard l'API publique d'intégration).
 */
object PushModule : IslandModule {
    override val id = "push"
    override val titleRes = R.string.module_push

    private val items = MutableStateFlow<List<IslandContent>>(emptyList())

    fun push(content: IslandContent) = items.update { list ->
        val now = android.os.SystemClock.elapsedRealtime()
        list.filter { it.key != content.key && (it.expiresAt ?: Long.MAX_VALUE) > now } + content
    }

    fun remove(key: String) = items.update { list -> list.filter { it.key != key } }

    override fun contents(env: ModuleEnv): Flow<List<IslandContent>> = items
}
