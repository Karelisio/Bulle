package com.karelisio.bulle.notifications

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Notifications masquées côté Bulle (gardées sur le téléphone, survivent au redémarrage) : celles que le système refuse d'effacer
 * (service de premier plan, « en cours »…) disparaissent du Volet sans être annulées.
 * Une clé est oubliée dès que la notification disparaît vraiment.
 */
object NotifLocalHide {
    private val _keys = MutableStateFlow<Set<String>>(emptySet())
    val keys: StateFlow<Set<String>> = _keys.asStateFlow()

    private const val PREFS = "notif_local_hide"
    private const val KEY = "keys"

    @Volatile
    private var prefs: SharedPreferences? = null

    /** Charge les clés enregistrées (une fois) ; chaque changement est ensuite enregistré. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        val saved = p.getStringSet(KEY, null).orEmpty()
        if (saved.isNotEmpty()) _keys.update { it + saved }
    }

    private fun save() {
        prefs?.edit()?.putStringSet(KEY, HashSet(_keys.value))?.apply()
    }

    fun hide(keys: Collection<String>) {
        if (keys.isNotEmpty()) { _keys.update { it + keys }; save() }
    }

    fun show(keys: Collection<String>) {
        if (keys.isNotEmpty()) { _keys.update { it - keys.toSet() }; save() }
    }

    /** La notification [key] a disparu : inutile de la masquer encore. */
    fun forget(key: String) {
        if (key in _keys.value) { _keys.update { it - key }; save() }
    }

    /** Ne garde que les clés encore actives (reconnexion du listener). */
    fun retain(active: Set<String>) {
        val before = _keys.value
        _keys.update { pruned(it, active) }
        if (_keys.value != before) save()
    }

    fun pruned(hidden: Set<String>, active: Set<String>): Set<String> =
        if (hidden.all { it in active }) hidden else hidden.filterTo(HashSet()) { it in active }

    fun resetForTests() {
        _keys.value = emptySet()
        prefs = null
    }
}
