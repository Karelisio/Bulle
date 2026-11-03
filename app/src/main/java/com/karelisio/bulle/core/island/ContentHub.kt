package com.karelisio.bulle.core.island

import android.os.SystemClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update

/**
 * Agrège les contenus de tous les modules activés, retire les contenus expirés
 * et ceux que l'utilisateur a balayés.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContentHub(env: ModuleEnv, registry: ModuleRegistry = ModuleRegistry) {

    private val dismissed = MutableStateFlow<Set<String>>(emptySet())

    private val raw = env.settings
        .map { s -> registry.modules.filter { it.isEnabled(s) } }
        .distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }
        .flatMapLatest { modules ->
            if (modules.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    modules.map { m ->
                        m.contents(env)
                            .onStart { emit(emptyList()) }
                            .catch { emit(emptyList()) }
                    },
                ) { lists -> lists.flatMap { it } }
            }
        }
        // Un contenu balayé qui disparaît de sa source peut revenir plus tard.
        .onEach { list -> val keys = list.mapTo(HashSet()) { it.key }; dismissed.update { it intersect keys } }

    val contents: StateFlow<List<IslandContent>> = combine(raw, dismissed) { list, d -> list.filter { it.key !in d } }
        .transformLatest { list ->
            var alive = list
            while (true) {
                val now = SystemClock.elapsedRealtime()
                alive = alive.filter { (it.expiresAt ?: Long.MAX_VALUE) > now }
                emit(alive)
                val next = alive.mapNotNull { it.expiresAt }.minOrNull() ?: break
                delay((next - now).coerceAtLeast(1))
            }
        }
        .distinctUntilChanged()
        .stateIn(env.scope, SharingStarted.Eagerly, emptyList())

    fun dismiss(key: String) = dismissed.update { it + key }
}
